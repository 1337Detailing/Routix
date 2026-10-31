package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class RouteDeletionSafetyTest {
    @Test public void failedDeletionCannotTombstoneRouteOrEraseItsMetadata() {
        Context app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = app.getSharedPreferences("route-delete-" + UUID.randomUUID(), 0);
        RouteStore store = new RouteStore(app, prefs);
        File nonexistent = new File(store.routesDir(), "missing_" + UUID.randomUUID() + ".gpx");
        prefs.edit().putBoolean("favorite_" + nonexistent.getName(), true)
                .putString("route_name_" + nonexistent.getName(), "Ma tournée").commit();
        assertFalse(store.delete(nonexistent));
        assertFalse(prefs.getBoolean("deleted_route_" + nonexistent.getName(), false));
        assertTrue(prefs.getBoolean("favorite_" + nonexistent.getName(), false));
        assertEquals("Ma tournée", prefs.getString("route_name_" + nonexistent.getName(), ""));
    }
}
