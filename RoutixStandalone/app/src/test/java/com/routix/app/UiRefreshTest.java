package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="mdpi")
public final class UiRefreshTest {
    private static Object read(Object target,String name)throws Exception{
        Field f=RoutixActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
    private static void go(RoutixActivity a,String tab)throws Exception{
        Method method=RoutixActivity.class.getDeclaredMethod("showTab",String.class,boolean.class);
        method.setAccessible(true);
        method.invoke(a,tab,false);
    }
    @Test public void lightAndDarkTokensAreReadableAndIndependentOfSavedRoutes(){
        SharedPreferences p=RuntimeEnvironment.getApplication().getSharedPreferences("ui_refresh_test",Context.MODE_PRIVATE);
        for(String mode:new String[]{"light","dark"}){
            p.edit().putString("app_theme",mode).commit();
            RoutixTheme.Tokens t=RoutixTheme.from(p);
            assertEquals("light".equals(mode),t.light());
            assertTrue(ColorUtils.calculateContrast(t.text,t.base)>=4.5);
            assertTrue(ColorUtils.calculateContrast(t.secondaryContent,t.base)>=4.5);
            assertTrue(ColorUtils.calculateContrast(t.onAccent(),t.accent)>=4.5);
            assertEquals(RoutixTheme.TAP_MIN,48);
        }
        p.edit().clear().commit();
    }
    @Test public void allWorkspaceScreensRemainReachableAndRecordingRetainsMapChrome()throws Exception{
        SharedPreferences p=RuntimeEnvironment.getApplication().getSharedPreferences("routix",0);
        p.edit().putString("app_theme","light").putBoolean("animations",false).commit();
        try(ActivityController<RoutixActivity> controller=Robolectric.buildActivity(RoutixActivity.class).create()){
            RoutixActivity a=controller.get();
            FrameLayout root=(FrameLayout)read(a,"root");
            ViewGroup dock=(ViewGroup)read(a,"dock");
            assertEquals(4,dock.getChildCount());
            for(int i=0;i<dock.getChildCount();i++)assertTrue(dock.getChildAt(i).getMinimumHeight()>=48);
            for(String tab:new String[]{"map","record","routes","more","settings","stats","gpxlab"}){
                go(a,tab);
                assertEquals(tab,read(a,"selectedTab"));
                LinearLayout content=(LinearLayout)read(a,"contentHost");
                assertTrue(tab,content.getChildCount()>0);
            }
            go(a,"record");
            assertEquals(View.VISIBLE,((View)read(a,"mapAttribution")).getVisibility());
            assertNotNull(read(a,"recordMain"));
            assertNotNull(read(a,"reverseButton"));
            assertNotNull(read(a,"twoSidesButton"));
            assertNotNull(read(a,"pauseButton"));
            TextView pause=(TextView)read(a,"pauseButton");
            assertFalse("pause is unavailable before recording",pause.isEnabled());
            root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));
            root.layout(0,0,320,640);
            assertTrue(((View)read(a,"contentHost")).getBottom()<=((View)read(a,"dock")).getTop());
        }finally{
            p.edit().remove("app_theme").remove("animations").commit();
        }
    }
}
