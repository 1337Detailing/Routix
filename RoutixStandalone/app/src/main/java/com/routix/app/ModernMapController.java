package com.routix.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.*;
import android.location.Location;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.*;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.*;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.layers.*;
import org.maplibre.geojson.*;
import org.maplibre.geojson.Point;
import java.util.*;
import static org.maplibre.android.style.layers.PropertyFactory.*;

/** Map rendering is throttled separately from GPS acquisition to avoid continuous GPU work. */
final class ModernMapController {
    private static final int MAX_ROUTE_RENDER_POINTS=6000,MAX_ARROW_RENDER_POINTS=1800;
    private final MapView view;private final SharedPreferences prefs;private MapLibreMap map;private Style style;
    private List<org.osmdroid.util.GeoPoint> route=Collections.emptyList(),aheadRoute=Collections.emptyList(),approach=Collections.emptyList(),recordingRoute=Collections.emptyList();private List<List<org.osmdroid.util.GeoPoint>> recordingSegments=Collections.emptyList();private Location location,headingFix;
    private long routeSignature=Long.MIN_VALUE,aheadSignature=Long.MIN_VALUE,approachSignature=Long.MIN_VALUE,recordingSignature=Long.MIN_VALUE,lastCameraMs,lastPositionFrameMs;
    private Location visualLocation;
    private boolean follow=true,heading=true,destroyed,visible=true,bearingReady,guidingMode;
    private double bearing;private String styleUri;private float actionDistance=Float.MAX_VALUE;
    private List<RouteStore.Event> events=Collections.emptyList();private int eventsSignature=Integer.MIN_VALUE,bridgesSignature=Integer.MIN_VALUE;private List<RoadRestrictionProvider.Bridge> bridges=Collections.emptyList();
    private final Bitmap userIcon;
    ModernMapController(Activity activity,FrameLayout root,Bitmap icon,Bundle state){prefs=activity.getSharedPreferences("routix",0);userIcon=icon;MapLibre.getInstance(activity);view=new MapView(activity,MapLibreMapOptions.createFromAttributes(activity).textureMode(true));view.onCreate(state);root.addView(view,1,new FrameLayout.LayoutParams(-1,-1));view.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)follow=false;return false;});view.addOnDidFailLoadingMapListener(error->DiagnosticLog.info("map style load failed: "+error));view.getMapAsync(m->{if(destroyed)return;map=m;map.getUiSettings().setAttributionEnabled(false);map.getUiSettings().setLogoEnabled(false);refreshStyle();});}
    void refreshStyle(){if(map==null||destroyed)return;String requested=MapStyles.uri(prefs);styleUri=requested;style=null;map.setStyle(new Style.Builder().fromUri(requested),s->{if(destroyed||!requested.equals(styleUri))return;style=s;s.addSource(empty("route"));s.addSource(empty("route-ahead"));s.addSource(empty("recording"));s.addSource(empty("arrows"));s.addSource(empty("approach"));s.addSource(empty("approach-arrows"));s.addSource(empty("events"));s.addSource(empty("bridges"));s.addSource(empty("position"));s.addImage("truck",userIcon);s.addImage("direction",arrow());RoutixTheme.Tokens ct=MapStyles.tokens(prefs);MapStyles.apply(s,ct);int guidanceColor=prefs.getInt("guidance_color",prefs.getInt("route_color",ct.accent)),recordColor=prefs.getInt("recording_color",ct.teal),aheadColor=prefs.getInt("ahead_color",ct.peach);String routeHex=hex(guidanceColor),traceStyle=prefs.getString("guidance_trace_style","arrows");s.addLayer(new LineLayer("route-glow","route").withProperties(lineColor(routeHex),lineWidth("soft".equals(traceStyle)?21f:17f),lineOpacity("soft".equals(traceStyle)?.30f:.18f),lineJoin("round"),lineCap("round")));s.addLayer(new LineLayer("recording-line","recording").withProperties(lineColor(hex(recordColor)),lineWidth(7f),lineOpacity(.72f),lineJoin("round"),lineCap("round")));s.addLayer(new LineLayer("route-casing","route").withProperties(lineColor(hex(ct.crust)),lineWidth(14f),lineOpacity(.98f),lineJoin("round"),lineCap("round")));LineLayer routeLayer=new LineLayer("route-line","route").withProperties(lineColor(routeHex),lineWidth(9f),lineJoin("round"),lineCap("round"));if("dashed".equals(traceStyle))routeLayer.setProperties(lineDasharray(new Float[]{1.4f,1.15f}));s.addLayer(routeLayer);s.addLayer(new LineLayer("route-ahead-glow","route-ahead").withProperties(lineColor(hex(aheadColor)),lineWidth(17f),lineOpacity(.28f),lineJoin("round"),lineCap("round")));s.addLayer(new LineLayer("route-ahead-line","route-ahead").withProperties(lineColor(hex(aheadColor)),lineWidth(10.5f),lineJoin("round"),lineCap("round")));s.addLayer(new SymbolLayer("route-arrows","arrows").withProperties(symbolPlacement("line"),symbolSpacing(46f),iconImage("direction"),iconSize(1.05f),iconAllowOverlap(false),iconKeepUpright(false),iconRotationAlignment("map"),iconOpacity("arrows".equals(traceStyle)?1f:0f)));s.addLayer(new LineLayer("approach-glow","approach").withProperties(lineColor(hex(ct.teal)),lineWidth(16f),lineOpacity(.20f),lineJoin("round"),lineCap("round")));s.addLayer(new LineLayer("approach-casing","approach").withProperties(lineColor(hex(ct.crust)),lineWidth(12f),lineOpacity(.96f),lineJoin("round"),lineCap("round")));s.addLayer(new LineLayer("approach-line","approach").withProperties(lineColor(hex(ct.sapphire)),lineWidth(8f),lineJoin("round"),lineCap("round")));s.addLayer(new SymbolLayer("approach-arrows-layer","approach-arrows").withProperties(symbolPlacement("line"),symbolSpacing(50f),iconImage("direction"),iconSize(.96f),iconAllowOverlap(false),iconKeepUpright(false),iconRotationAlignment("map"),iconOpacity(1f)));s.addLayer(new CircleLayer("bridge-dots","bridges").withProperties(circleColor(hex(ct.red)),circleRadius(12f),circleStrokeColor(hex(ct.crust)),circleStrokeWidth(2f)));s.addLayer(new SymbolLayer("bridge-labels","bridges").withProperties(textField(org.maplibre.android.style.expressions.Expression.get("label")),textSize(12f),textColor(hex(ct.text)),textHaloColor(hex(ct.crust)),textHaloWidth(3f),textOffset(new Float[]{0f,1.8f})));s.addLayer(new SymbolLayer("user","position").withProperties(iconImage("truck"),iconAllowOverlap(true),iconIgnorePlacement(true),iconSize(.80f)));s.addLayer(new CircleLayer("marker-dots","events").withProperties(circleColor(hex(prefs.getInt("marker_color",ct.peach))),circleRadius(6f),circleStrokeColor(hex(ct.mantle)),circleStrokeWidth(2f)));s.addLayer(new SymbolLayer("marker-labels","events").withProperties(textField(org.maplibre.android.style.expressions.Expression.get("label")),textSize(12f),textColor(hex(ct.text)),textHaloColor(hex(ct.mantle)),textHaloWidth(2f),textOffset(new Float[]{0f,1.5f})));renderRoute();renderAhead();renderRecording();renderApproach();renderEvents();renderBridges();update(location,false,Float.MAX_VALUE);});}
    private static String hex(int color){return String.format(Locale.US,"#%06X",(0xFFFFFF&color));}
    private static GeoJsonSource empty(String id){return new GeoJsonSource(id,FeatureCollection.fromFeatures(new Feature[0]));}
    private Bitmap arrow(){Bitmap b=Bitmap.createBitmap(44,34,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(RoutixTheme.from(prefs).text);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setShadowLayer(3,0,1,RoutixTheme.from(prefs).crust);Path path=new Path();path.moveTo(11,6);path.lineTo(29,17);path.lineTo(11,28);c.drawPath(path,p);return b;}
    void setRoute(List<org.osmdroid.util.GeoPoint> points){List<org.osmdroid.util.GeoPoint> next=points==null?Collections.emptyList():points;long signature=signature(next);if(signature==routeSignature)return;routeSignature=signature;route=new ArrayList<>(next);if(visible)renderRoute();}
    void setAheadRoute(List<org.osmdroid.util.GeoPoint> points){List<org.osmdroid.util.GeoPoint> next=points==null?Collections.emptyList():points;long signature=signature(next);if(signature==aheadSignature)return;aheadSignature=signature;aheadRoute=new ArrayList<>(next);if(visible)renderAhead();}
    void setRecordingSegments(List<List<org.osmdroid.util.GeoPoint>> segments){
        if(segments==null)segments=Collections.emptyList();
        long signature=1;
        for(List<org.osmdroid.util.GeoPoint> segment:segments)
            if(segment!=null&&!segment.isEmpty())signature=31*signature+signature(segment);
        if(signature==recordingSignature)return;
        List<List<org.osmdroid.util.GeoPoint>> copy=new ArrayList<>();
        for(List<org.osmdroid.util.GeoPoint> segment:segments)
            if(segment!=null&&!segment.isEmpty())copy.add(new ArrayList<>(segment));
        recordingSignature=signature;
        recordingSegments=copy;
        recordingRoute=Collections.emptyList();
        if(visible)renderRecording();
    }
    void setRecordingRoute(List<org.osmdroid.util.GeoPoint> points){recordingSegments=Collections.emptyList();List<org.osmdroid.util.GeoPoint> next=points==null?Collections.emptyList():points;long signature=signature(next);if(signature==recordingSignature)return;recordingSignature=signature;recordingRoute=new ArrayList<>(next);if(visible)renderRecording();}
    void setApproach(List<org.osmdroid.util.GeoPoint> points){List<org.osmdroid.util.GeoPoint> next=points==null?Collections.emptyList():points;long signature=signature(next);if(signature==approachSignature)return;approachSignature=signature;approach=new ArrayList<>(next);if(visible)renderApproach();}
    void setEvents(List<RouteStore.Event> values){List<RouteStore.Event> next=values==null?Collections.emptyList():values;int signature=eventsSignature(next);if(signature==eventsSignature)return;eventsSignature=signature;events=new ArrayList<>(next);if(visible)renderEvents();}
    private static long signature(List<org.osmdroid.util.GeoPoint> points){if(points==null||points.isEmpty())return 0;long h=points.size();int[] index={0,points.size()/2,points.size()-1};for(int i:index){org.osmdroid.util.GeoPoint p=points.get(i);h=31*h+Double.doubleToLongBits(p.getLatitude());h=31*h+Double.doubleToLongBits(p.getLongitude());}return h;}
    private static int eventsSignature(List<RouteStore.Event> values){int h=1;if(values!=null)for(RouteStore.Event e:values){h=31*h+(e.type==null?0:e.type.hashCode());h=31*h+(e.label==null?0:e.label.hashCode());long lat=Double.doubleToLongBits(e.lat),lon=Double.doubleToLongBits(e.lon);h=31*h+(int)(lat^(lat>>>32));h=31*h+(int)(lon^(lon>>>32));h=31*h+(int)(e.time^(e.time>>>32));}return h;}
    void setBridges(List<RoadRestrictionProvider.Bridge> values){
        int signature=1;
        if(values!=null)for(RoadRestrictionProvider.Bridge b:values){
            signature=31*signature+Double.hashCode(b.lat);
            signature=31*signature+Double.hashCode(b.lon);
            signature=31*signature+(b.height==null?0:b.height.hashCode());
        }
        if(signature==bridgesSignature)return;
        bridgesSignature=signature;
        bridges=values==null?Collections.emptyList():new ArrayList<>(values);
        if(visible)renderBridges();
    }
    private void renderBridges(){if(style==null)return;GeoJsonSource source=style.getSourceAs("bridges");if(source==null)return;List<Feature> fs=new ArrayList<>();for(RoadRestrictionProvider.Bridge b:bridges){Feature x=Feature.fromGeometry(Point.fromLngLat(b.lon,b.lat));x.addStringProperty("label","↕ "+b.height);fs.add(x);}source.setGeoJson(FeatureCollection.fromFeatures(fs));}
    private void renderEvents(){if(style==null)return;GeoJsonSource source=style.getSourceAs("events");if(source==null)return;List<Feature> features=new ArrayList<>();for(RouteStore.Event e:events){Feature f=Feature.fromGeometry(Point.fromLngLat(e.lon,e.lat));f.addStringProperty("label",e.label);features.add(f);}source.setGeoJson(FeatureCollection.fromFeatures(features));}
    private void renderRoute(){renderLine("route","arrows",route,1100);}
    private void renderAhead(){renderLine("route-ahead",null,aheadRoute,0);}
    private void renderRecording(){
        if(recordingSegments.isEmpty()){renderLine("recording",null,recordingRoute,0);return;}
        if(style==null||!style.isFullyLoaded())return;
        GeoJsonSource source=style.getSourceAs("recording");if(source==null)return;
        List<Feature> features=new ArrayList<>();
        for(List<org.osmdroid.util.GeoPoint> segment:recordingSegments){
            if(segment.size()<2)continue;
            List<Point> geometry=new ArrayList<>();
            for(org.osmdroid.util.GeoPoint p:segment)geometry.add(Point.fromLngLat(p.getLongitude(),p.getLatitude()));
            features.add(Feature.fromGeometry(LineString.fromLngLats(geometry)));
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features));
    }
    private void renderApproach(){renderLine("approach","approach-arrows",approach,1300);}
    private void renderLine(String sourceId,String arrowsId,List<org.osmdroid.util.GeoPoint> input,double arrowHorizon){if(style==null||!style.isFullyLoaded())return;GeoJsonSource source=style.getSourceAs(sourceId);if(source==null)return;List<Point> p=new ArrayList<>();int stride=Math.max(1,(int)Math.ceil(input.size()/(double)MAX_ROUTE_RENDER_POINTS));for(int i=0;i<input.size();i+=stride){org.osmdroid.util.GeoPoint x=input.get(i);p.add(Point.fromLngLat(x.getLongitude(),x.getLatitude()));}if(!input.isEmpty()&&((input.size()-1)%stride)!=0){org.osmdroid.util.GeoPoint x=input.get(input.size()-1);p.add(Point.fromLngLat(x.getLongitude(),x.getLatitude()));}int aheadEnd=0;double distance=0;for(int i=1;i<p.size();i++){float[] d=new float[1];Location.distanceBetween(p.get(i-1).latitude(),p.get(i-1).longitude(),p.get(i).latitude(),p.get(i).longitude(),d);distance+=d[0];aheadEnd=i;if(distance>arrowHorizon)break;}List<Point> ahead=new ArrayList<>();int aheadCount=aheadEnd+1,aheadStride=Math.max(1,(int)Math.ceil(aheadCount/(double)MAX_ARROW_RENDER_POINTS));for(int i=0;i<=aheadEnd&&i<p.size();i+=aheadStride)ahead.add(p.get(i));if(aheadEnd>0&&aheadEnd<p.size()&&(ahead.isEmpty()||ahead.get(ahead.size()-1)!=p.get(aheadEnd)))ahead.add(p.get(aheadEnd));GeoJsonSource arrows=arrowsId==null?null:style.getSourceAs(arrowsId);if(arrows!=null)arrows.setGeoJson(ahead.size()<2?FeatureCollection.fromFeatures(new Feature[0]):FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(LineString.fromLngLats(ahead))}));source.setGeoJson(p.size()<2?FeatureCollection.fromFeatures(new Feature[0]):FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(LineString.fromLngLats(p))}));}
    void update(Location fix,boolean guiding){update(fix,guiding,Float.MAX_VALUE);}
    void update(Location fix,boolean guiding,float nextActionDistance){
        location=fix;guidingMode=guiding;actionDistance=nextActionDistance;if(!visible||map==null||style==null||fix==null||destroyed)return;
        if(!MapStyles.uri(prefs).equals(styleUri)){refreshStyle();return;}
        boolean accurate=!fix.hasAccuracy()||fix.getAccuracy()<35f;float derivedDistance=0f;long derivedDt=0L;
        if(headingFix!=null){derivedDistance=headingFix.distanceTo(fix);derivedDt=fix.getTime()-headingFix.getTime();}
        boolean derivedMoving=accurate&&headingFix!=null&&derivedDt>0&&derivedDt<=10000&&derivedDistance>=4f;
        boolean moving=(fix.hasSpeed()&&fix.getSpeed()>.8f)||derivedMoving;
        if(heading&&accurate){double target=Double.NaN;if(fix.hasBearing()&&fix.hasSpeed()&&fix.getSpeed()>.8f)target=fix.getBearing();else if(derivedMoving)target=headingFix.bearingTo(fix);if(!Double.isNaN(target)){bearing=bearingReady?MapStyles.smoothBearing(bearing,target):target;bearingReady=true;}}
        if(accurate&&(headingFix==null||derivedDt<=0||derivedDt>10000||derivedDistance>=4f))headingFix=new Location(fix);
        animatePosition(fix,moving);
        if(!follow)return;
        long now=SystemClock.elapsedRealtime();
        // Camera updates overlap slightly with the GPS cadence: no stop/start sensation between fixes.
        long throttle=prefs.getBoolean("battery_saver",true)?(moving?650:2600):350;if(now-lastCameraMs<throttle)return;lastCameraMs=now;
        Location target=predict(fix,moving);double zoom=guiding?zoomFor(nextActionDistance):16.8;
        CameraPosition next=new CameraPosition.Builder(map.getCameraPosition()).target(new LatLng(target.getLatitude(),target.getLongitude())).zoom(zoom).bearing(heading&&bearingReady?bearing:0).tilt(guiding?30:0).build();
        map.easeCamera(CameraUpdateFactory.newCameraPosition(next),moving?(prefs.getBoolean("battery_saver",true)?900:650):700);
    }
    private void animatePosition(Location fix,boolean moving){
        GeoJsonSource source=style==null?null:style.getSourceAs("position");if(source==null)return;
        long now=SystemClock.elapsedRealtime();if(now-lastPositionFrameMs<90&&visualLocation!=null)return;lastPositionFrameMs=now;
        if(visualLocation==null||visualLocation.distanceTo(fix)>80){visualLocation=new Location(fix);}
        else{float speed=fix.hasSpeed()?fix.getSpeed():0f;float alpha=moving?(speed>12?.58f:.42f):.28f;visualLocation.setLatitude(visualLocation.getLatitude()+(fix.getLatitude()-visualLocation.getLatitude())*alpha);visualLocation.setLongitude(visualLocation.getLongitude()+(fix.getLongitude()-visualLocation.getLongitude())*alpha);visualLocation.setTime(fix.getTime());}
        source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(visualLocation.getLongitude(),visualLocation.getLatitude())));
    }
    private Location predict(Location fix,boolean moving){
        Location out=new Location(fix);if(!moving||!fix.hasSpeed()||!bearingReady)return out;
        // A small look-ahead hides GPS latency without changing recorded/guidance coordinates.
        double meters=Math.min(18,Math.max(0,fix.getSpeed())*.65),r=6371000d,br=Math.toRadians(bearing),lat=Math.toRadians(fix.getLatitude());
        out.setLatitude(fix.getLatitude()+Math.toDegrees(meters*Math.cos(br)/r));double cos=Math.max(.15,Math.cos(lat));out.setLongitude(fix.getLongitude()+Math.toDegrees(meters*Math.sin(br)/(r*cos)));return out;
    }
    private double zoomFor(float meters){if(!Float.isFinite(meters))return 17.2;if(meters<55)return 18.45;if(meters<140)return 18.0;if(meters<320)return 17.55;if(meters<700)return 17.1;return 16.75;}
    void recenter(Location l,boolean guiding){follow=true;lastCameraMs=0;update(l,guiding,actionDistance);}
    void heading(boolean enabled){heading=enabled;follow=true;lastCameraMs=0;if(map==null)return;double target=enabled&&bearingReady?bearing:0;map.easeCamera(CameraUpdateFactory.bearingTo(target),320);if(location!=null)update(location,guidingMode,actionDistance);}
    void setVisible(boolean enabled){if(visible==enabled&&view.getVisibility()==(enabled?View.VISIBLE:View.GONE))return;visible=enabled;view.setVisibility(enabled?View.VISIBLE:View.GONE);if(enabled){renderRoute();renderAhead();renderRecording();renderApproach();renderEvents();renderBridges();}}
    void inset(int top){/* Attribution is rendered by the activity above the dock. */}
    void onStart(){view.onStart();}void onResume(){view.onResume();}void onPause(){view.onPause();}void onStop(){view.onStop();}
    void onSaveInstanceState(Bundle state){view.onSaveInstanceState(state);}void onLowMemory(){view.onLowMemory();}
    void onDestroy(){destroyed=true;style=null;map=null;location=null;headingFix=null;visualLocation=null;route=Collections.emptyList();aheadRoute=Collections.emptyList();approach=Collections.emptyList();recordingRoute=Collections.emptyList();events=Collections.emptyList();routeSignature=Long.MIN_VALUE;aheadSignature=Long.MIN_VALUE;approachSignature=Long.MIN_VALUE;recordingSignature=Long.MIN_VALUE;eventsSignature=Integer.MIN_VALUE;view.onDestroy();if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);}
}
