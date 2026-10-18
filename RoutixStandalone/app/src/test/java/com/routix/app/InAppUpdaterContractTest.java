package com.routix.app;
import org.junit.Test;import static org.junit.Assert.*;import java.nio.file.*;
public class InAppUpdaterContractTest{
 @Test public void betaOnlyAcceptsSuccessfulMasterPushes()throws Exception{String s=new String(Files.readAllBytes(Paths.get("src/main/java/com/routix/app/InAppUpdater.java")));assertTrue(s.contains("branch=master&status=success"));assertTrue(s.contains("\"push\".equals"));assertTrue(s.contains("\"success\".equals"));assertTrue(s.contains("Routix-debug"));assertEquals("Routix",InAppUpdater.REPO);assertTrue(s.contains("expired"));}
 @Test public void updaterDoesNotTouchTrackingOrRoutes()throws Exception{String t=new String(Files.readAllBytes(Paths.get("src/main/java/com/routix/app/TrackingService.java")));String r=new String(Files.readAllBytes(Paths.get("src/main/java/com/routix/app/RouteStore.java")));assertFalse(t.contains("InAppUpdater"));assertFalse(r.contains("InAppUpdater"));}
}