package com.routix.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.os.SystemClock;
import android.view.View;

/** A header-owned speed display, never a floating window over map controls. */
final class CompactSpeedometer extends View {
    private final RoutixTheme.Tokens theme;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float speed;
    private long fixNanos;
    private boolean valid;
    private Location previousFix;
    private final Runnable expire=()->{valid=false;setContentDescription("Vitesse indisponible, GPS en attente");invalidate();};
    CompactSpeedometer(Context context){super(context);theme=RoutixTheme.from(context);setContentDescription("Vitesse indisponible, GPS en attente");}
    void update(Location location){
        removeCallbacks(expire);
        if(location==null){expire.run();return;}
        long now=SystemClock.elapsedRealtimeNanos(),age=now-location.getElapsedRealtimeNanos();
        boolean fresh=location.getElapsedRealtimeNanos()>0&&age>=0&&age<5000000000L;
        boolean precise=location.hasAccuracy()&&location.getAccuracy()<=35;
        float metresPerSecond=Float.NaN;
        if(fresh&&precise&&location.hasSpeed()&&Float.isFinite(location.getSpeed())&&location.getSpeed()>=0)metresPerSecond=location.getSpeed();
        else if(fresh&&precise&&previousFix!=null&&previousFix.hasAccuracy()&&previousFix.getAccuracy()<=35){
            long dt=location.getElapsedRealtimeNanos()-previousFix.getElapsedRealtimeNanos();
            if(dt>=500000000L&&dt<=10000000000L){
                float d=previousFix.distanceTo(location);
                float candidate=d/(dt/1000000000f);
                if(Float.isFinite(candidate)&&candidate>=0&&candidate<=70)metresPerSecond=candidate;
            }
        }
        valid=Float.isFinite(metresPerSecond);
        speed=valid?Math.max(0,metresPerSecond*3.6f):0;
        fixNanos=location.getElapsedRealtimeNanos();
        if(fresh&&precise)previousFix=new Location(location);
        setContentDescription(valid?Math.round(speed)+" kilomètres par heure":"Vitesse indisponible, GPS en attente");
        invalidate();if(valid)postDelayed(expire,Math.max(1,5000-age/1000000));
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(valid){long remaining=5000-(SystemClock.elapsedRealtimeNanos()-fixNanos)/1000000;if(remaining<=0)expire.run();else postDelayed(expire,remaining);}}
    @Override protected void onDetachedFromWindow(){removeCallbacks(expire);super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.LEFT);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(30*density);paint.setColor(theme.text);
        String value=valid?String.valueOf(Math.round(speed)):"—";canvas.drawText(value,4*density,34*density,paint);
        float x=Math.max(56*density,paint.measureText(value)+10*density);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(12*density);paint.setColor(theme.muted());canvas.drawText("km/h",x,25*density,paint);
        paint.setTextSize(10*density);paint.setColor(valid?theme.readable(theme.green):theme.muted());canvas.drawText(valid?"GPS actif":"GPS…",x,40*density,paint);
    }
}
