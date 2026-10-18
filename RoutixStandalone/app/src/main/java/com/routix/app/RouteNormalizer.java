package com.routix.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Order-preserving GPS cleanup shared by imported and freshly recorded tours.
 * It removes invalid/very inaccurate fixes and redundant points without
 * merging repeated streets or destroying return passes. Speed plausibility is
 * deliberately handled later with route context: timestamps in imported GPX
 * files can be coarse or synthetic, so a single large segment is not enough
 * evidence to delete a valid point.
 */
final class RouteNormalizer {
    static final class Result {
        final List<RouteStore.Point> points;
        final int inputCount, invalidRemoved, duplicateRemoved, simplifiedRemoved;
        Result(List<RouteStore.Point> points, int inputCount, int invalidRemoved, int duplicateRemoved, int simplifiedRemoved) {
            this.points=points; this.inputCount=inputCount; this.invalidRemoved=invalidRemoved;
            this.duplicateRemoved=duplicateRemoved; this.simplifiedRemoved=simplifiedRemoved;
        }
    }

    private static final double MIN_POINT_SPACING_M = 1.25;
    private static final double SIMPLIFY_TOLERANCE_M = 1.8;
    private static final double SHARP_TURN_DEG = 28.0;
    private static final int MIN_SIMPLIFY_POINTS = 8;

    /** Exact passthrough used when the user disables GPX optimization. No point is removed, reordered or simplified. */
    static Result preserve(List<RouteStore.Point> source) {
        List<RouteStore.Point> out=source==null?new ArrayList<>():new ArrayList<>(source);
        return new Result(out,out.size(),0,0,0);
    }

    static Result normalize(List<RouteStore.Point> source) {
        if(source==null || source.isEmpty()) return new Result(Collections.emptyList(),0,0,0,0);
        int invalid=0, dup=0;
        List<RouteStore.Point> valid=new ArrayList<>();
        for(RouteStore.Point p:source) {
            if(!valid(p)){invalid++;continue;}
            if(p.accuracy>80 && p.accuracy!=0){invalid++;continue;}
            if(!valid.isEmpty()) {
                RouteStore.Point prev=valid.get(valid.size()-1);
                if(!p.breakBefore && distanceM(prev,p)<MIN_POINT_SPACING_M){dup++;continue;}
            }
            valid.add(p);
        }
        // Sparse traces carry semantic geometry: never collapse their middle points.
        if(valid.size()<MIN_SIMPLIFY_POINTS) return new Result(valid,source.size(),invalid,dup,0);

        boolean[] keep=new boolean[valid.size()];
        keep[0]=true; keep[valid.size()-1]=true;
        for(int i=1;i<valid.size();i++)if(valid.get(i).breakBefore){keep[i]=true;keep[i-1]=true;}
        for(int i=1;i<valid.size()-1;i++){
            RouteStore.Point a=valid.get(i-1),b=valid.get(i),c=valid.get(i+1);
            if(turnAngle(a,b,c)>=SHARP_TURN_DEG) keep[i]=true;
        }
        int anchor=0;
        for(int i=1;i<keep.length;i++){
            if(!keep[i]) continue;
            if(!valid.get(i).breakBefore)simplify(valid,anchor,i,keep);
            anchor=i;
        }
        List<RouteStore.Point> out=new ArrayList<>();
        for(int i=0;i<valid.size();i++) if(keep[i]) out.add(valid.get(i));
        return new Result(out,source.size(),invalid,dup,valid.size()-out.size());
    }

    private static boolean valid(RouteStore.Point p){
        return p!=null && Double.isFinite(p.lat) && Double.isFinite(p.lon)
                && p.lat>=-90 && p.lat<=90 && p.lon>=-180 && p.lon<=180;
    }

    private static void simplify(List<RouteStore.Point> p,int first,int last,boolean[] keep){
        java.util.ArrayDeque<int[]> pending=new java.util.ArrayDeque<>();pending.push(new int[]{first,last});
        while(!pending.isEmpty()){int[] span=pending.pop();int lo=span[0],hi=span[1];if(hi<=lo+1)continue;
            double best=-1;int index=-1;for(int i=lo+1;i<hi;i++){if(keep[i])continue;double d=segmentDistanceM(p.get(i),p.get(lo),p.get(hi));if(d>best){best=d;index=i;}}
            if(index>=0&&best>SIMPLIFY_TOLERANCE_M){keep[index]=true;pending.push(new int[]{lo,index});pending.push(new int[]{index,hi});}
        }
    }

    private static double turnAngle(RouteStore.Point a,RouteStore.Point b,RouteStore.Point c){
        double lat0=Math.toRadians(b.lat),kx=111320.0*Math.cos(lat0),ky=110540.0;
        double v1x=(b.lon-a.lon)*kx,v1y=(b.lat-a.lat)*ky;
        double v2x=(c.lon-b.lon)*kx,v2y=(c.lat-b.lat)*ky;
        double l1=Math.hypot(v1x,v1y),l2=Math.hypot(v2x,v2y);
        if(l1<2||l2<2)return 0;
        double dot=(v1x*v2x+v1y*v2y)/(l1*l2);
        dot=Math.max(-1,Math.min(1,dot));
        return Math.toDegrees(Math.acos(dot));
    }

    static double distanceM(RouteStore.Point a,RouteStore.Point b){
        double r=6371000.0;
        double p1=Math.toRadians(a.lat),p2=Math.toRadians(b.lat);
        double dp=Math.toRadians(b.lat-a.lat),dl=Math.toRadians(b.lon-a.lon);
        double h=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return 2*r*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));
    }

    private static double segmentDistanceM(RouteStore.Point p,RouteStore.Point a,RouteStore.Point b){
        double lat0=Math.toRadians((a.lat+b.lat+p.lat)/3.0);
        double kx=111320.0*Math.cos(lat0), ky=110540.0;
        double bx=(b.lon-a.lon)*kx, by=(b.lat-a.lat)*ky;
        double px=(p.lon-a.lon)*kx, py=(p.lat-a.lat)*ky;
        double den=bx*bx+by*by;
        if(den<1e-6)return Math.hypot(px,py);
        double t=(px*bx+py*by)/den;
        t=Math.max(0,Math.min(1,t));
        return Math.hypot(px-t*bx,py-t*by);
    }
}
