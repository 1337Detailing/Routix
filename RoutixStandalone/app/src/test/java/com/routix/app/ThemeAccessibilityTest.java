package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.core.graphics.ColorUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ThemeAccessibilityTest {
    @Test public void allFlavorsKeepReadableTextAndEveryAccentHasReadableButtonInk(){
        for(String flavor:new String[]{"sand"})for(String accent:RoutixTheme.accents()){
            RoutixTheme.Tokens t=RoutixTheme.flavor(flavor,accent);
            assertTrue(flavor+" text",ColorUtils.calculateContrast(t.text,t.base)>=4.5);
            assertTrue(flavor+" supporting text",ColorUtils.calculateContrast(t.muted(),t.base)>=4.5);
            assertTrue(flavor+" accent label "+accent,ColorUtils.calculateContrast(t.readable(t.accent),t.base)>=4.5);
            assertTrue(flavor+" "+accent,ColorUtils.calculateContrast(t.onAccent(),t.accent)>=4.5);
        }
    }
    @Test public void mapFollowsThemeUnlessExplicitlyOverridden(){
        SharedPreferences p=RuntimeEnvironment.getApplication().getSharedPreferences("theme_test",Context.MODE_PRIVATE);p.edit().clear().putString("map_appearance","light").commit();
        assertFalse(MapStyles.dark(p));p.edit().putString("map_appearance","dark").commit();assertTrue(MapStyles.dark(p));
        p.edit().putString("map_appearance","theme").commit();assertEquals("sand",MapStyles.tokens(p).flavor);
    }
}
