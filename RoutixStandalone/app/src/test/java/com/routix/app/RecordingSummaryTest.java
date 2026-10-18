package com.routix.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecordingSummaryTest {
    @Test public void daylightSavingDoesNotChangeElapsedMilliseconds(){
        long before=1711845000000L;
        RecordingSummary.Snapshot stats=RecordingSummary.from(before,5000,before+3600000);
        assertEquals(3600000,stats.elapsedMs);
        assertEquals(5,stats.averageKmh,.0001);
    }
    @Test public void invalidOrMissingClockNeverCreatesNonsense(){
        assertEquals(0,RecordingSummary.from(0,1500,System.currentTimeMillis()).elapsedMs);
        assertEquals(0,RecordingSummary.from(5000,Double.NaN,6000).averageKmh,0);
    }
}
