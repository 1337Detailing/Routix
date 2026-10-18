package com.routix.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.Collections;
import java.util.List;

/** Tiny, non-interactive route thumbnail for the route library. */
final class RoutePreviewView extends View {
    private final RoutixTheme.Tokens theme;
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG),dot=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<RouteStore.Point> points=Collections.emptyList();

    RoutePreviewView(Context context){super(context);theme=RoutixTheme.from(context);setWillNotDraw(false);}
    void setPoints(List<RouteStore.Point> value){points=value==null?Collections.emptyList():value;invalidate();}

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
        canvas.drawColor(theme.mantle);
        if(points.size()<2)return;
        double minLat=Double.POSITIVE_INFINITY,maxLat=Double.NEGATIVE_INFINITY,minLon=Double.POSITIVE_INFINITY,maxLon=Double.NEGATIVE_INFINITY;
        for(RouteStore.Point p:points){minLat=Math.min(minLat,p.lat);maxLat=Math.max(maxLat,p.lat);minLon=Math.min(minLon,p.lon);maxLon=Math.max(maxLon,p.lon);}
        double latSpan=Math.max(1e-7,maxLat-minLat),lonSpan=Math.max(1e-7,maxLon-minLon);
        float pad=Math.max(10,getResources().getDisplayMetrics().density*10);float dw=w-pad*2,dh=h-pad*2;
        double scale=Math.min(dw/lonSpan,dh/latSpan);double usedW=lonSpan*scale,usedH=latSpan*scale;
        float ox=(float)((w-usedW)/2),oy=(float)((h-usedH)/2);
        Path path=new Path();int stride=Math.max(1,(int)Math.ceil(points.size()/900.0));
        boolean started=false;
        for(int i=0;i<points.size();i+=stride){
            RouteStore.Point p=points.get(i);float x=(float)(ox+(p.lon-minLon)*scale),y=(float)(oy+(maxLat-p.lat)*scale);
            if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);
        }
        RouteStore.Point last=points.get(points.size()-1);path.lineTo((float)(ox+(last.lon-minLon)*scale),(float)(oy+(maxLat-last.lat)*scale));
        line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(theme.crust);line.setStrokeWidth(getResources().getDisplayMetrics().density*8);canvas.drawPath(path,line);
        line.setColor(theme.accent);line.setStrokeWidth(getResources().getDisplayMetrics().density*4);canvas.drawPath(path,line);
        RouteStore.Point first=points.get(0);
        dot.setColor(theme.green);canvas.drawCircle((float)(ox+(first.lon-minLon)*scale),(float)(oy+(maxLat-first.lat)*scale),getResources().getDisplayMetrics().density*4,dot);
        dot.setColor(theme.red);canvas.drawCircle((float)(ox+(last.lon-minLon)*scale),(float)(oy+(maxLat-last.lat)*scale),getResources().getDisplayMetrics().density*4,dot);
    }
}
