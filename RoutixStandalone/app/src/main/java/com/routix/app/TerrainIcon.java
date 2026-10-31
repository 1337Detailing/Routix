package com.routix.app;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small shared stroke icon family, independent of the device emoji font. */
final class TerrainIcon extends Drawable {
    private final String kind;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    TerrainIcon(String kind,int color){this.kind=kind;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);}
    @Override public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
        switch(kind){
            case "map":Path path=new Path();path.moveTo(3,5);path.lineTo(9,3);path.lineTo(15,5);path.lineTo(21,3);path.lineTo(21,19);path.lineTo(15,21);path.lineTo(9,19);path.lineTo(3,21);path.close();canvas.drawPath(path,paint);canvas.drawLine(9,3,9,19,paint);canvas.drawLine(15,5,15,21,paint);break;
            case "record":canvas.drawCircle(12,12,9,paint);canvas.drawCircle(12,12,4,paint);break;
            case "routes":for(int y=6;y<=18;y+=6){canvas.drawCircle(4,y,1,paint);canvas.drawLine(9,y,21,y,paint);}break;
            case "chevron":canvas.drawLine(9,5,16,12,paint);canvas.drawLine(16,12,9,19,paint);break;
            case "north":Path n=new Path();n.moveTo(5,20);n.lineTo(12,3);n.lineTo(19,20);n.lineTo(12,16);n.close();canvas.drawPath(n,paint);break;
            case "locate":canvas.drawCircle(12,12,6,paint);canvas.drawCircle(12,12,2,paint);canvas.drawLine(12,2,12,5,paint);canvas.drawLine(12,19,12,22,paint);canvas.drawLine(2,12,5,12,paint);canvas.drawLine(19,12,22,12,paint);break;
            case "search":canvas.drawCircle(10.5f,10.5f,6.3f,paint);canvas.drawLine(15.4f,15.4f,21,21,paint);break;
            case "star":Path star=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?9:4;float x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();canvas.drawPath(star,paint);break;
            default:for(int x=5;x<=19;x+=7)canvas.drawCircle(x,12,1.5f,paint);
        }canvas.restore();}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
