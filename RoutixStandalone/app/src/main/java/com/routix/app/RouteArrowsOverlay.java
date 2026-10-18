package com.routix.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Screen-spaced chevrons rotate with the map and always follow traversal order. */
final class RouteArrowsOverlay extends Overlay {
    private RoutixTheme.Tokens theme;
    private List<GeoPoint> points=Collections.emptyList();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    void setPoints(List<GeoPoint> points){this.points=points;}
    @Override public void draw(Canvas canvas,MapView map,boolean shadow){
        if(shadow||points.size()<2)return;
        if(theme==null)theme=RoutixTheme.from(map.getContext());
        float density=map.getResources().getDisplayMetrics().density;
        double spacing=42*density,next=20*density;
        Point a=new Point(),b=new Point();map.getProjection().toPixels(points.get(0),a);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        Path arrow=new Path();android.graphics.Rect clip=canvas.getClipBounds();
        Map<String,List<double[]>> directions=new HashMap<>();
        for(int i=1;i<points.size();i++){
            map.getProjection().toPixels(points.get(i),b);
            double dx=b.x-a.x,dy=b.y-a.y,length=Math.hypot(dx,dy);
            if(length>.01){
                double ux=dx/length,uy=dy/length;
                // Jump over invisible chevrons on very long segments without a per-arrow loop.
                double left=Math.min(a.x,b.x),right=Math.max(a.x,b.x),top=Math.min(a.y,b.y),bottom=Math.max(a.y,b.y);
                if(right<clip.left||left>clip.right||bottom<clip.top||top>clip.bottom){if(next<=length)next+=Math.floor((length-next)/spacing+1)*spacing;}
                else while(next<=length){
                    float x=(float)(a.x+next*ux),y=(float)(a.y+next*uy);
                    if(clip.contains((int)x,(int)y)){
                        int gx=(int)Math.floor(x/spacing),gy=(int)Math.floor(y/spacing);boolean laterVisit=false;
                        for(int sx=gx-1;sx<=gx+1;sx++)for(int sy=gy-1;sy<=gy+1;sy++)
                            for(double[] old:directions.getOrDefault(sx+":"+sy,Collections.emptyList()))
                                if(Math.hypot(x-old[0],y-old[1])<spacing*1.1&&ux*old[2]+uy*old[3]<.5)laterVisit=true;
                        if(laterVisit){next+=spacing;continue;}
                        directions.computeIfAbsent(gx+":"+gy,k->new ArrayList<>()).add(new double[]{x,y,ux,uy});
                        float back=12*density,wing=8*density;arrow.reset();
                        arrow.moveTo(x-(float)ux*back-(float)uy*wing,y-(float)uy*back+(float)ux*wing);
                        arrow.lineTo(x,y);arrow.lineTo(x-(float)ux*back+(float)uy*wing,y-(float)uy*back-(float)ux*wing);
                        paint.setColor(theme.crust);paint.setStrokeWidth(8*density);canvas.drawPath(arrow,paint);
                        paint.setColor(theme.text);paint.setStrokeWidth(4*density);canvas.drawPath(arrow,paint);
                    }
                    next+=spacing;
                }
                next-=length;
            }
            a.set(b.x,b.y);
        }
    }
}
