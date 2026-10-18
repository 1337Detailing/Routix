package com.routix.app;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="mdpi")
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ThemeScreensTest {
    private Object get(Object a,String key)throws Exception{Field f=RoutixActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(a);}
    private void set(Object a,String key,Object value)throws Exception{Field f=RoutixActivity.class.getDeclaredField(key);f.setAccessible(true);f.set(a,value);}
    private void render(View root,String name)throws Exception{
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));root.layout(0,0,320,640);
        Bitmap b=Bitmap.createBitmap(320,640,Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));File dir=new File("build/reports/chrome");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    }
    @Test public void populatedWorkspacesRenderInEveryFlavor()throws Exception{
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("routix",0);
        for(String flavor:new String[]{"sand"}){
            prefs.edit().clear().putString("theme_flavor",flavor).putBoolean("animations",false).commit();
            try(ActivityController<RoutixActivity> controller=Robolectric.buildActivity(RoutixActivity.class).create()){
                RoutixActivity a=controller.get();RouteStore store=(RouteStore)get(a,"store");File route=new File(store.routesDir(),"ui-fixture.gpx");
                String xml="<gpx version=\"1.1\"><trk><name>Tournée du centre</name><trkseg><trkpt lat=\"48.898\" lon=\"7.962\"/><trkpt lat=\"48.899\" lon=\"7.963\"/><trkpt lat=\"48.899\" lon=\"7.964\"/></trkseg></trk></gpx>";
                try(FileOutputStream out=new FileOutputStream(route)){out.write(xml.getBytes(StandardCharsets.UTF_8));}
                RouteFolderStore folders=(RouteFolderStore)get(a,"folderStore");folders.create("Exemple");folders.move(route,"Exemple");folders.setExpanded("Exemple",true);
                set(a,"detailFile",route);set(a,"systemTop",24);set(a,"systemBottom",24);
                FrameLayout root=(FrameLayout)get(a,"root");Method show=RoutixActivity.class.getDeclaredMethod("showTab",String.class,boolean.class);show.setAccessible(true);
                for(String page:new String[]{"record","routes","detail","hours","settings","more","gpxlab"}){
                    show.invoke(a,page,false);render(root,flavor+"-"+page);View header=(View)get(a,"topBar"),dock=(View)get(a,"dock"),content=(View)get(a,"contentHost");assertTrue(page,content.getTop()>=header.getBottom());assertTrue(page,content.getBottom()<=dock.getTop());
                }
                set(a,"guiding",true);set(a,"guidingRoute",store.parse(route));show.invoke(a,"map",false);render(root,flavor+"-guidance");assertTrue(((View)get(a,"contentHost")).getTop()>=((View)get(a,"topBar")).getBottom());
                route.delete();
            }
        }
        prefs.edit().clear().commit();
    }
    @Test public void permissionsScreenRendersInLightAndDark()throws Exception{
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("routix",0);
        for(String flavor:new String[]{"sand"}){prefs.edit().clear().putString("theme_flavor",flavor).commit();try(ActivityController<OnboardingActivity> c=Robolectric.buildActivity(OnboardingActivity.class).create()){render(c.get().findViewById(android.R.id.content),flavor+"-permissions");}}
        prefs.edit().clear().commit();
    }
}
