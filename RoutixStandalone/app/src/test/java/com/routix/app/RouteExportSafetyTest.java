package com.routix.app;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class RouteExportSafetyTest {
    @Test public void csvMarkersCannotBecomeSpreadsheetFormulas() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        RouteStore.Summary route = new RouteStore.Summary(null);
        route.points.add(new RouteStore.Point(48.0, 7.0, 1000, 5));
        route.events.add(new RouteStore.Event("REMARK", "=HYPERLINK(\"https://example.invalid\")",
                48.0, 7.0, 1000, 5));
        File out = RouteExporter.write(context, route, "routix-export-formula", false);
        String csv = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"https://example.invalid\"\")\""));
    }
}
