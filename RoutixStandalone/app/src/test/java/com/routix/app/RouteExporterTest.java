package com.routix.app;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class RouteExporterTest {
    @Test public void kmlSeparatesGapsAndCsvIncludesPointsAndMarkers()throws Exception{
        Context c=RuntimeEnvironment.getApplication();
        RouteStore.Summary s=new RouteStore.Summary(new File("example.gpx"));
        s.points.add(new RouteStore.Point(48.8,7.8,1000,5));
        s.points.add(new RouteStore.Point(48.81,7.81,2000,5));
        s.points.add(new RouteStore.Point(48.9,7.9,70000,5,true));
        s.points.add(new RouteStore.Point(48.91,7.91,71000,5));
        s.events.add(new RouteStore.Event("STOP","Pause \"repère\"",48.8,7.8,2000,5));
        String kml=read(RouteExporter.write(c,s,"Test & parcours",true));
        assertEquals(2,kml.split("<LineString>").length-1);
        assertTrue(kml.contains("Test &amp; parcours"));
        String csv=read(RouteExporter.write(c,s,"Test & parcours",false));
        assertEquals(6,csv.trim().split("\\n").length);
        assertTrue(csv.contains("true,,"));
        assertTrue(csv.contains("\"\"repère\"\""));
    }
    private String read(File f)throws Exception{return new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);}
}
