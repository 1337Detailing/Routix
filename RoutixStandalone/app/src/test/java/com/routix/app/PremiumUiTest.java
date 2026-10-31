package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import androidx.core.graphics.ColorUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
public class PremiumUiTest {
    @Test public void graphitePaletteHasReadableAccentAndStrongTextContrast(){
        SharedPreferences p=RuntimeEnvironment.getApplication().getSharedPreferences("premium-ui-test",Context.MODE_PRIVATE);
        RoutixTheme.Tokens t=RoutixTheme.from(p);
        assertEquals("#0B0B11",String.format("#%06X",t.base&0xffffff));
        assertTrue(ColorUtils.calculateContrast(t.text,t.base)>=7.0);
        assertTrue(ColorUtils.calculateContrast(t.subtext0,t.base)>=4.5);
        assertTrue(ColorUtils.calculateContrast(t.accent,t.base)>=4.5);
        assertTrue(ColorUtils.calculateContrast(t.onAccent(),t.accent)>=4.5);
        assertNotNull(RoutixTheme.selected(t,1f));
        assertTrue(RoutixTheme.touch(t,t.mantle,18,1f) instanceof RippleDrawable);
    }

    @Test public void fourTabsAreStillAccessibleAndSelectedTabHasVisibleHighlight()throws Exception{
        SharedPreferences p=RuntimeEnvironment.getApplication().getSharedPreferences("routix",Context.MODE_PRIVATE);
        p.edit().putBoolean("animations",false).commit();
        try(ActivityController<RoutixActivity> controller=Robolectric.buildActivity(RoutixActivity.class).create()){
            RoutixActivity a=controller.get();
            Field rootField=RoutixActivity.class.getDeclaredField("root");rootField.setAccessible(true);
            FrameLayout root=(FrameLayout)rootField.get(a);
            Field dockField=RoutixActivity.class.getDeclaredField("dock");dockField.setAccessible(true);
            ViewGroup dock=(ViewGroup)dockField.get(a);
            assertEquals(4,dock.getChildCount());
            assertTrue(dock.getChildAt(0).isSelected());
            assertTrue(dock.getChildAt(0).getBackground() instanceof GradientDrawable);
            assertNotNull(dock.getChildAt(0).getContentDescription());
            Method show=RoutixActivity.class.getDeclaredMethod("showTab",String.class,boolean.class);show.setAccessible(true);
            show.invoke(a,"routes",false);
            assertTrue(dock.getChildAt(2).isSelected());
            assertFalse(dock.getChildAt(0).isSelected());
            root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));
            root.layout(0,0,320,640);
            for(int i=0;i<4;i++)assertTrue(dock.getChildAt(i).getWidth()>=64);
        }
    }
}
