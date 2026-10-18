package com.routix.app;

/** Clock arithmetic uses UTC instants, independent of daylight saving or local timezone. */
final class RecordingSummary {
    static final class Snapshot {
        final long elapsedMs;final double averageKmh;
        Snapshot(long elapsedMs,double averageKmh){this.elapsedMs=elapsedMs;this.averageKmh=averageKmh;}
    }
    private RecordingSummary(){}
    static Snapshot from(long startedAt,double distanceM,long now){
        long duration=startedAt>0&&now>startedAt?now-startedAt:0;
        double d=Double.isFinite(distanceM)?Math.max(0,distanceM):0;
        return new Snapshot(duration,duration>0?d*3_600d/duration:0);
    }
}
