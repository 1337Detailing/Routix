package com.routix.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.speech.tts.TextToSpeech;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** One adaptive GPS subscription; UI listeners exist only while the activity is visible. */
public final class TrackingService extends Service implements LocationListener {
    interface Listener {void changed();}
    final class LocalBinder extends Binder {TrackingService service(){return TrackingService.this;}}
    final List<RouteStore.Point> points=new ArrayList<>();
    final List<RouteStore.Event> events=new ArrayList<>();
    boolean recording,paused,ready,saving;long started;double distance;
    Location location;GuidanceEngine guidance;GuidanceEngine.State guidanceState;GuidanceCoverage guidanceCoverage;RouteStore.Summary route;

    long guidanceStartedAt;double guidanceTravelDistance;int guidanceReverseAdded,guidanceTwoSidesAdded;

    static final String ACTION_PAUSE="com.routix.app.action.PAUSE",ACTION_RESUME="com.routix.app.action.RESUME",ACTION_SAVE="com.routix.app.action.SAVE",ACTION_STOP_GUIDANCE="com.routix.app.action.STOP_GUIDANCE";
    private String deferredAction;
    private static final int GPS_IDLE=0,GPS_MOVING=1,GPS_STATIONARY=2;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private SessionJournal journal;private RouteStore store;private SharedPreferences prefs;
    private LocationManager manager;private boolean subscribed,foreground,visible,closed;
    private int gpsProfile=-1;private long stationarySince,lastUiPublishMs,lastStatsWriteMs;
    private Listener listener;private TextToSpeech speech;private boolean speechReady;private GuidanceEngine.Event spoken;
    private long lastProgressWriteMs;private float lastProgressWriteM=-1;private Location lastAcceptedRecordFix,lastAcceptedGuidanceFix;
    private final RealtimeTraceFilter recordFilter=new RealtimeTraceFilter();
    private final java.util.concurrent.atomic.AtomicBoolean draftPending=new java.util.concurrent.atomic.AtomicBoolean();
    private final RouteAwareAnalyzer routeAware=new RouteAwareAnalyzer();private RoadContextProvider roadContext;private StreetPassageStore streetPassages;

    private final Runnable signalHealth=new Runnable(){@Override public void run(){if(!closed){if(recording||guidance!=null)publish();main.postDelayed(this,10000);}}};
    private final Runnable draft=new Runnable(){public void run(){
        if(recording)saveDraft();
        if(!closed)main.postDelayed(this,prefs!=null&&prefs.getBoolean("battery_saver",true)?30000:15000);
    }};

    @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("routix",MODE_PRIVATE);journal=new SessionJournal(this);store=new RouteStore(this,prefs);manager=(LocationManager)getSystemService(LOCATION_SERVICE);roadContext=new RoadContextProvider(this);streetPassages=new StreetPassageStore(this);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("tracking","Tournée en cours",NotificationManager.IMPORTANCE_LOW));
        io.execute(()->{try{
            List<RouteStore.Point> p=new ArrayList<>();List<RouteStore.Event> e=new ArrayList<>();journal.read(p,e);
            boolean rec=Boolean.parseBoolean(journal.readState("recording","false")),pause=Boolean.parseBoolean(journal.readState("paused","false"));
            long start=parseLong(journal.readState("started","0"),0);
            if(!rec&&!p.isEmpty()){
                store.saveDraft(p,e,start>0?start:p.get(0).time);
                journal.clearRecording();p.clear();e.clear();
            }
            String file=journal.readState("guidance","");float progress=parseFloat(journal.readState("progress","0"),0);
            long gStarted=parseLong(journal.readState("guidance_started","0"),0);
            double gDistance=parseDouble(journal.readState("guidance_distance","0"),0);
            int gReverse=(int)parseLong(journal.readState("guidance_reverse","0"),0),gTwo=(int)parseLong(journal.readState("guidance_two_sides","0"),0);
            RouteStore.Summary recovered=file.isEmpty()?null:store.parse(new File(store.routesDir(),new File(file).getName()));boolean validGuidance=recovered!=null&&recovered.points.size()>1;
            if(!file.isEmpty()&&!validGuidance){journal.state("guidance","");journal.state("progress","0");DiagnosticLog.info("stale guidance checkpoint cleared");}
            main.post(()->{if(closed)return;points.addAll(p);events.addAll(e);recording=rec;paused=pause;started=start;for(int i=1;i<points.size();i++)distance+=RouteStore.segmentMeters(points.get(i-1),points.get(i));
                if(validGuidance){guidanceStartedAt=gStarted>0?gStarted:System.currentTimeMillis();guidanceTravelDistance=Math.max(0,gDistance);guidanceReverseAdded=Math.max(0,gReverse);guidanceTwoSidesAdded=Math.max(0,gTwo);installGuidance(recovered,progress);}
                ready=true;if(deferredAction!=null){String action=deferredAction;deferredAction=null;applyNotificationAction(action);}if(recording||guidance!=null){if(visible&&!foreground)activate();else if(visible||foreground)subscribe();}else idle();publish();if(visible&&!subscribed)subscribe();});
        }catch(Exception ex){DiagnosticLog.error("session recovery",ex);main.post(()->{if(closed)return;ready=true;if(foreground&&!recording&&guidance==null)idle();publish();if(visible&&!subscribed)subscribe();});}});
        main.postDelayed(draft,30000);main.postDelayed(signalHealth,10000);
    }

    @Override public IBinder onBind(Intent intent){return new LocalBinder();}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        try{promote();String action=intent==null?null:intent.getAction();if(action!=null){if(ready)applyNotificationAction(action);else deferredAction=action;}if(ready)subscribe();return START_STICKY;}catch(RuntimeException e){DiagnosticLog.error("foreground start",e);stopSelf();return START_NOT_STICKY;}
    }
    private void applyNotificationAction(String action){
        if(ACTION_PAUSE.equals(action)&&recording&&!paused)togglePause();
        else if(ACTION_RESUME.equals(action)&&recording&&paused)togglePause();
        else if(ACTION_SAVE.equals(action)&&recording&&!saving){
            if(points.size()>=2)finish(file->{if(file==null)DiagnosticLog.info("notification save unsuccessful; session retained");});
            else {if(!paused)togglePause();DiagnosticLog.info("notification save deferred: fewer than two fixes");}
        }else if(ACTION_STOP_GUIDANCE.equals(action)&&guidance!=null)stopGuidance();
    }
    private PendingIntent notificationAction(String action,int code){
        Intent i=new Intent(this,TrackingService.class).setAction(action);
        return PendingIntent.getService(this,code,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private Notification trackingNotification(){
        Intent open=new Intent(this,RoutixActivity.class);PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        String title=getString(R.string.notification_active),text=getString(R.string.notification_open);int progress=0;
        if(recording){title=getString(paused?R.string.notification_paused:R.string.notification_recording);
            text=getString(R.string.notification_points,points.size());
            if(location==null||SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos()>20_000_000_000L)text=getString(R.string.notification_gps_lost);
        }
        if(guidance!=null&&guidanceState!=null){progress=guidanceState.progressPercent;title="Routix · "+progress+" %";text=formatIslandDistance(guidanceState.remainingM)+" restant";if(guidanceState.nextEvent!=null&&guidanceState.distanceToNextEventM<1000)text+=" · "+guidanceState.nextEvent.label;}
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,"tracking").setSmallIcon(R.drawable.ic_tracking).setColor(RoutixTheme.from(this).accent).setContentTitle(title).setContentText(text).setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setCategory(NotificationCompat.CATEGORY_NAVIGATION);
        if(guidance!=null&&guidanceState!=null){String summary=formatIslandDistance(guidanceState.remainingM)+" · "+progress+" %";b.setSubText(summary);b.getExtras().putCharSequence("android.shortCriticalText",formatIslandDistance(guidanceState.remainingM));}
        if(guidance!=null)b.addExtras(NotificationCapsuleCompat.guidance(this,title,text,progress));
        if(recording&&!saving){
            boolean isPaused=paused;
            b.addAction(0,getString(isPaused?R.string.notification_resume:R.string.notification_pause),notificationAction(isPaused?ACTION_RESUME:ACTION_PAUSE,101));
            b.addAction(0,getString(R.string.notification_save),notificationAction(ACTION_SAVE,102));
        }else if(guidance!=null)b.addAction(0,getString(R.string.notification_stop),notificationAction(ACTION_STOP_GUIDANCE,103));
        return b.build();
    }
    private static String formatIslandDistance(float meters){if(!Float.isFinite(meters))return "—";return meters>=1000?String.format(Locale.FRANCE,"%.1f km",meters/1000f):Math.round(Math.max(0,meters))+" m";}
    private void promote(){if(foreground)return;NotificationCapsuleCompat.registerScene(this);Notification n=trackingNotification();
        if(Build.VERSION.SDK_INT>=29)startForeground(71,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(71,n);foreground=true;
    }
    private void updateTrackingNotification(){if(!foreground)return;try{((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(71,trackingNotification());}catch(RuntimeException e){DiagnosticLog.error("tracking notification update",e);}}

    private boolean activate(){try{ContextCompat.startForegroundService(this,new Intent(this,TrackingService.class));promote();subscribe();return subscribed;}catch(RuntimeException ex){DiagnosticLog.error("tracking activation",ex);return false;}}
    void observe(Listener l){listener=l;visible=l!=null;if(visible){if(recording||guidance!=null)activate();else subscribe();publish();}else if(!recording&&guidance==null)unsubscribe();}
    private void publish(){updateTrackingNotification();if(listener!=null)try{listener.changed();}catch(RuntimeException e){DiagnosticLog.error("UI observer",e);}}
    private void publishLocation(boolean moving){if(listener==null)return;long now=SystemClock.elapsedRealtime();long min=prefs.getBoolean("battery_saver",true)?(moving?900:3000):500;if(now-lastUiPublishMs<min)return;lastUiPublishMs=now;publish();}
    private void write(Runnable task){try{io.execute(()->{try{task.run();}catch(RuntimeException e){DiagnosticLog.error("session persistence",e);if(!closed)main.post(()->android.widget.Toast.makeText(this,"Sauvegarde impossible : vérifie le stockage. Exporte le diagnostic.",android.widget.Toast.LENGTH_LONG).show());}});}catch(RejectedExecutionException e){DiagnosticLog.error("session persistence rejected",e);}}
    private void ensureSpeech(){if(speech!=null||closed||!prefs.getBoolean("voice_markers",true))return;speechReady=false;speech=new TextToSpeech(this,status->{if(closed)return;speechReady=status==TextToSpeech.SUCCESS;if(speechReady&&speech!=null){String code=prefs.getString("app_language","");Locale voice=code.isEmpty()?Locale.getDefault():Locale.forLanguageTag(code);speech.setLanguage(voice);}});}
    private void releaseSpeech(){speechReady=false;if(speech!=null){try{speech.stop();speech.shutdown();}catch(RuntimeException e){DiagnosticLog.error("speech shutdown",e);}speech=null;}spoken=null;}

    private void persistProgress(float progress,boolean force){long now=SystemClock.elapsedRealtime();if(!force&&now-lastProgressWriteMs<4000&&lastProgressWriteM>=0&&Math.abs(progress-lastProgressWriteM)<12f)return;lastProgressWriteMs=now;lastProgressWriteM=progress;write(()->journal.state("progress",Float.toString(progress)));}
    private void persistGuidanceStats(boolean force){if(guidance==null&&!force)return;long now=SystemClock.elapsedRealtime();if(!force&&now-lastStatsWriteMs<15000)return;lastStatsWriteMs=now;long start=guidanceStartedAt;double dist=guidanceTravelDistance;int rev=guidanceReverseAdded,two=guidanceTwoSidesAdded;write(()->{journal.state("guidance_started",Long.toString(start));journal.state("guidance_distance",Double.toString(dist));journal.state("guidance_reverse",Integer.toString(rev));journal.state("guidance_two_sides",Integer.toString(two));});}

    boolean beginRecording(){if(!ready||saving||recording||guidance!=null||!activate())return false;points.clear();events.clear();distance=0;lastAcceptedRecordFix=null;recordFilter.reset();routeAware.reset();streetPassages.resetSession();started=System.currentTimeMillis();recording=true;paused=false;long start=started;write(()->{journal.clearRecording();journal.state("started",Long.toString(start));journal.state("recording","true");store.clearDraft();});requestProfile(GPS_MOVING);publish();return true;}
    void recoverDraft(RouteStore.Summary draft){if(!beginRecording())return;points.addAll(draft.points);events.addAll(draft.events);distance=draft.distanceM;started=draft.firstTime;List<RouteStore.Point> p=new ArrayList<>(points);List<RouteStore.Event> e=new ArrayList<>(events);long start=started;write(()->{journal.state("started",Long.toString(start));for(RouteStore.Point x:p)journal.point(x);for(RouteStore.Event x:e)journal.event(x);});publish();}
    void togglePause(){if(!recording||saving)return;paused=!paused;if(!paused){lastAcceptedRecordFix=null;recordFilter.reset();routeAware.reset();}boolean value=paused;write(()->journal.state("paused",Boolean.toString(value)));saveDraft();publish();}
    void addEvent(String type,String label){if(!recording||paused||location==null)return;RouteStore.Event e=new RouteStore.Event(type,label,location.getLatitude(),location.getLongitude(),System.currentTimeMillis(),location.getAccuracy());events.add(e);write(()->journal.event(e));publish();}
    void finish(java.util.function.Consumer<File> result){if(saving||points.size()<2)return;saving=true;boolean wasPaused=paused;paused=true;List<RouteStore.Point> p=new ArrayList<>(points);List<RouteStore.Event> e=new ArrayList<>(events);long start=started;publish();io.execute(()->{File f=null;try{f=store.createRoute(p,e,start);if(f!=null){journal.clearRecording();store.clearDraft();streetPassages.save(f.getName());}}catch(RuntimeException ex){DiagnosticLog.error("finish recording",ex);}File saved=f;main.post(()->{saving=false;if(saved!=null){recording=false;paused=false;points.clear();events.clear();distance=0;idle();}else paused=wasPaused;publish();result.accept(saved);});});}

    void beginGuidance(RouteStore.Summary s){if(!ready||recording||guidance!=null||s==null||s.points.size()<2||!activate())return;
        guidanceStartedAt=System.currentTimeMillis();guidanceTravelDistance=0;lastAcceptedGuidanceFix=null;guidanceReverseAdded=0;guidanceTwoSidesAdded=0;lastStatsWriteMs=0;
        installGuidance(s,0);String name=s.file.getName();long start=guidanceStartedAt;write(()->{journal.state("progress","0");journal.state("guidance",name);journal.state("guidance_started",Long.toString(start));journal.state("guidance_distance","0");journal.state("guidance_reverse","0");journal.state("guidance_two_sides","0");});requestProfile(GPS_MOVING);publish();
    }
    private void installGuidance(RouteStore.Summary s,float progress){route=s;lastProgressWriteM=progress;lastProgressWriteMs=SystemClock.elapsedRealtime();List<GuidanceEngine.Point> p=new ArrayList<>();for(RouteStore.Point x:s.points)p.add(new GuidanceEngine.Point(x.lat,x.lon));List<GuidanceEngine.Event> e=new ArrayList<>();for(RouteStore.Event x:s.events){int index=-1;if(x.time>0&&x.time>=s.firstTime&&x.time<=s.lastTime){long best=Long.MAX_VALUE;for(int i=0;i<s.points.size();i++){long t=s.points.get(i).time;if(t>0&&Math.abs(t-x.time)<best){best=Math.abs(t-x.time);index=i;}}}e.add(new GuidanceEngine.Event(x.type,x.label,x.lat,x.lon,index));}guidance=new GuidanceEngine(p,e);guidanceCoverage=new GuidanceCoverage(p);guidance.restoreProgress(progress);GuidanceEngine.Point restored=guidance.pointAt(progress);guidanceState=guidance.update(restored.lat,restored.lon,0,1000);spoken=null;}
    void addGuidanceEvent(String type,String label){if(route==null||location==null)return;RouteStore.Summary current=route;float progress=guidance.progressM();RouteStore.Event event=new RouteStore.Event(type,label,location.getLatitude(),location.getLongitude(),current.points.get(guidance.currentIndex()).time,location.getAccuracy());current.events.add(event);
        if("REVERSE".equals(type))guidanceReverseAdded++;else if("TWO_SIDES".equals(type))guidanceTwoSidesAdded++;persistGuidanceStats(true);
        installGuidance(current,progress);List<RouteStore.Event> copy=new ArrayList<>(current.events);write(()->{if(!store.writeGpx(current.file,current.points,copy,current.firstTime,store.displayName(current.file)))throw new IllegalStateException("Marker save failed");});publish();}
    void stopGuidance(){persistGuidanceStats(true);guidance=null;guidanceState=null;guidanceCoverage=null;route=null;write(()->{journal.state("guidance","");journal.state("progress","0");journal.state("guidance_started","0");journal.state("guidance_distance","0");journal.state("guidance_reverse","0");journal.state("guidance_two_sides","0");});releaseSpeech();guidanceStartedAt=0;guidanceTravelDistance=0;guidanceReverseAdded=0;guidanceTwoSidesAdded=0;idle();publish();}
    boolean reposition(){if(location==null||guidance==null||location.getAccuracy()>35)return false;boolean ok=guidance.reposition(location.getLatitude(),location.getLongitude());if(ok){float progress=guidance.progressM();persistProgress(progress,true);onLocationChanged(location);}return ok;}

    private void idle(){if(!recording&&guidance==null){releaseSpeech();stationarySince=0;if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf();}if(!visible)unsubscribe();else requestProfile(GPS_IDLE);}}
    void saveDraft(){if(!recording||saving||!draftPending.compareAndSet(false,true))return;List<RouteStore.Point> p=new ArrayList<>(points);List<RouteStore.Event> e=new ArrayList<>(events);long start=started;write(()->{try{store.saveDraft(p,e,start);}finally{draftPending.set(false);}});}

    private void subscribe(){requestProfile(recording||guidance!=null?GPS_MOVING:GPS_IDLE);}
    private void requestProfile(int profile){
        if((subscribed&&profile==gpsProfile)||(!visible&&!foreground))return;
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){unsubscribe();return;}
        long minTime;float minDistance;
        boolean saver=prefs.getBoolean("battery_saver",true);
        if(!saver){profile=GPS_MOVING;minTime=1000;minDistance=0;}
        else if(profile==GPS_STATIONARY){minTime=7000;minDistance=3f;}
        else if(profile==GPS_IDLE){minTime=4000;minDistance=2f;}
        else {minTime=1800;minDistance=1.5f;}
        try{
            if(subscribed)manager.removeUpdates(this);
            subscribed=false;gpsProfile=-1;
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,minTime,minDistance,this,Looper.getMainLooper());
            subscribed=true;gpsProfile=profile;DiagnosticLog.info("GPS profile="+profile+" interval="+minTime+"ms distance="+minDistance+"m");
        }catch(RuntimeException e){DiagnosticLog.error("GPS subscribe",e);}
    }
    private void unsubscribe(){try{if(manager!=null)manager.removeUpdates(this);}catch(RuntimeException e){DiagnosticLog.error("GPS unsubscribe",e);}subscribed=false;gpsProfile=-1;DiagnosticLog.info("GPS unsubscribed");}

    @Override public void onLocationChanged(Location fix){
        if(!ready||!valid(fix))return;if(location!=null&&fix.getElapsedRealtimeNanos()<location.getElapsedRealtimeNanos())return;
        Location previous=location==null?null:new Location(location);location=new Location(fix);
        float estimatedSpeed=fix.hasSpeed()?fix.getSpeed():Float.NaN;
        if(!Float.isFinite(estimatedSpeed)&&previous!=null){long dt=(fix.getElapsedRealtimeNanos()-previous.getElapsedRealtimeNanos())/1_000_000L;if(dt>0){float[] d=new float[1];Location.distanceBetween(previous.getLatitude(),previous.getLongitude(),fix.getLatitude(),fix.getLongitude(),d);estimatedSpeed=d[0]/(dt/1000f);}}
        boolean moving=Float.isFinite(estimatedSpeed)&&estimatedSpeed>1.1f;
        if(recording||guidance!=null){
            if(moving){stationarySince=0;if(gpsProfile==GPS_STATIONARY)requestProfile(GPS_MOVING);}
            else if(prefs.getBoolean("battery_saver",true)){long now=SystemClock.elapsedRealtime();if(stationarySince==0)stationarySince=now;if(now-stationarySince>20000&&gpsProfile!=GPS_STATIONARY)requestProfile(GPS_STATIONARY);}
        }

        try{if(recording&&!paused&&!saving&&fix.getAccuracy()<=prefs.getInt("record_accuracy",45)){
            Location ref=lastAcceptedRecordFix;long dt=ref==null?0:(fix.getElapsedRealtimeNanos()-ref.getElapsedRealtimeNanos())/1_000_000L;
            boolean gap=!points.isEmpty()&&(ref==null||dt>30000);if(gap){recordFilter.reset();routeAware.reset();}boolean plausible=gap||ref==null||GpsMovementFilter.plausible(ref.distanceTo(fix),dt,ref.getAccuracy(),fix.getAccuracy());
            if(plausible){
                Location filtered=recordFilter.update(fix);RouteAwareAnalyzer.Observation road=routeAware.update(filtered,roadContext.nearby(filtered));Location smart=road.snapped?road.location:filtered;
                RouteStore.Point p=new RouteStore.Point(smart.getLatitude(),smart.getLongitude(),fix.getTime(),smart.getAccuracy(),gap);
                double d=points.isEmpty()||gap?0:RouteNormalizer.distanceM(points.get(points.size()-1),p);streetPassages.observe(road,fix.getTime(),d);
                // Adaptive spacing: dense at low speed/corners, slightly wider at road speed.
                double spacing=prefs.getInt("record_spacing",2);if(fix.hasSpeed())spacing=Math.max(spacing,Math.min(5.0,1.5+fix.getSpeed()*.10));
                if(points.isEmpty()||gap||d>=spacing){distance+=d;points.add(p);write(()->journal.point(p));}
                // Plausibility must reference the accepted RAW fix, not the filtered coordinate.
                lastAcceptedRecordFix=new Location(fix);
            }
        }}catch(RuntimeException e){DiagnosticLog.error("record fix",e);}

        try{if(guidance!=null){
            if(fix.getAccuracy()<=45){if(lastAcceptedGuidanceFix==null||lastAcceptedGuidanceFix.getAccuracy()>45)lastAcceptedGuidanceFix=new Location(fix);else{float travelled=lastAcceptedGuidanceFix.distanceTo(fix);long dt=(fix.getElapsedRealtimeNanos()-lastAcceptedGuidanceFix.getElapsedRealtimeNanos())/1_000_000L;if(GpsMovementFilter.plausible(travelled,dt,lastAcceptedGuidanceFix.getAccuracy(),fix.getAccuracy())){if(travelled>=2.5f)guidanceTravelDistance+=travelled;lastAcceptedGuidanceFix=new Location(fix);}}}
            guidance.setOffRouteThreshold(prefs.getInt("offroute_m",45));guidanceState=guidance.update(fix.getLatitude(),fix.getLongitude(),fix.getElapsedRealtimeNanos()/1000000,fix.getAccuracy());if(guidanceCoverage!=null&&!guidanceState.poorAccuracy&&!guidanceState.offRoute)guidanceCoverage.observe(guidanceState.nearestIndex,guidanceState.distanceToTraceM);float progress=guidance.progressM();persistProgress(progress,false);persistGuidanceStats(false);GuidanceEngine.Event next=guidanceState.nextEvent;
            if(next!=null&&next!=spoken&&!guidanceState.poorAccuracy&&!guidanceState.offRoute&&guidanceState.distanceToNextEventM<55&&prefs.getBoolean("voice_markers",true)){ensureSpeech();if(speechReady&&speech!=null){speech.speak(next.label+" "+getString(R.string.tts_in)+" "+Math.round(guidanceState.distanceToNextEventM)+" "+getString(R.string.tts_metres),TextToSpeech.QUEUE_FLUSH,null,"marker");spoken=next;}}
        }}catch(RuntimeException e){DiagnosticLog.error("guidance fix",e);}
        publishLocation(moving);
    }

    static boolean valid(Location l){return l!=null&&Double.isFinite(l.getLatitude())&&Double.isFinite(l.getLongitude())&&Math.abs(l.getLatitude())<=90&&Math.abs(l.getLongitude())<=180&&l.hasAccuracy()&&Float.isFinite(l.getAccuracy())&&l.getAccuracy()>=0;}
    private static long parseLong(String s,long fallback){try{return Long.parseLong(s);}catch(Exception e){return fallback;}}
    private static float parseFloat(String s,float fallback){try{return Float.parseFloat(s);}catch(Exception e){return fallback;}}
    private static double parseDouble(String s,double fallback){try{return Double.parseDouble(s);}catch(Exception e){return fallback;}}

    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    @Override public void onProviderDisabled(String provider){DiagnosticLog.info("GPS disabled");publish();}
    @Override public void onProviderEnabled(String provider){DiagnosticLog.info("GPS enabled");publish();}
    @Override public void onTaskRemoved(Intent rootIntent){if(recording)saveDraft();if(guidance!=null){persistProgress(guidance.progressM(),true);persistGuidanceStats(true);}DiagnosticLog.info("task removed while tracking="+(recording||guidance!=null));super.onTaskRemoved(rootIntent);}
    @Override public void onDestroy(){if(recording)saveDraft();if(guidance!=null){persistProgress(guidance.progressM(),true);persistGuidanceStats(true);}closed=true;listener=null;main.removeCallbacksAndMessages(null);unsubscribe();releaseSpeech();if(roadContext!=null)roadContext.close();try{io.execute(journal::close);}catch(RejectedExecutionException e){DiagnosticLog.error("journal close rejected",e);}io.shutdown();super.onDestroy();}
}
