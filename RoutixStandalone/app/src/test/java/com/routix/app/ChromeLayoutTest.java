package com.routix.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.RobolectricTestRunner;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ChromeLayoutTest {
    private Object field(Object a,String n)throws Exception{Field f=RoutixActivity.class.getDeclaredField(n);f.setAccessible(true);return f.get(a);}
    private void set(Object a,String n,int value)throws Exception{Field f=RoutixActivity.class.getDeclaredField(n);f.setAccessible(true);f.setInt(a,value);}
    private void invoke(Object a,String n)throws Exception{Method m=RoutixActivity.class.getDeclaredMethod(n);m.setAccessible(true);m.invoke(a);}

    @Test public void fourWorkspacesFitSmallScreensWithoutScrolling()throws Exception{
        try(ActivityController<RoutixActivity> controller=Robolectric.buildActivity(RoutixActivity.class).create()){
            RoutixActivity activity=controller.get();
            ((org.osmdroid.views.MapView)field(activity,"map")).setUseDataConnection(false);
            FrameLayout root=(FrameLayout)field(activity,"root");
            set(activity,"systemTop",28);set(activity,"systemBottom",24);
            for(int[] size:new int[][]{{320,640},{390,844},{430,932},{640,360}}){
                root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));root.layout(0,0,size[0],size[1]);invoke(activity,"positionChrome");
                root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));root.layout(0,0,size[0],size[1]);
                View header=(View)field(activity,"topBar"),dock=(View)field(activity,"dock"),content=(View)field(activity,"contentHost");
                assertTrue(header.getBottom()<content.getBottom());assertTrue(dock.getTop()>header.getBottom());assertTrue(dock.getBottom()<=size[1]-24+2);
                ViewGroup row=(ViewGroup)dock;assertEquals(4,row.getChildCount());for(int i=0;i<4;i++){assertTrue(row.getChildAt(i).getWidth()>=64);assertTrue(row.getChildAt(i).getRight()<=dock.getWidth());}
                for(String tab:new String[]{"record","routes","more","settings","hours","gpxlab"}){
                    Method show=RoutixActivity.class.getDeclaredMethod("showTab",String.class,boolean.class);show.setAccessible(true);show.invoke(activity,tab,false);
                    root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));root.layout(0,0,size[0],size[1]);
                    assertTrue(content.getTop()>=header.getBottom());assertTrue(content.getBottom()<=dock.getTop());
                    Bitmap shot=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);root.draw(new Canvas(shot));
                    File screenshots=new File("build/reports/chrome");screenshots.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(screenshots,tab+"-"+size[0]+".png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();
                }
                Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
                File dir=new File("build/reports/chrome");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"chrome-v3-"+size[0]+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
            }
        }
    }
}
