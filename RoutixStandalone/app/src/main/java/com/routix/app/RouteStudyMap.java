package com.routix.app;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.BoundingBox;
import java.util.*;

/** Isolated rehearsal/comparison map: cannot move real navigation progress. */
final class RouteStudyMap {
    private final RoutixTheme.Tokens theme;
    private final RoutixActivity host;private final RouteStore store;private final RouteStore.Summary before,route;
    private final RouteComparison.Result comparison;private final List<PaperRoute.Step> oldStreets,streets;
    private final Handler handler=new Handler(Looper.getMainLooper());private final List<GeoPoint> points=new ArrayList<>();
    private final List<Polyline> oldLines=new ArrayList<>(),newLines=new ArrayList<>();
    private Dialog dialog;private MapView map;private Marker cursor;private TextView status;private Button play;
    private SeekBar seek;private double[] cumulative,eventDistance;private double position;private int speed=4;private boolean playing,dragging;private long lastTick;
    RouteStudyMap(RoutixActivity h,RouteStore s,RouteStore.Summary a,RouteStore.Summary b,RouteComparison.Result c,List<PaperRoute.Step> oldStreets,List<PaperRoute.Step> streets){host=h;theme=RoutixTheme.from(h);store=s;before=a;route=b==null?a:b;comparison=c;this.oldStreets=oldStreets;this.streets=streets;}
    private int dp(int n){return (int)(n*host.getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String s,int size){TextView t=new TextView(host);t.setText(s);t.setTextSize(size);t.setTextColor(theme.text);t.setPadding(dp(12),dp(5),dp(12),dp(5));return t;}
    private Button button(String s,Runnable action){Button b=new Button(host);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(theme.onAccent());b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(theme.accent));b.setMinHeight(dp(52));b.setOnClickListener(v->action.run());return b;}
    void show(){
        dialog=new Dialog(host,RoutixTheme.activityStyle(host.getSharedPreferences("routix",0)));LinearLayout panel=new LinearLayout(host);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(theme.base);
        LinearLayout heading=new LinearLayout(host);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(label(comparison==null?"Reconnaissance":"Comparaison",21),new LinearLayout.LayoutParams(0,-2,1));heading.addView(button("Fermer",this::close));panel.addView(heading);
        panel.addView(label(comparison==null?store.displayName(route.file):store.displayName(before.file)+" → "+store.displayName(route.file),14));
        map=new MapView(host);map.setMultiTouchControls(true);map.setTilesScaledToDpi(true);map.getController().setZoom(17.0);panel.addView(map,new LinearLayout.LayoutParams(-1,0,1));
        status=label("",13);panel.addView(status);
        for(RouteStore.Point p:route.points)points.add(new GeoPoint(p.lat,p.lon));
        if(comparison==null)buildPlayback(panel);else buildComparison(panel);
        dialog.setContentView(panel);dialog.setOnDismissListener(d->{playing=false;handler.removeCallbacksAndMessages(null);if(map!=null){map.onPause();map.onDetach();map=null;}});dialog.show();dialog.getWindow().setLayout(-1,-1);map.onResume();
        map.post(()->{if(map==null||points.isEmpty())return;List<GeoPoint> bounds=new ArrayList<>(points);if(comparison!=null)for(RouteStore.Point p:before.points)bounds.add(new GeoPoint(p.lat,p.lon));try{map.zoomToBoundingBox(BoundingBox.fromGeoPoints(bounds),false,dp(40));}catch(Exception ignored){map.getController().setCenter(points.get(0));}});
    }
    private Polyline line(List<GeoPoint> pts,int color){Polyline l=new Polyline();l.setPoints(pts);l.getOutlinePaint().setColor(color);l.getOutlinePaint().setStrokeWidth(dp(5));map.getOverlays().add(l);return l;}
    private void buildComparison(LinearLayout panel){
        addComparisonLines(comparison.before,true,oldLines);addComparisonLines(comparison.after,false,newLines);
        status.setText(String.format(Locale.FRANCE,"Vert : +%.0f m · Rouge : −%.0f m\nOrange : %.0f m en sens opposé%s\nComparaison géométrique indicative (tolérance 30 m). Les passages répétés peuvent être ambigus.",comparison.addedM,comparison.removedM,comparison.reversedM,comparison.orderChanged?" · ordre différent détecté":""));
        LinearLayout toggles=new LinearLayout(host);for(boolean old:new boolean[]{true,false}){CheckBox box=new CheckBox(host);box.setText(old?"Référence":"Comparée");box.setTextColor(theme.text);box.setChecked(true);box.setOnCheckedChangeListener((v,on)->{for(Polyline l:old?oldLines:newLines)l.setEnabled(on);if(map!=null)map.invalidate();});toggles.addView(box,new LinearLayout.LayoutParams(0,-2,1));}panel.addView(toggles);
        Set<String> changed=new LinkedHashSet<>();changedNames(before,comparison.before,oldStreets,"Retirée : ",changed);changedNames(route,comparison.after,streets,"Modifiée : ",changed);
        ScrollView scroll=new ScrollView(host);TextView names=label(changed.isEmpty()?"Prépare les noms de rues dans les outils de chaque tournée pour identifier les portions colorées.":android.text.TextUtils.join("\n",changed),13);scroll.addView(names);panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(80)));
    }
    private void changedNames(RouteStore.Summary summary,List<RouteComparison.Segment> segments,List<PaperRoute.Step> names,String prefix,Set<String> out){
        if(names==null||names.isEmpty())return;double[] distances=new double[summary.points.size()];for(int i=1;i<distances.length;i++)distances[i]=distances[i-1]+RouteComparison.distance(summary.points.get(i-1),summary.points.get(i));int at=0;
        for(RouteComparison.Segment s:segments){while(at+1<names.size()&&names.get(at+1).index<distances.length&&distances[names.get(at+1).index]<=s.along)at++;if(s.kind!=RouteComparison.SAME)out.add(prefix+names.get(at).name);}
    }
    private void addComparisonLines(List<RouteComparison.Segment> segments,boolean old,List<Polyline> out){
        List<GeoPoint> run=new ArrayList<>();int kind=-1;
        for(RouteComparison.Segment s:segments){int k=s.kind;if(k!=kind&&!run.isEmpty()){out.add(line(run,color(kind,old)));run=new ArrayList<>();}if(run.isEmpty())run.add(new GeoPoint(s.a.lat,s.a.lon));run.add(new GeoPoint(s.b.lat,s.b.lon));kind=k;}
        if(!run.isEmpty())out.add(line(run,color(kind,old)));
    }
    private int color(int kind,boolean old){return kind==RouteComparison.ADDED?theme.green:kind==RouteComparison.REMOVED?theme.red:kind==RouteComparison.REVERSED?theme.peach:old?theme.overlay1:theme.blue;}
    private void buildPlayback(LinearLayout panel){
        line(points,theme.accent);RouteArrowsOverlay arrows=new RouteArrowsOverlay();arrows.setPoints(points);map.getOverlays().add(arrows);
        cumulative=new double[route.points.size()];for(int i=1;i<cumulative.length;i++)cumulative[i]=cumulative[i-1]+RouteComparison.distance(route.points.get(i-1),route.points.get(i));
        eventDistance=new double[route.events.size()];
        for(int j=0;j<route.events.size();j++){RouteStore.Event e=route.events.get(j);int nearest=0;double best=Double.MAX_VALUE;for(int i=0;i<route.points.size();i++){RouteStore.Point p=route.points.get(i);double d=e.time>0&&p.time>0?Math.abs((double)e.time-p.time):RouteComparison.distance(p,new RouteStore.Point(e.lat,e.lon,0,0));if(d<best){best=d;nearest=i;}}eventDistance[j]=cumulative[nearest];Marker m=new Marker(map);m.setPosition(new GeoPoint(e.lat,e.lon));m.setTitle(e.label);map.getOverlays().add(m);}
        cursor=new Marker(map);cursor.setPosition(points.get(0));cursor.setTitle("Position simulée");map.getOverlays().add(cursor);
        seek=new SeekBar(host);seek.setMax(1000);panel.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean user){if(user){position=total()*value/1000.;render();}}public void onStartTrackingTouch(SeekBar s){dragging=true;}public void onStopTrackingTouch(SeekBar s){dragging=false;lastTick=SystemClock.elapsedRealtime();}});
        LinearLayout controls=new LinearLayout(host);play=button("Lecture",()->{if(position>=total())position=0;playing=!playing;play.setText(playing?"Pause":"Lecture");lastTick=SystemClock.elapsedRealtime();handler.removeCallbacks(tick);if(playing)handler.post(tick);});controls.addView(play,new LinearLayout.LayoutParams(0,-2,1));
        Button rate=button("×4",()->{});rate.setOnClickListener(v->{speed=speed==1?4:speed==4?10:1;rate.setText("×"+speed);});controls.addView(rate,new LinearLayout.LayoutParams(0,-2,1));controls.addView(button("Repère suivant",()->{double target=total();for(double d:eventDistance)if(d>position+1)target=Math.min(target,d);position=target;render();}),new LinearLayout.LayoutParams(0,-2,1));panel.addView(controls);
        render();
    }
    private double total(){return cumulative[cumulative.length-1];}
    private final Runnable tick=new Runnable(){public void run(){if(!playing||map==null)return;long now=SystemClock.elapsedRealtime();if(!dragging)position=Math.min(total(),position+Math.min(1000,now-lastTick)/1000.*8.3333*speed);lastTick=now;render();if(position>=total()){playing=false;play.setText("Rejouer");}else handler.postDelayed(this,100);}};
    private void render(){
        if(map==null||cumulative==null)return;int index=Arrays.binarySearch(cumulative,position);if(index<0)index=Math.max(0,-index-2);index=Math.min(index,cumulative.length-2);double len=cumulative[index+1]-cumulative[index];RouteStore.Point p=RouteComparison.interpolate(route.points.get(index),route.points.get(index+1),len<=0?0:Math.max(0,Math.min(1,(position-cumulative[index])/len)));
        GeoPoint pos=new GeoPoint(p.lat,p.lon);cursor.setPosition(pos);map.getController().setCenter(pos);seek.setProgress(total()<=0?0:(int)(position*1000/total()));
        String street="";if(streets!=null)for(PaperRoute.Step step:streets){if(step.index>index)break;street=step.name;}
        String event="Aucun autre repère";double next=Double.MAX_VALUE;for(int i=0;i<eventDistance.length;i++){double d=eventDistance[i]-position;if(d>=-8&&d<next){next=d;event=route.events.get(i).label+" · "+Math.max(0,Math.round(d))+" m";}}
        status.setText(String.format(Locale.FRANCE,"SIMULATION · %.0f / %.0f m · base 30 km/h ×%d\n%s%s",position,total(),speed,street.isEmpty()?"":street+"\n",event));map.invalidate();
    }
    void pause(){playing=false;handler.removeCallbacks(tick);if(play!=null)play.setText("Lecture");if(map!=null)map.onPause();}
    void resume(){if(map!=null)map.onResume();}
    void close(){if(dialog!=null)dialog.dismiss();}
}
