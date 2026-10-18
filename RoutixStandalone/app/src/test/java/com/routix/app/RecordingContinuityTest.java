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
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class RecordingContinuityTest {
    private RouteStore.Point point(double lat,long time,boolean gap) {
        return new RouteStore.Point(lat,7.95,time,6,gap);
    }

    @Test public void interruptedTripRoundTripsAcrossSqliteReopen() {
        Context context=RuntimeEnvironment.getApplication();
        SessionJournal first=new SessionJournal(context);
        first.clearRecording();
        first.point(point(48.81,1000,false));
        first.point(point(48.82,61000,true));
        first.close();
        SessionJournal second=new SessionJournal(context);
        List<RouteStore.Point> points=new ArrayList<>();
        second.read(points,new ArrayList<>());
        assertEquals(2,points.size());
        assertFalse(points.get(0).breakBefore);
        assertTrue(points.get(1).breakBefore);
        second.clearRecording();
        second.close();
    }

    @Test public void gpxSegmentsAreNotJoinedAndDistanceSkipsGpsOutage() throws Exception {
        Context c=RuntimeEnvironment.getApplication();
        RouteStore store=new RouteStore(c,c.getSharedPreferences("segment_test",0));
        File file=new File(c.getCacheDir(),"segments-test.gpx");
        List<RouteStore.Point> p=Arrays.asList(
            point(48.81,1000,false),
            point(48.8101,2000,false),
            point(48.83,70000,true),
            point(48.8301,71000,false));
        assertTrue(store.writeGpx(file,p,Collections.emptyList(),1000,"Segments"));
        String xml=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
        assertEquals(2,xml.split("<trkseg>").length-1);
        RouteStore.Summary restored=store.parse(file);
        assertEquals(4,restored.points.size());
        assertTrue(restored.points.get(2).breakBefore);
        assertTrue("no teleport distance",restored.distanceM<40);
        assertTrue("motion within segments counted",restored.distanceM>15);
        file.delete();
    }

    @Test public void simplificationKeepsBothSidesOfDiscontinuity() {
        List<RouteStore.Point> p=Arrays.asList(
            point(48.81,1000,false),point(48.8101,2000,false),
            point(48.810101,65000,true),point(48.8102,66000,false));
        RouteNormalizer.Result normalized=RouteNormalizer.normalize(p);
        assertEquals(4,normalized.points.size());
        assertTrue(normalized.points.get(2).breakBefore);
    }

    @Test public void largeGapIsNeverCountedAsPlausibleContinuousMovement() {
        assertFalse(GpsMovementFilter.plausible(180,60000,5,5));
        assertTrue(GpsMovementFilter.plausible(8,1000,5,5));
    }
}
