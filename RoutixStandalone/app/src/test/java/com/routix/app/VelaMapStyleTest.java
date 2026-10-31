package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class VelaMapStyleTest {
    private SharedPreferences preferences() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("vela_map_test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        return prefs;
    }

    @Test public void originalStyleRemainsDefault() {
        SharedPreferences prefs = preferences();
        assertFalse(MapStyles.vela(prefs));
        prefs.edit().putString("map_appearance", "dark").commit();
        assertEquals("asset://maps/dark.json", MapStyles.uri(prefs));
    }

    @Test public void velaUsesLiveOpenFreeMapStyleNotPinnedDatedTiles() {
        SharedPreferences prefs = preferences();
        prefs.edit().putString("basemap_style", "vela").putString("map_appearance", "light").commit();
        assertTrue(MapStyles.vela(prefs));
        assertEquals("https://tiles.openfreemap.org/styles/liberty", MapStyles.uri(prefs));
    }

    @Test public void nightChangeReloadsSameLibertyUri() {
        SharedPreferences prefs = preferences();
        prefs.edit().putString("basemap_style", "vela").putString("map_appearance", "light").commit();
        String lightUri = MapStyles.uri(prefs), lightKey = MapStyles.styleKey(prefs);
        prefs.edit().putString("map_appearance", "dark").commit();
        assertEquals(lightUri, MapStyles.uri(prefs));
        assertNotEquals(lightKey, MapStyles.styleKey(prefs));
        assertTrue(MapStyles.styleKey(prefs).endsWith("#night"));
    }

    @Test public void switchBackRestoresRoutixStyle() {
        SharedPreferences prefs = preferences();
        prefs.edit().putString("map_appearance", "light")
                .putString("basemap_style", "vela").commit();
        prefs.edit().putString("basemap_style", "routix").commit();
        assertEquals("asset://maps/light.json", MapStyles.uri(prefs));
    }
}
