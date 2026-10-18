package com.routix.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Routix navigation and route tools. */
public class RoutixActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION=42, NOTIFICATION_PERMISSION=43, IMPORT_GPX=1337, IMPORT_PLAN_IMAGE=1338, MAX_DISPLAY_ROUTE_POINTS=5000;
    private RoutixTheme.Tokens theme;
    private int BG,SURFACE,SURFACE2,TEXT,MUTED,GREEN,PEACH,RED,MAUVE,BLUE,PINK,TEAL,YELLOW;

    private SharedPreferences prefs; private RouteStore store; private RouteFolderStore folderStore; private WorkHoursStore hoursStore; private Location lastLocation; private final RoadRestrictionProvider roadRestrictions=new RoadRestrictionProvider();
    private ModernMapController modern; private TrackingService tracker; private boolean bound,visible,headingUp=true,offline; private Bundle mapState; private boolean recoveryOffered;
    private FrameLayout root; private MapView map; private View topBar,dock,recordSheet; private LinearLayout contentHost; private CompactSpeedometer headerSpeed; private TextView speedLimit; private ImageView mapLogo;
    private Polyline remainingLine,aheadLine,approachLine,recordingLine; private RouteArrowsOverlay routeArrows,approachArrows; private int systemTop,systemBottom; private String selectedTab="map"; private int accent;
    private boolean recording,paused,guiding,guidanceCameraFollow=true; private long recordingStarted,guidanceSession,lastOfflineCameraMs; private double recordedDistance;
    private DepartureNavigation.Result approachRoute; private int approachIndex; private boolean approachingStart,approachPending,finishSummaryShown; private long approachGeneration; private String approachRouteName;
    private float nextActionDistance=Float.MAX_VALUE;
    private TextView recordMain,recordStatus,recordDistance,recordPoints,recordEvents,reverseButton,twoSidesButton,pauseButton,guideCue,guideDistance,guideProgress;
    private File detailFile; private Bitmap planPreview; private PlanImageAnalyzer.Result planAnalysis;
    private GuidanceEngine guidance; private RouteStore.Summary guidingRoute;

    @Override protected void onCreate(Bundle state){
        setTheme(RoutixTheme.activityStyle(getSharedPreferences("routix",MODE_PRIVATE)));super.onCreate(state);mapState=state;WindowCompat.setDecorFitsSystemWindows(getWindow(),false);getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("routix",MODE_PRIVATE);applyThemeTokens();RoutixTheme.systemBars(this,theme);migrateMapPreference();accent=theme.accent;store=new RouteStore(this,prefs);folderStore=new RouteFolderStore(prefs);hoursStore=new WorkHoursStore(prefs);
        Configuration.getInstance().setCacheMapTileCount((short)64);Configuration.getInstance().setTileFileSystemCacheMaxBytes(64L*1024*1024);Configuration.getInstance().setTileFileSystemCacheTrimBytes(48L*1024*1024);Configuration.getInstance().setUserAgentValue(getPackageName()+"/2.1 Routix");Configuration.getInstance().setOsmdroidBasePath(new File(getCacheDir(),"osmdroid"));Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(),"osmdroid/tiles"));
        root=new FrameLayout(this);root.setBackgroundColor(BG);buildMap();topBar=buildTopBar();contentHost=new LinearLayout(this);contentHost.setOrientation(LinearLayout.VERTICAL);dock=buildDock();root.addView(topBar);root.addView(contentHost);root.addView(dock);setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,in)->{androidx.core.graphics.Insets b=in.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());systemTop=b.top;systemBottom=b.bottom;positionChrome();return in;});ViewCompat.requestApplyInsets(root);
        if(state!=null&&state.getString("detail_file")!=null)detailFile=new File(store.routesDir(),new File(state.getString("detail_file")).getName());showTab(state==null?"map":state.getString("workspace","map"),false);ensureLocation();applyRuntimePrefs();new Thread(()->{store.restoreBackups();runOnUiThread(()->{if(!isDestroyed()&&"routes".equals(selectedTab))showTab("routes",false);});},"restore-backups").start();
    }

    private void applyThemeTokens(){theme=RoutixTheme.from(prefs);BG=theme.background;SURFACE=theme.surface;SURFACE2=theme.surface0;TEXT=theme.content;MUTED=theme.secondaryContent;GREEN=theme.success;PEACH=theme.warning;RED=theme.error;MAUVE=theme.mauve;BLUE=theme.blue;PINK=theme.pink;TEAL=theme.teal;YELLOW=theme.yellow;}

    private void migrateMapPreference(){String s=prefs==null?null:prefs.getString("map_style",null);if(s==null||s.startsWith("apple_")||"terrain".equals(s))prefs.edit().putString("map_style","osm").apply();}

    private void buildMap(){
        map=new MapView(this);map.setUseDataConnection(false);map.setMultiTouchControls(true);map.setMinZoomLevel(3.0);map.setMaxZoomLevel(20.0);map.getController().setZoom(18.0);map.setHorizontalMapRepetitionEnabled(false);map.setVerticalMapRepetitionEnabled(false);map.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&guiding)guidanceCameraFollow=false;return false;});applyMapStyle();root.addView(map,new FrameLayout.LayoutParams(-1,-1));

        approachLine=new Polyline();approachLine.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);approachLine.getOutlinePaint().setStrokeJoin(Paint.Join.ROUND);map.getOverlays().add(approachLine);approachArrows=new RouteArrowsOverlay();map.getOverlays().add(approachArrows);
        recordingLine=new Polyline();recordingLine.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);recordingLine.getOutlinePaint().setStrokeJoin(Paint.Join.ROUND);recordingLine.getOutlinePaint().setColor(recordingColor());recordingLine.getOutlinePaint().setAlpha(184);recordingLine.getOutlinePaint().setStrokeWidth(dp(7));map.getOverlays().add(recordingLine);remainingLine=new Polyline();remainingLine.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);remainingLine.getOutlinePaint().setStrokeJoin(Paint.Join.ROUND);map.getOverlays().add(remainingLine);aheadLine=new Polyline();aheadLine.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);aheadLine.getOutlinePaint().setStrokeJoin(Paint.Join.ROUND);map.getOverlays().add(aheadLine);routeArrows=new RouteArrowsOverlay();map.getOverlays().add(routeArrows);
        mapLogo=new ImageView(this);mapLogo.setImageResource(R.drawable.ic_routix);mapLogo.setScaleType(ImageView.ScaleType.CENTER_CROP);mapLogo.setPadding(dp(4),dp(4),dp(4),dp(4));mapLogo.setBackground(surface(SURFACE,18));mapLogo.setElevation(dp(2));root.addView(mapLogo,new FrameLayout.LayoutParams(dp(52),dp(52)));
    }

    private void applyMapStyle(){
        if(map==null)return;map.setUseDataConnection(false);offline=false;
        FranceOfflineManager.Pack pack=FranceOfflineManager.find(prefs.getString("offline_pack",""));
        if(pack!=null&&FranceOfflineManager.isInstalled(this,pack))try{
            org.mapsforge.map.android.graphics.AndroidGraphicFactory.createInstance(getApplication());
            String name=MapStyles.dark(prefs)?"dark":"light";File theme=new File(getCacheDir(),"offline-"+name+".xml");
            try(java.io.InputStream in=getAssets().open("maps/"+name+".xml")){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int read;while((read=in.read(buffer))!=-1)bytes.write(buffer,0,read);java.nio.file.Files.write(theme.toPath(),MapStyles.offlineTheme(bytes.toString("UTF-8"),MapStyles.tokens(prefs)).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            org.osmdroid.mapsforge.MapsForgeTileSource source=org.osmdroid.mapsforge.MapsForgeTileSource.createFromFiles(new File[]{FranceOfflineManager.file(this,pack)},new org.mapsforge.map.rendertheme.ExternalRenderTheme(theme),"routix-"+name,"fr");
            map.setTileProvider(new org.osmdroid.mapsforge.MapsForgeTileProvider(new org.osmdroid.tileprovider.util.SimpleRegisterReceiver(this),source,null));offline=true;
        }catch(Exception e){DiagnosticLog.error("offline map",e);toast("Pack hors ligne illisible : carte en ligne utilisée");}
        if(modern!=null){modern.refreshStyle();modern.setVisible(!offline&&"map".equals(selectedTab));}
        map.setVisibility(offline&&"map".equals(selectedTab)?View.VISIBLE:View.GONE);map.setAlpha(offline?1:0);if(guiding)redrawGuidance();map.invalidate();
    }

    private Bitmap createPositionIcon(String style){
        int z=dp(prefs==null?54:prefs.getInt("position_icon_size",54));Bitmap b=Bitmap.createBitmap(z,z,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setShadowLayer(dp(5),0,dp(2),theme.crust);p.setColor(Color.argb(226,Color.red(accent),Color.green(accent),Color.blue(accent)));c.drawCircle(z/2f,z/2f,z*.43f,p);p.clearShadowLayer();p.setColor(theme.onAccent());
        if("Bonhomme".equals(style)){c.drawCircle(z*.5f,z*.31f,z*.09f,p);c.drawRoundRect(z*.43f,z*.40f,z*.57f,z*.67f,dp(4),dp(4),p);p.setStrokeWidth(dp(4));p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(z*.45f,z*.51f,z*.34f,z*.61f,p);c.drawLine(z*.55f,z*.51f,z*.66f,z*.61f,p);c.drawLine(z*.47f,z*.65f,z*.40f,z*.78f,p);c.drawLine(z*.53f,z*.65f,z*.60f,z*.78f,p);}
        else if("Voiture".equals(style)){c.drawRoundRect(z*.31f,z*.20f,z*.69f,z*.80f,dp(7),dp(7),p);p.setColor(theme.crust);c.drawRoundRect(z*.36f,z*.31f,z*.64f,z*.49f,dp(4),dp(4),p);p.setColor(theme.onAccent());c.drawRect(z*.27f,z*.30f,z*.32f,z*.45f,p);c.drawRect(z*.68f,z*.30f,z*.73f,z*.45f,p);c.drawRect(z*.27f,z*.58f,z*.32f,z*.73f,p);c.drawRect(z*.68f,z*.58f,z*.73f,z*.73f,p);}
        else if("Flèche".equals(style)){android.graphics.Path path=new android.graphics.Path();path.moveTo(z*.5f,z*.18f);path.lineTo(z*.72f,z*.76f);path.lineTo(z*.5f,z*.64f);path.lineTo(z*.28f,z*.76f);path.close();c.drawPath(path,p);}
        else if("Point".equals(style)){c.drawCircle(z*.5f,z*.5f,z*.18f,p);}
        else {c.drawRoundRect(z*.29f,z*.17f,z*.71f,z*.78f,dp(7),dp(7),p);p.setColor(theme.crust);c.drawRoundRect(z*.34f,z*.22f,z*.66f,z*.38f,dp(3),dp(3),p);p.setColor(theme.surface2);c.drawRoundRect(z*.34f,z*.44f,z*.66f,z*.69f,dp(4),dp(4),p);p.setColor(theme.onAccent());c.drawRect(z*.25f,z*.26f,z*.31f,z*.43f,p);c.drawRect(z*.69f,z*.26f,z*.75f,z*.43f,p);c.drawRect(z*.25f,z*.57f,z*.31f,z*.74f,p);c.drawRect(z*.69f,z*.57f,z*.75f,z*.74f,p);}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(theme.onAccent());c.drawCircle(z/2f,z/2f,z*.43f,p);return b;
    }

    private View buildTopBar(){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),dp(5),dp(8),dp(5));bar.setBackground(surface(SURFACE,24));bar.setElevation(dp(2));ImageView brandIcon=new ImageView(this);brandIcon.setImageResource(R.drawable.ic_routix);brandIcon.setContentDescription(getString(R.string.app_name));LinearLayout.LayoutParams brandLp=new LinearLayout.LayoutParams(dp(38),dp(38));brandLp.rightMargin=dp(5);bar.addView(brandIcon,brandLp);
        headerSpeed=new CompactSpeedometer(this);bar.addView(headerSpeed,new LinearLayout.LayoutParams(dp(116),dp(50)));speedLimit=text("—",15,Typeface.BOLD,TEXT);speedLimit.setGravity(Gravity.CENTER);speedLimit.setContentDescription("Limitation de vitesse OSM");speedLimit.setBackground(surface(SURFACE2,99));LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(dp(48),dp(42));sl.leftMargin=dp(4);bar.addView(speedLimit,sl);
        TextView status=text("GPS",11f,Typeface.BOLD,MUTED);status.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);status.setContentDescription("État GPS");status.setPadding(dp(7),0,dp(8),0);
        LinearLayout.LayoutParams statusLp=new LinearLayout.LayoutParams(0,-1,1);statusLp.width=0;bar.addView(status,statusLp);
        return bar;
    }
    private View buildMapControls(){
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView north=icon(headingUp?"↑":"N");north.setContentDescription("Basculer cap en haut ou nord en haut");
        north.setOnClickListener(v->{press(v);headingUp=!headingUp;north.setRotation(headingUp?0:-45);north.setContentDescription(headingUp?"Cap en haut":"Nord en haut");guidanceCameraFollow=true;if(modern!=null)modern.heading(headingUp);if(offline){if(headingUp&&lastLocation!=null&&lastLocation.hasBearing())map.setMapOrientation(360-lastLocation.getBearing());else map.setMapOrientation(0);}recenter();});
        actions.addView(north,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView loc=icon("◎");loc.setContentDescription("Recentrer la carte");loc.setOnClickListener(v->{press(v);guidanceCameraFollow=true;recenter();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(48),dp(48));lp.leftMargin=dp(6);actions.addView(loc,lp);
        return actions;
    }

    private View buildDock(){LinearLayout row=new LinearLayout(this);row.setPadding(dp(4),dp(4),dp(4),dp(4));row.setBackground(surface(SURFACE,24));row.setElevation(dp(2));tab(row,"⌖",getString(R.string.tab_map),"map");tab(row,"●",getString(R.string.tab_record),"record");tab(row,"≡",getString(R.string.tab_routes),"routes");tab(row,"•••",getString(R.string.tab_more),"more");return row;}
    private void tab(LinearLayout row,String glyph,String label,String key){TextView t=text(label,12,Typeface.BOLD,MUTED);TerrainIcon symbol=new TerrainIcon(key,MUTED);symbol.setBounds(0,0,dp(22),dp(22));t.setCompoundDrawables(null,symbol,null,null);t.setCompoundDrawablePadding(dp(4));t.setGravity(Gravity.CENTER);t.setTag(key);t.setContentDescription(label);t.setOnClickListener(v->showTab(key,true));row.addView(t,new LinearLayout.LayoutParams(0,-1,1));}

    private void showTab(String key,boolean animate){
        selectedTab=key;contentHost.animate().cancel();contentHost.setAlpha(1);contentHost.setTranslationY(0);contentHost.setScaleX(1);contentHost.setScaleY(1);contentHost.removeAllViews();boolean mapMode="map".equals(key);map.setVisibility(mapMode&&offline?View.VISIBLE:View.GONE);if(modern!=null)modern.setVisible(mapMode&&!offline);topBar.setVisibility(View.VISIBLE);if(mapLogo!=null)mapLogo.setVisibility(mapMode&&false?View.VISIBLE:View.GONE);
        if(mapMode){contentHost.addView(mapCard());}else if("record".equals(key))contentHost.addView(recordPage());else if("routes".equals(key))contentHost.addView(routesPage());else if("detail".equals(key)&&detailFile!=null)contentHost.addView(routeDetailPage(detailFile));else if("hours".equals(key))contentHost.addView(hoursPage());else if("gpxlab".equals(key))contentHost.addView(gpxLabPage());else if("planlab".equals(key))contentHost.addView(planLabPage());else if("settings".equals(key))contentHost.addView(settingsPage());else if("stats".equals(key))contentHost.addView(statsPage());else if("more".equals(key))contentHost.addView(morePage());recordSheet="record".equals(key)?contentHost:null;updateDock();positionChrome();
        if(mapMode&&guiding&&tracker!=null)updateRouteGuidance(lastLocation);if(animate&&RoutixTheme.motion(prefs)&&!(guiding&&prefs.getBoolean("battery_saver",true))){int d=animDuration();contentHost.setAlpha(0);contentHost.setTranslationY(dp((RoutixTheme.motion(prefs)&&prefs.getBoolean("rich_animations",false))?20:8));contentHost.setScaleX((RoutixTheme.motion(prefs)&&prefs.getBoolean("rich_animations",false))?.982f:1f);contentHost.setScaleY((RoutixTheme.motion(prefs)&&prefs.getBoolean("rich_animations",false))?.982f:1f);contentHost.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(d).start();}
    }
    private void updateDock(){ViewGroup r=(ViewGroup)dock;for(int i=0;i<r.getChildCount();i++){TextView t=(TextView)r.getChildAt(i);boolean on=selectedTab.equals(t.getTag())||("routes".equals(t.getTag())&&"detail".equals(selectedTab))||("more".equals(t.getTag())&&!"map".equals(selectedTab)&&!"record".equals(selectedTab)&&!"routes".equals(selectedTab)&&!"detail".equals(selectedTab));t.setTextColor(on?accent:MUTED);TerrainIcon symbol=new TerrainIcon(String.valueOf(t.getTag()),on?accent:MUTED);symbol.setBounds(0,0,dp(22),dp(22));t.setCompoundDrawables(null,symbol,null,null);t.setBackground(on?surface(SURFACE2,12):null);if((RoutixTheme.motion(prefs)&&prefs.getBoolean("rich_animations",false))&&!(guiding&&prefs.getBoolean("battery_saver",true)))t.animate().scaleX(on?1.055f:1f).scaleY(on?1.055f:1f).setDuration(180).start();}}

    private View mapCard(){
        LinearLayout floating=new LinearLayout(this);floating.setOrientation(LinearLayout.VERTICAL);View floatingControls=buildMapControls();LinearLayout.LayoutParams controlLayout=new LinearLayout.LayoutParams(-2,dp(guiding?46:52));controlLayout.gravity=Gravity.END;controlLayout.bottomMargin=dp(guiding?6:10);floating.addView(floatingControls,controlLayout);
        if(!guiding)return floating;
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(7),dp(8),dp(7));card.setBackground(surface(SURFACE,22));card.setElevation(dp(2));
        guideCue=text("GPS en attente…",17,Typeface.BOLD,TEXT);guideCue.setSingleLine(true);guideCue.setEllipsize(android.text.TextUtils.TruncateAt.END);guideCue.setGravity(Gravity.CENTER_VERTICAL);guideCue.setPadding(dp(10),0,dp(10),0);guideCue.setBackground(surface(SURFACE2,12));card.addView(guideCue,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout essential=new LinearLayout(this);essential.setGravity(Gravity.CENTER_VERTICAL);essential.setPadding(0,dp(5),0,0);
        guideDistance=compactMetric("—","RESTANT");essential.addView(guideDistance,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView follow=pill("Me suivre",SURFACE2);follow.setMinHeight(0);follow.setOnClickListener(v->{press(v);guidanceCameraFollow=true;recenter();});LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(46),1);fp.leftMargin=dp(5);essential.addView(follow,fp);
        TextView stop=pill("Arrêter",RED);stop.setMinHeight(0);stop.setOnClickListener(v->stopGuidance());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(46),.8f);sp.leftMargin=dp(5);essential.addView(stop,sp);
        TextView more=pill("•••",SURFACE2);more.setMinHeight(0);more.setContentDescription("Plus d’actions de guidage");LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(48),dp(46));mp.leftMargin=dp(5);essential.addView(more,mp);card.addView(essential);
        LinearLayout secondary=new LinearLayout(this);secondary.setGravity(Gravity.CENTER_VERTICAL);secondary.setPadding(0,dp(5),0,0);secondary.setVisibility(View.GONE);
        guideProgress=compactMetric("—","COUVERT");secondary.addView(guideProgress,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView resume=pill("Reprendre ici",SURFACE2);resume.setMinHeight(0);resume.setOnClickListener(v->resumeGuidanceHere());LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(44),1.2f);rp.leftMargin=dp(5);secondary.addView(resume,rp);card.addView(secondary);
        more.setOnClickListener(v->{press(v);boolean open=secondary.getVisibility()!=View.VISIBLE;secondary.setVisibility(open?View.VISIBLE:View.GONE);more.setText(open?"⌄":"•••");});
        floating.addView(card);return floating;
    }
    private TextView flagBubble(String glyph,int color){TextView t=text(glyph,22,Typeface.BOLD,color);t.setGravity(Gravity.CENTER);t.setBackground(surface(SURFACE2,23));t.setElevation(dp(4));return t;}

    private View recordPage(){
        ScrollView sv=new ScrollView(this);LinearLayout p=page(getString(R.string.screen_record),"");recordStatus=text(recording?(paused?"EN PAUSE":"ENREGISTREMENT ACTIF"):"PRÊT",13,Typeface.BOLD,recording?GREEN:MUTED);p.addView(recordStatus);LinearLayout stats=new LinearLayout(this);stats.setPadding(0,dp(12),0,dp(12));recordDistance=metric(formatDistance(recordedDistance),"DISTANCE");recordPoints=metric(String.valueOf(tracker==null?0:tracker.points.size()),"POINTS");recordEvents=metric(String.valueOf(tracker==null?0:tracker.events.size()),"REPÈRES");stats.addView(recordDistance,new LinearLayout.LayoutParams(0,dp(68),1));stats.addView(recordPoints,new LinearLayout.LayoutParams(0,dp(68),1));stats.addView(recordEvents,new LinearLayout.LayoutParams(0,dp(68),1));p.addView(stats);
        LinearLayout main=new LinearLayout(this);main.setOrientation(LinearLayout.VERTICAL);recordMain=pill(recording?"Terminer et sauvegarder":"Commencer l’enregistrement",recording?RED:accent);recordMain.setOnClickListener(v->{press(v);if(recording)finishRecording();else startRecording();});main.addView(recordMain,new LinearLayout.LayoutParams(-1,dp(60)));LinearLayout flags=new LinearLayout(this);flags.setPadding(0,dp(10),0,0);main.addView(flags);reverseButton=pill("Marche arrière",SURFACE2);twoSidesButton=pill("2 côtés",SURFACE2);reverseButton.setOnClickListener(v->addEvent("REVERSE","Marche arrière"));twoSidesButton.setOnClickListener(v->addEvent("TWO_SIDES","2 côtés"));LinearLayout.LayoutParams b1=new LinearLayout.LayoutParams(0,dp(54),1);b1.leftMargin=dp(8);flags.addView(reverseButton,b1);LinearLayout.LayoutParams b2=new LinearLayout.LayoutParams(0,dp(54),1);b2.leftMargin=dp(7);flags.addView(twoSidesButton,b2);p.addView(main);
        pauseButton=pill(paused?"Reprendre":"Pause",SURFACE2);pauseButton.setOnClickListener(v->togglePause());LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(46));pp.topMargin=dp(8);p.addView(pauseButton,pp);setFlagEnabled(recording);TextView info=text("Ajoute les repères au fil du parcours. La tournée est sauvegardée automatiquement.",12,Typeface.NORMAL,MUTED);info.setPadding(0,dp(17),0,0);p.addView(info);sv.addView(p);return sv;
    }

    private View routesPage(){
        ScrollView sv=new ScrollView(this);LinearLayout p=page("Tournées",store.routeFiles().size()+" tournée(s) • dossiers et parcours");
        LinearLayout actions=new LinearLayout(this);TextView imp=pill("Importer GPX",accent);imp.setOnClickListener(v->{press(v);importGpx();});TextView rec=pill("Nouvelle tournée",SURFACE2);rec.setOnClickListener(v->{press(v);showTab("record",true);});TextView folder=pill("Dossier",SURFACE2);folder.setOnClickListener(v->{press(v);createFolderDialog();});
        actions.addView(imp,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(48),1);rp.leftMargin=dp(6);actions.addView(rec,rp);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(48),.8f);fp.leftMargin=dp(6);actions.addView(folder,fp);p.addView(actions);
        EditText search=new EditText(this);search.setHint("Rechercher dans toutes les tournées");search.setHintTextColor(theme.secondaryContent);search.setTextColor(TEXT);search.setTextSize(14);search.setSingleLine(true);search.setPadding(dp(15),0,dp(15),0);search.setBackground(surface(SURFACE,18));LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,dp(48));sl.topMargin=dp(12);p.addView(search,sl);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams ll=new LinearLayout.LayoutParams(-1,-2);ll.topMargin=dp(5);p.addView(list,ll);List<File> files=store.routeFiles();renderRoutes(list,files,"");
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){renderRoutes(list,files,s.toString());}public void afterTextChanged(Editable e){}});sv.addView(p);return sv;
    }

    private void renderRoutes(LinearLayout list,List<File> files,String query){
        list.removeAllViews();String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);int shown=0,index=0;
        if(!q.isEmpty()){
            for(File f:files)if(store.displayName(f).toLowerCase(Locale.ROOT).contains(q)){list.addView(routeCard(f,index++));shown++;}
        }else{
            for(String folder:folderStore.folders()){
                List<File> inside=new ArrayList<>();for(File f:files)if(folder.equals(folderStore.folderOf(f)))inside.add(f);
                list.addView(folderHeader(folder,inside.size()));
                if(folderStore.expanded(folder))for(File f:inside){list.addView(routeCard(f,index++));shown++;}
            }
            List<File> rootFiles=new ArrayList<>();for(File f:files)if(RouteFolderStore.ROOT.equals(folderStore.folderOf(f)))rootFiles.add(f);
            if(!rootFiles.isEmpty()){TextView rootLabel=text("SANS DOSSIER  •  "+rootFiles.size(),10,Typeface.BOLD,MUTED);rootLabel.setPadding(dp(4),dp(15),0,dp(2));list.addView(rootLabel);for(File f:rootFiles){list.addView(routeCard(f,index++));shown++;}}
        }
        if(shown==0&&folderStore.folders().isEmpty()){TextView e=infoCard(q.isEmpty()?"Aucune tournée":"Aucun résultat",q.isEmpty()?"Importe un GPX ou enregistre ton premier parcours.":"La recherche couvre aussi les dossiers.");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(14);list.addView(e,lp);}
    }

    private View folderHeader(String folder,int count){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(10),dp(8),dp(10));row.setBackground(surface(SURFACE,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);row.setLayoutParams(lp);
        TextView arrow=text(folderStore.expanded(folder)?"⌄":"›",19,Typeface.BOLD,accent);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(34),dp(38)));
        LinearLayout title=new LinearLayout(this);title.setOrientation(LinearLayout.VERTICAL);title.addView(text(" "+folder,15,Typeface.BOLD,TEXT));title.addView(text(count+" tournée(s)",10,Typeface.NORMAL,MUTED));row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=icon("•••");more.setOnClickListener(v->{press(v);folderMenu(folder);});row.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));
        row.setOnClickListener(v->{folderStore.setExpanded(folder,!folderStore.expanded(folder));showTab("routes",false);});return row;
    }

    private void createFolderDialog(){EditText e=new EditText(this);e.setHint("Nom du dossier");e.setSingleLine(true);new RoutixDialogs.Builder(this).setTitle("Nouveau dossier").setView(e).setNegativeButton("Annuler",null).setPositiveButton("Créer",(d,w)->{boolean ok=folderStore.create(e.getText().toString());toast(ok?"Dossier créé":"Nom invalide ou déjà utilisé");if(ok)showTab("routes",false);}).show();}
    private void folderMenu(String folder){
        new RoutixDialogs.Builder(this).setTitle(folder).setItems(new String[]{"Ajouter des tournées","Renommer","Supprimer le dossier"},(d,w)->{
            if(w==0)addRoutesToFolder(folder);
            else if(w==1){EditText e=new EditText(this);e.setText(folder);e.setSingleLine(true);new RoutixDialogs.Builder(this).setTitle("Renommer le dossier").setView(e).setNegativeButton("Annuler",null).setPositiveButton("Renommer",(x,y)->{boolean ok=folderStore.rename(folder,e.getText().toString());toast(ok?"Dossier renommé":"Renommage impossible");if(ok)showTab("routes",false);}).show();}
            else new RoutixDialogs.Builder(this).setTitle("Supprimer le dossier ?").setMessage("Les tournées seront replacées à la racine. Aucun GPX ne sera supprimé.").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(x,y)->{folderStore.delete(folder);showTab("routes",false);}).show();
        }).show();
    }

    private void addRoutesToFolder(String folder){
        List<File> candidates=new ArrayList<>();for(File f:store.routeFiles())if(!folder.equals(folderStore.folderOf(f)))candidates.add(f);
        if(candidates.isEmpty()){toast("Toutes les tournées sont déjà dans ce dossier");return;}
        String[] labels=new String[candidates.size()];boolean[] checked=new boolean[candidates.size()];
        for(int i=0;i<candidates.size();i++){File f=candidates.get(i);String current=folderStore.folderOf(f);labels[i]=store.displayName(f)+(current.isEmpty()?"":"  •  "+current);}
        new RoutixDialogs.Builder(this).setTitle("Ajouter à "+folder).setMultiChoiceItems(labels,checked,(d,which,isChecked)->checked[which]=isChecked)
            .setNegativeButton("Annuler",null).setPositiveButton("Ajouter",(d,w)->{int moved=0;for(int i=0;i<candidates.size();i++)if(checked[i]&&folderStore.move(candidates.get(i),folder))moved++;folderStore.setExpanded(folder,true);toast(moved==0?"Aucune tournée sélectionnée":moved+" tournée(s) ajoutée(s)");showTab("routes",false);}).show();
    }

    private View routeCard(File f,int index){
        RouteStore.Summary s=store.parse(f);LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(15),dp(14),dp(15),dp(14));card.setBackground(surface(SURFACE,23));card.setElevation(dp(1));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(9);card.setLayoutParams(cp);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView fav=icon("star");TerrainIcon star=new TerrainIcon("star",store.isFavorite(f)?YELLOW:MUTED);star.setBounds(0,0,dp(22),dp(22));fav.setCompoundDrawables(star,null,null,null);fav.setContentDescription(store.isFavorite(f)?"Retirer des favoris":"Ajouter aux favoris");fav.setGravity(Gravity.CENTER);fav.setOnClickListener(v->{press(v);store.setFavorite(f,!store.isFavorite(f));showTab("routes",false);});head.addView(fav,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);TextView name=text(store.displayName(f),17,Typeface.BOLD,TEXT);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);names.addView(name);String folder=folderStore.folderOf(f);String source=f.getName().startsWith("Import_")?"IMPORT GPX":"TERRAIN ROUTIX";names.addView(text((folder.isEmpty()?"RACINE":folder.toUpperCase(Locale.ROOT))+" • "+formatDate(f.lastModified())+" • "+source,10,Typeface.NORMAL,MUTED));head.addView(names,new LinearLayout.LayoutParams(0,-2,1));TextView more=icon("•••");more.setOnClickListener(v->{press(v);routeMenu(f,s);});head.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));card.addView(head);
        RoutePreviewView preview=new RoutePreviewView(this);preview.setPoints(s.points);preview.setClipToOutline(true);preview.setBackground(surface(SURFACE2,18));LinearLayout.LayoutParams pv=new LinearLayout.LayoutParams(-1,dp(110));pv.topMargin=dp(10);card.addView(preview,pv);
        LinearLayout stats=new LinearLayout(this);stats.setPadding(0,dp(10),0,dp(9));stats.addView(summaryStat("DISTANCE",formatDistance(s.distanceM)),new LinearLayout.LayoutParams(0,-2,1));stats.addView(summaryStat("DURÉE",formatDuration(s.durationMs)),new LinearLayout.LayoutParams(0,-2,1));stats.addView(summaryStat("POINTS",String.valueOf(s.points.size())),new LinearLayout.LayoutParams(0,-2,1));stats.addView(summaryStat("REPÈRES",String.valueOf(s.events.size())),new LinearLayout.LayoutParams(0,-2,1));card.addView(stats);
        LinearLayout a=new LinearLayout(this);TextView guide=pill("Démarrer",accent);guide.setOnClickListener(v->{press(v);startGuidance(s);});TextView plan=pill("Plan",SURFACE2);plan.setOnClickListener(v->{press(v);PrintablePlan.open(this,s,store.displayName(f));});TextView lab=pill("GPS départ",SURFACE2);lab.setOnClickListener(v->{press(v);startGuidance(s);});a.addView(guide,new LinearLayout.LayoutParams(0,dp(52),1.2f));LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,dp(52),.8f);p1.leftMargin=dp(6);a.addView(plan,p1);LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,dp(52),.9f);p2.leftMargin=dp(6);a.addView(lab,p2);card.addView(a);
        TextView details=pill("Voir la tournée",SURFACE2);details.setOnClickListener(v->{detailFile=f;showTab("detail",true);});LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,dp(52));dl.topMargin=dp(8);card.addView(details,dl);
        animateCard(card,index);return card;
    }

    private View routeDetailPage(File file){
        RouteStore.Summary route=store.parse(file);ScrollView scroll=new ScrollView(this);
        LinearLayout page=page(store.displayName(file),formatDistance(route.distanceM)+" · "+formatDuration(route.durationMs));
        TextView back=pill("Toutes les tournées",SURFACE2);back.setOnClickListener(v->showTab("routes",true));page.addView(back,new LinearLayout.LayoutParams(-1,dp(52)));
        RoutePreviewView preview=new RoutePreviewView(this);preview.setPoints(route.points);LinearLayout.LayoutParams previewLayout=new LinearLayout.LayoutParams(-1,dp(200));previewLayout.topMargin=dp(16);page.addView(preview,previewLayout);
        page.addView(section("Prêt à repartir"));TextView start=pill("Démarrer le guidage",accent);start.setOnClickListener(v->startGuidance(route));page.addView(start,new LinearLayout.LayoutParams(-1,dp(58)));
        menu(page,"Plan de tournée",route.events.size()+" repères · "+route.points.size()+" points",()->PrintablePlan.open(this,route,store.displayName(file)));menu(page,"Améliorer la tournée","Optimiser les kilomètres · aperçu avant sauvegarde",()->improveRoute(file,route));
        StreetPassageStore.Insight insight=new StreetPassageStore(this).read(file.getName());
        if(insight!=null){page.addView(section("Intelligence de tournée"));LinearLayout intel=new LinearLayout(this);intel.setOrientation(LinearLayout.VERTICAL);intel.setPadding(dp(14),dp(12),dp(14),dp(12));intel.setBackground(surface(SURFACE,20));intel.addView(text(insight.unique+" rues reconnues • "+insight.passages+" passages • "+insight.repeats+" répétition"+(insight.repeats>1?"s":""),13,Typeface.BOLD,TEXT));intel.addView(text(formatDistance(insight.coveredMeters)+" de portions reconnues • "+insight.directedCells+" secteurs directionnels"+(insight.opposite>0?" • "+insight.opposite+" parcourus dans les 2 sens":""),11,Typeface.NORMAL,MUTED));int shown=Math.min(8,insight.streets.size());for(int i=0;i<shown;i++)intel.addView(text(insight.streets.get(i),11,Typeface.NORMAL,MUTED));if(insight.streets.size()>shown)intel.addView(text("+"+(insight.streets.size()-shown)+" autres rues",11,Typeface.BOLD,accent));page.addView(intel);}
        menu(page,"Dossier",folderStore.folderOf(file).isEmpty()?"Sans dossier":folderStore.folderOf(file),()->moveRouteDialog(file));
        menu(page,"Renommer",store.displayName(file),()->rename(file));menu(page,"Partager","Fichier GPX",()->share(file));
        menu(page,"Tous les outils","Favoris, duplication, amélioration, GPX Lab et suppression",()->routeMenu(file,route));scroll.addView(page);return scroll;
    }

    private void routeMenu(File f,RouteStore.Summary s){String[] x={store.isFavorite(f)?"Retirer des favoris":"Ajouter aux favoris","Déplacer dans un dossier","Renommer","Dupliquer","Améliorer la tournée","Partager le GPX","Générer le plan","Avancé : GPX Lab","Supprimer"};new RoutixDialogs.Builder(this).setTitle(store.displayName(f)).setMessage(formatDistance(s.distanceM)+" • "+formatDuration(s.durationMs)+" • "+s.points.size()+" points").setItems(x,(d,w)->{if(w==0){store.setFavorite(f,!store.isFavorite(f));showTab("routes",false);}else if(w==1)moveRouteDialog(f);else if(w==2)rename(f);else if(w==3){File copy=store.duplicate(f);if(copy!=null)folderStore.move(copy,folderStore.folderOf(f));showTab("routes",false);}else if(w==4)improveRoute(f,s);else if(w==5)share(f);else if(w==6)PrintablePlan.open(this,s,store.displayName(f));else if(w==7){prefs.edit().putString("gpx_lab_file",f.getName()).apply();showTab("gpxlab",true);}else confirmDelete(f);}).show();}
    private void improveRoute(File source,RouteStore.Summary original){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(18),dp(18),dp(18));TextView title=text("Améliorer la tournée",22,Typeface.BOLD,TEXT),status=text("Préparation…",13,Typeface.NORMAL,MUTED);android.widget.ProgressBar progress=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);panel.addView(title);panel.addView(status);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(8));pp.topMargin=dp(14);panel.addView(progress,pp);TextView note=text("L’original ne sera jamais modifié. OSM + OSRM sont utilisés uniquement après cette action.",11,Typeface.NORMAL,MUTED);note.setPadding(0,dp(12),0,0);panel.addView(note);
        AlertDialog wait=new RoutixDialogs.Builder(this).setView(panel).setNegativeButton("Annuler",(d,w)->{}).create();wait.setCanceledOnTouchOutside(false);wait.show();RoutixDialogs.style(wait,this);
        new Thread(()->{try{RouteImprovementEngine.Result result=RouteImprovementEngine.improve(original,(pct,label)->runOnUiThread(()->{progress.setProgress(pct);status.setText(label+" • "+pct+" %");}));runOnUiThread(()->{if(!wait.isShowing())return;wait.dismiss();showImprovementPreview(source,original,result);});}catch(Exception ex){DiagnosticLog.error("route improvement",ex);runOnUiThread(()->{if(wait.isShowing())wait.dismiss();new RoutixDialogs.Builder(this).setTitle("Amélioration impossible").setMessage(improvementError(ex)).setPositiveButton("OK",null).show();});}},"route-improvement").start();
    }
    private String improvementError(Exception e){String m=e.getMessage();if(m==null||m.trim().isEmpty())m="Le calcul n’a pas pu aboutir.";if(m.contains("timed out")||m.contains("Unable to resolve")||m.contains("réseau")||m.contains("HTTP"))return "Connexion OSM/OSRM indisponible. Vérifie le réseau puis réessaie.\n\n"+m;return m;}
    private void showImprovementPreview(File source,RouteStore.Summary original,RouteImprovementEngine.Result r){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(16),dp(18),dp(14));panel.addView(text("Aperçu de l’amélioration",22,Typeface.BOLD,TEXT));RouteCompareView preview=new RouteCompareView(this);preview.setRoutes(original.points,r.points,guidanceColor(),aheadColor());LinearLayout.LayoutParams pv=new LinearLayout.LayoutParams(-1,dp(250));pv.topMargin=dp(12);panel.addView(preview,pv);
        String gain=r.gainM()>0?formatDistance(r.gainM())+" gagnés • "+String.format(Locale.getDefault(),"%.1f %%",r.gainPct()):"aucun gain de distance";TextView stats=infoCard("Avant "+formatDistance(r.beforeM)+"  →  Après "+formatDistance(r.afterM),gain+" • "+r.requiredArcs+" passages OSM requis");LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=dp(10);panel.addView(stats,st);
        new RoutixDialogs.Builder(this).setView(scrollPanel(panel)).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer la version améliorée",(d,w)->{File saved=store.createImprovedRoute(source,r.points);if(saved!=null){folderStore.move(saved,folderStore.folderOf(source));toast("Version améliorée enregistrée");showTab("routes",false);}else toast("Enregistrement impossible");}).show();
    }

    private void moveRouteDialog(File f){List<String> folders=folderStore.folders();String[] labels=new String[folders.size()+1];labels[0]="Sans dossier";for(int i=0;i<folders.size();i++)labels[i+1]=""+folders.get(i);new RoutixDialogs.Builder(this).setTitle("Déplacer "+store.displayName(f)).setItems(labels,(d,w)->{folderStore.move(f,w==0?RouteFolderStore.ROOT:folders.get(w-1));showTab("routes",false);}).show();}
    private void rename(File f){EditText e=new EditText(this);e.setText(store.displayName(f));e.setSingleLine(true);new RoutixDialogs.Builder(this).setTitle("Nom de la tournée").setMessage("Le fichier GPX sera renommé lui aussi.").setView(e).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{String old=f.getName();File renamed=store.rename(f,e.getText().toString());if(renamed!=null)folderStore.migrateFileName(old,renamed.getName());toast(renamed==null?"Renommage impossible":"Tournée renommée");showTab("routes",false);}).show();}
    private void confirmDelete(File f){new RoutixDialogs.Builder(this).setTitle("Supprimer cette tournée ?").setMessage("Le fichier GPX et sa copie Routix seront supprimés.").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(a,b)->{folderStore.forget(f);boolean ok=store.delete(f);toast(ok?"Tournée supprimée":"Suppression impossible");showTab("routes",false);}).show();}
    private void share(File f){try{Uri u=FileProvider.getUriForFile(this,getPackageName()+".files",f);Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/gpx+xml");i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Partager la tournée"));}catch(Exception e){toast("Partage impossible");}}

    private View hoursPage(){
        ScrollView sv=new ScrollView(this);LinearLayout p=page(getString(R.string.screen_hours),"");String month=new SimpleDateFormat("yyyy-MM",Locale.FRANCE).format(new Date());List<WorkHoursStore.Entry> entries=hoursStore.entries();int total=hoursStore.totalForMonth(month),days=0;for(WorkHoursStore.Entry e:entries)if(month.equals(e.monthKey()))days++;
        LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.VERTICAL);summary.setPadding(dp(18),dp(16),dp(18),dp(16));summary.setBackground(surface(SURFACE,22));summary.setElevation(dp(1));summary.addView(text(monthLabel(month).toUpperCase(Locale.FRANCE),10,Typeface.BOLD,BLUE));summary.addView(text(formatMinutes(total),32,Typeface.BOLD,TEXT));summary.addView(text(days+" jour(s) • moyenne "+(days==0?"0h00":formatMinutes(total/days))+" / jour",11,Typeface.NORMAL,MUTED));p.addView(summary);
        TextView add=pill("Ajouter une journée",accent);add.setOnClickListener(v->showHoursEntrySheet());LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(50));ap.topMargin=dp(11);ap.bottomMargin=dp(10);p.addView(add,ap);
        for(WorkHoursStore.Entry e:entries){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(surface(SURFACE,20));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);LinearLayout date=new LinearLayout(this);date.setOrientation(LinearLayout.VERTICAL);date.addView(text(dayLabel(e.date),15,Typeface.BOLD,TEXT));date.addView(text(e.date,10,Typeface.NORMAL,MUTED));top.addView(date,new LinearLayout.LayoutParams(0,-2,1));TextView net=pill(formatMinutes(e.netMinutes()),SURFACE2);top.addView(net,new LinearLayout.LayoutParams(dp(86),dp(36)));card.addView(top);LinearLayout st=new LinearLayout(this);st.setPadding(0,dp(10),0,0);st.addView(summaryStat("DÉBUT",timeLabel(e.startMinutes)),new LinearLayout.LayoutParams(0,-2,1));st.addView(summaryStat("FIN",timeLabel(e.endMinutes)),new LinearLayout.LayoutParams(0,-2,1));st.addView(summaryStat("PAUSE",e.pauseMinutes+" min"),new LinearLayout.LayoutParams(0,-2,1));card.addView(st);TextView del=text("Supprimer",11,Typeface.BOLD,RED);del.setGravity(Gravity.END);del.setPadding(0,dp(8),0,0);del.setOnClickListener(v->{hoursStore.delete(e.id);showTab("hours",false);});card.addView(del);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);p.addView(card,lp);}sv.addView(p);return sv;
    }
    private void showHoursEntrySheet(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(18),dp(18),dp(16));panel.setBackground(surface(SURFACE,28));String date=todayKey();panel.addView(text("Ajouter des heures",24,Typeface.BOLD,TEXT));TextView sub=text(dayLabel(date)+" • "+date,11,Typeface.NORMAL,MUTED);sub.setPadding(0,dp(3),0,dp(13));panel.addView(sub);
        EditText start=hoursInput("Début • ex. 730",prefs.getString("hours_last_start",""));EditText end=hoursInput("Fin • ex. 1530",prefs.getString("hours_last_end",""));EditText pause=hoursInput("Pause • minutes",String.valueOf(prefs.getInt("hours_last_pause",30)));panel.addView(start,new LinearLayout.LayoutParams(-1,dp(52)));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(52));ep.topMargin=dp(8);panel.addView(end,ep);
        LinearLayout now=new LinearLayout(this);TextView startNow=pill("Début = maintenant",SURFACE2);TextView endNow=pill("Fin = maintenant",SURFACE2);startNow.setOnClickListener(v->start.setText(timeLabel(nowMinutes())));endNow.setOnClickListener(v->end.setText(timeLabel(nowMinutes())));now.addView(startNow,new LinearLayout.LayoutParams(0,dp(38),1));LinearLayout.LayoutParams n2=new LinearLayout.LayoutParams(0,dp(38),1);n2.leftMargin=dp(6);now.addView(endNow,n2);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(8);panel.addView(now,np);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(52));pp.topMargin=dp(9);panel.addView(pause,pp);
        TextView hint=text("730, 0730 ou 7:30 deviennent automatiquement 07:30.",10,Typeface.NORMAL,MUTED);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(8);hp.bottomMargin=dp(8);panel.addView(hint,hp);LinearLayout quick=new LinearLayout(this);for(int m:new int[]{0,20,30,45,60}){TextView q=pill(m+" min",SURFACE2);q.setTextSize(10);q.setOnClickListener(v->pause.setText(String.valueOf(m)));quick.addView(q,new LinearLayout.LayoutParams(0,dp(36),1));}panel.addView(quick);
        AlertDialog d=new RoutixDialogs.Builder(this).setView(scrollPanel(panel)).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",null).create();d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{int a=parseClock(start.getText().toString()),b=parseClock(end.getText().toString()),br=parsePositive(pause.getText().toString());if(a<0||b<0){toast("Heure invalide • exemple : 730 ou 1530");return;}int gross=b>=a?b-a:b+1440-a;if(br>=gross){toast("La pause doit être plus courte que le service");return;}hoursStore.add(date,a,b,br);prefs.edit().putString("hours_last_start",timeLabel(a)).putString("hours_last_end",timeLabel(b)).putInt("hours_last_pause",br).apply();d.dismiss();showTab("hours",true);}));d.show();RoutixDialogs.style(d,this);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(surface(BG,22));w.setDimAmount(.45f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);} }
    private EditText hoursInput(String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setTextSize(15);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setPadding(dp(15),0,dp(15),0);e.setBackground(surface(SURFACE2,18));if(value!=null)e.setText(value);return e;}

    private View gpxLabPage(){
        ScrollView sv=new ScrollView(this);LinearLayout p=page(getString(R.string.screen_gpx),"");List<File> files=store.routeFiles();if(files.isEmpty()){p.addView(infoCard("Aucun GPX","Importe ou enregistre une tournée pour afficher un avant / après."));sv.addView(p);return sv;}File f=files.get(0);String wanted=prefs.getString("gpx_lab_file",null);if(wanted!=null)for(File x:files)if(x.getName().equals(wanted)){f=x;break;}final File selected=f;RouteStore.Summary after=store.parse(selected);int input=prefs.getInt("import_input_"+selected.getName(),after.points.size()),output=prefs.getInt("import_output_"+selected.getName(),after.points.size()),invalid=prefs.getInt("import_invalid_"+selected.getName(),0),dup=prefs.getInt("import_duplicates_"+selected.getName(),0),simp=prefs.getInt("import_simplified_"+selected.getName(),Math.max(0,input-output));boolean matched=prefs.getBoolean("import_matched_"+selected.getName(),false);int confidence=prefs.getInt("import_confidence_"+selected.getName(),matched?0:100),generated=prefs.getInt("import_generated_steps_"+selected.getName(),0);
        TextView choose=pill("Fichier : "+store.displayName(selected)+"  ▾",SURFACE2);choose.setOnClickListener(v->{String[] labels=new String[files.size()];for(int i=0;i<files.size();i++)labels[i]=store.displayName(files.get(i));new RoutixDialogs.Builder(this).setTitle("GPX à analyser").setItems(labels,(d,w)->{prefs.edit().putString("gpx_lab_file",files.get(w).getName()).apply();showTab("gpxlab",false);}).show();});p.addView(choose,new LinearLayout.LayoutParams(-1,dp(50)));p.addView(section("Avant / après"));LinearLayout compare=new LinearLayout(this);compare.addView(bigMetric(String.valueOf(input),"AVANT\npoints bruts",PEACH),new LinearLayout.LayoutParams(0,dp(112),1));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(112),1);cp.leftMargin=dp(9);compare.addView(bigMetric(String.valueOf(output),"APRÈS\npoints utiles",GREEN),cp);p.addView(compare);TextView delta=infoCard("Transformation","−"+Math.max(0,input-output)+" points • "+invalid+" invalides • "+dup+" doublons • "+simp+" simplifiés");LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2);dl.topMargin=dp(9);p.addView(delta,dl);p.addView(section("Pipeline"));p.addView(stage("01","Validation GPS",invalid+" points rejetés",invalid>0?PEACH:GREEN));p.addView(stage("02","Déduplication",dup+" points trop proches supprimés",dup>0?BLUE:GREEN));p.addView(stage("03","Simplification conservatrice",simp+" points retirés sans casser les retours",MAUVE));p.addView(stage("04","Recalage routier",matched?("Actif • confiance "+confidence+" % • "+generated+" étapes ajoutées"):"Non appliqué / trace propre conservée",matched?TEAL:MUTED));p.addView(section("Contrôles"));p.addView(toggleRow("Optimiseur GPX","OFF = import brut : aucun point supprimé, simplifié ou recalé. ON = nettoyage et recalage routier.","gpx_match_roads",true,()->{}));sv.addView(p);return sv;
    }

    private View morePage(){ScrollView sv=new ScrollView(this);LinearLayout p=page(getString(R.string.screen_more),"");
        menu(p,getString(R.string.menu_hours),"",()->showTab("hours",true));menu(p,getString(R.string.screen_stats),"",()->showTab("stats",true));menu(p,getString(R.string.screen_settings),"",()->showTab("settings",true));
        menu(p,getString(R.string.menu_offline),"",this::offlineMenu);menu(p,getString(R.string.menu_updates),"",this::updateMenu);menu(p,getString(R.string.menu_diagnostics),"",this::exportDiagnostics);
        TextView advanced=pill("Avancé  ⌄",SURFACE2);p.addView(advanced,new LinearLayout.LayoutParams(-1,dp(52)));LinearLayout extra=new LinearLayout(this);extra.setOrientation(LinearLayout.VERTICAL);extra.setVisibility(View.GONE);menu(extra,getString(R.string.screen_gpx),"",()->showTab("gpxlab",true));menu(extra,getString(R.string.menu_plan_beta),"",()->showTab("planlab",true));p.addView(extra);advanced.setOnClickListener(v->extra.setVisibility(extra.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));sv.addView(p);return sv;}
    private View statsPage(){
        ScrollView sv=new ScrollView(this);LinearLayout p=page(getString(R.string.screen_stats),"");
        List<SessionStatsStore.Entry> entries=new SessionStatsStore(prefs).entries();
        long totalDuration=SessionStatsStore.totalDuration(entries);double totalDistance=SessionStatsStore.totalDistance(entries);
        double avgKmh=totalDuration>0?(totalDistance/1000d)/(totalDuration/3600000d):0;
        LinearLayout summary=new LinearLayout(this);summary.setPadding(0,0,0,dp(10));
        summary.addView(bigMetric(String.valueOf(entries.size()),"TOURNÉES",BLUE),new LinearLayout.LayoutParams(0,dp(100),1));
        LinearLayout.LayoutParams sm=new LinearLayout.LayoutParams(0,dp(100),1);sm.leftMargin=dp(8);summary.addView(bigMetric(formatDistance(totalDistance),"DISTANCE",GREEN),sm);
        p.addView(summary);
        LinearLayout totals=new LinearLayout(this);
        totals.addView(bigMetric(formatDuration(totalDuration),"DURÉE",MAUVE),new LinearLayout.LayoutParams(0,dp(100),1));
        LinearLayout.LayoutParams av=new LinearLayout.LayoutParams(0,dp(100),1);av.leftMargin=dp(8);totals.addView(bigMetric(String.format(Locale.FRANCE,"%.1f km/h",avgKmh),"MOYENNE",PEACH),av);p.addView(totals);
        p.addView(section("Historique"));
        if(entries.isEmpty())p.addView(infoCard("Aucune tournée terminée","Les prochaines tournées terminées apparaîtront automatiquement ici."));
        else for(int i=0;i<Math.min(30,entries.size());i++){SessionStatsStore.Entry e=entries.get(i);String name=e.route.isEmpty()?"Tournée":e.route.replaceFirst("\\.gpx$","");String body=formatDate(e.started)+" • "+formatDistance(e.distanceM)+" • "+formatDuration(e.durationMs);p.addView(infoCard(name,body));}
        sv.addView(p);return sv;
    }
    private void menu(LinearLayout p,String title,String subtitle,Runnable action){LinearLayout row=settingsRow(title,subtitle);row.setMinimumHeight(dp(64));row.setOnClickListener(v->action.run());p.addView(row);}
    private View settingsPage(){
        ScrollView sv=new ScrollView(this);LinearLayout p=page(getString(R.string.screen_settings),"");
        
        p.addView(colorSettingRow("Tracé d’enregistrement","recording_color",TEAL,()->{drawRecordingTrace();if(modern!=null)modern.refreshStyle();}));p.addView(colorSettingRow("Tracé de guidage","guidance_color",accent,()->{redrawGuidance();if(modern!=null)modern.refreshStyle();}));p.addView(colorSettingRow("50 prochains mètres","ahead_color",PEACH,()->{redrawGuidance();if(modern!=null)modern.refreshStyle();}));p.addView(stringChoiceRow("Style du tracé de guidage","guidance_trace_style",new String[]{"solid","dashed","arrows","soft"},new String[]{"━━━━  Ligne pleine","━ ━ ━  Ligne pointillée","➜ ➜ ➜  Flèches de direction","━━━━  Progressif doux"},"arrows",()->{if(modern!=null)modern.refreshStyle();redrawGuidance();}));
        p.addView(colorSettingRow("Couleur des repères","marker_color",PEACH,()->{if(modern!=null)modern.refreshStyle();}));
        p.addView(stringChoiceRow("Fond de carte","map_appearance",new String[]{"theme","light","dark"},new String[]{"Comme le thème","Clair","Sombre"},"theme",this::applyMapStyle));
        p.addView(toggleRow("Nuit automatique","Carte sombre de 20 h à 7 h.","auto_night",false,this::applyMapStyle));
        p.addView(stringChoiceRow("Icône de position","position_icon",new String[]{"Camion","Voiture","Flèche","Point"},new String[]{"Camion","Voiture","Flèche","Point"},"Camion",()->{if(modern!=null){modern.onPause();modern.onStop();modern.onDestroy();modern=null;createModernMap();modern.onResume();}}));
        p.addView(toggleRow("Guidage interne jusqu’au départ","Calcule et affiche directement le trajet d’approche dans Routix.","guide_to_start",true,()->{}));
        p.addView(choiceRow("Tolérance hors tracé","offroute_m",new int[]{25,35,45,60,80},new String[]{"25 m","35 m","45 m","60 m","80 m"},45,()->{}));
        p.addView(toggleRow("Rappels vocaux des repères","Annonce à moins de 55 m pendant le guidage.","voice_markers",true,()->{}));
        p.addView(toggleRow("Mode économie d’énergie","GPS adaptatif à l’arrêt, carte moins souvent redessinée et animations réduites en tournée.","battery_saver",true,this::applyRuntimePrefs));
        p.addView(toggleRow("Garder l’écran allumé","Désactivé par le mode économie d’énergie. Augmente fortement la consommation.","keep_screen",false,this::applyRuntimePrefs));
        TextView advanced=pill("Avancé  ⌄",SURFACE2);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(52));ap.topMargin=dp(16);p.addView(advanced,ap);
        LinearLayout extra=new LinearLayout(this);extra.setOrientation(LinearLayout.VERTICAL);extra.setVisibility(View.GONE);
        extra.addView(toggleRow("Animations","Transitions d’onglets, apparition des cartes et micro-feedback.","animations",true,()->{}));
        extra.addView(choiceRow("Vitesse des animations","anim_speed",new int[]{120,180,220,320,450},new String[]{"Très rapide","Rapide","Équilibrée","Douce","Cinématique"},220,()->{}));
        extra.addView(toggleRow("Animations riches","Échelle, rebond et apparition progressive des cartes.","rich_animations",true,()->{}));
        extra.addView(choiceRow("Précision GPS maximale","record_accuracy",new int[]{20,30,45,60},new String[]{"20 m","30 m","45 m","60 m"},45,()->{}));
        extra.addView(toggleRow("Optimiser les imports GPX","Recalage routier : peut modifier la trace d’origine.","gpx_match_roads",false,()->{}));
        menu(extra,getString(R.string.menu_background_gps),"",this::openBackgroundGpsSettings);
         menu(extra,getString(R.string.menu_battery),"",this::openBatterySettings);
         menu(extra,getString(R.string.menu_diagnostics),"",this::exportDiagnostics);p.addView(extra);advanced.setOnClickListener(v->extra.setVisibility(extra.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));sv.addView(p);return sv;}
    private void showMapStyleMenu(){new RoutixDialogs.Builder(this).setTitle("Fond de carte").setItems(new String[]{"Clair","Sombre"},(d,w)->{prefs.edit().putString("map_appearance",w==0?"light":"dark").putBoolean("auto_night",false).apply();applyMapStyle();}).show();}
    private void offlineMenu(){List<String> labels=new ArrayList<>();labels.add("Carte en ligne");for(FranceOfflineManager.Pack p:FranceOfflineManager.PACKS)labels.add(p.label+(FranceOfflineManager.isInstalled(this,p)?" • installé":" • télécharger"));new RoutixDialogs.Builder(this).setTitle("Cartes hors ligne").setItems(labels.toArray(new String[0]),(d,w)->{if(w==0){prefs.edit().remove("offline_pack").apply();applyMapStyle();return;}FranceOfflineManager.Pack pack=FranceOfflineManager.PACKS.get(w-1);if(FranceOfflineManager.isInstalled(this,pack)){prefs.edit().putString("offline_pack",pack.id).apply();applyMapStyle();toast("Carte hors ligne : "+pack.label);}else try{FranceOfflineManager.download(this,pack);toast("Téléchargement lancé. Sélectionne le pack une fois terminé.");}catch(RuntimeException e){DiagnosticLog.error("offline download",e);toast("Téléchargement indisponible");}}).show();}
    private void updateMenu(){
        String installed;try{installed=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){installed="?";}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(8),dp(18),dp(10));
        TextView version=text("Version installée : "+installed,13,Typeface.NORMAL,MUTED);box.addView(version);
        TextView state=text("Recherche de la dernière bêta…",14,Typeface.BOLD,TEXT);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(14);box.addView(state,sp);
        android.widget.ProgressBar progress=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgress(5);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(7));pp.topMargin=dp(10);box.addView(progress,pp);
        TextView beta=pill("Vérification…",SURFACE2);beta.setEnabled(false);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(54));bp.topMargin=dp(16);box.addView(beta,bp);
        TextView stable=pill("Voir les versions stables",SURFACE2);stable.setOnClickListener(v->openStableReleases());LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,dp(50));st.topMargin=dp(8);box.addView(stable,st);
        AlertDialog dialog=new RoutixDialogs.Builder(this).setTitle("Mises à jour Routix").setView(box).setNegativeButton("Fermer",null).create();dialog.show();RoutixDialogs.style(dialog,this);
        InAppUpdater updater=new InAppUpdater(this);updater.checkBeta(new InAppUpdater.Callback(){public void status(String s,int p){state.setText(s);progress.setProgress(p);}public void error(String m){progress.setVisibility(View.GONE);state.setText(m);beta.setText("Revérifier la bêta");beta.setEnabled(true);beta.setOnClickListener(v->{dialog.dismiss();updateMenu();});}public void ready(InAppUpdater.Update u){progress.setProgress(100);state.setText("Build #"+u.runNumber+" disponible · "+u.version());beta.setText("Mettre à jour vers la build #"+u.runNumber);beta.setEnabled(true);beta.setBackground(surface(accent,99));beta.setOnClickListener(v->{dialog.dismiss();downloadBeta(updater,u);});}});
    }
    private void openStableReleases(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/1337Detailing/Routix/releases/latest")));}catch(Exception e){toast("Page des versions indisponible");}}
    private void checkBetaUpdate(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(14),dp(18),dp(14));TextView state=text("Connexion à GitHub Actions…",14,Typeface.BOLD,TEXT);android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(5);box.addView(state);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(8));bp.topMargin=dp(14);box.addView(bar,bp);AlertDialog wait=new RoutixDialogs.Builder(this).setTitle("Canal bêta").setView(box).setNegativeButton("Fermer",null).create();wait.show();RoutixDialogs.style(wait,this);
        InAppUpdater updater=new InAppUpdater(this);updater.checkBeta(new InAppUpdater.Callback(){public void status(String s,int p){state.setText(s);bar.setProgress(p);}public void error(String m){if(wait.isShowing())wait.dismiss();new RoutixDialogs.Builder(RoutixActivity.this).setTitle("Mise à jour bêta").setMessage(m).setPositiveButton("OK",null).show();}public void ready(InAppUpdater.Update u){if(wait.isShowing())wait.dismiss();new RoutixDialogs.Builder(RoutixActivity.this).setTitle("Bêta "+u.version()).setMessage("Build #"+u.runNumber+" réussie sur master.\nCommit "+(u.sha.length()>8?u.sha.substring(0,8):u.sha)+"\n\nL’APK sera téléchargé depuis l’artefact officiel GitHub Actions puis Android demandera confirmation pour l’installation.").setNegativeButton("Annuler",null).setPositiveButton("Télécharger et installer",(d,w)->downloadBeta(updater,u)).show();}});
    }
    private void downloadBeta(InAppUpdater updater,InAppUpdater.Update u){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(14),dp(18),dp(14));TextView state=text("Téléchargement…",14,Typeface.BOLD,TEXT);android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);box.addView(state);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(8));bp.topMargin=dp(14);box.addView(bar,bp);AlertDialog wait=new RoutixDialogs.Builder(this).setTitle("Installation bêta").setView(box).setNegativeButton("Masquer",null).create();wait.show();RoutixDialogs.style(wait,this);updater.downloadAndInstall(u,new InAppUpdater.Callback(){public void status(String s,int p){state.setText(s);bar.setProgress(p);}public void ready(InAppUpdater.Update ignored){}public void error(String m){if(wait.isShowing())wait.dismiss();new RoutixDialogs.Builder(RoutixActivity.this).setTitle("Téléchargement impossible").setMessage(m).setPositiveButton("OK",null).show();}});
    }

    private void openBackgroundGpsSettings(){
        new RoutixDialogs.Builder(this).setTitle(R.string.menu_background_gps)
            .setMessage(R.string.background_gps_explanation)
            .setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.permission_settings,(d,w)->{
                Intent i=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+getPackageName()));
                startActivity(i);
            }).show();
    }
    private void openBatterySettings(){
        new RoutixDialogs.Builder(this).setTitle(R.string.menu_battery)
            .setMessage(R.string.battery_explanation)
            .setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.permission_settings,(d,w)->
                startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))
            .show();
    }
    private void exportDiagnostics(){try{File f=DiagnosticLog.export(this);Intent i=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,FileProvider.getUriForFile(this,getPackageName()+".files",f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Rapport Routix"));}catch(Exception e){DiagnosticLog.error("export diagnostic",e);toast("Export impossible");}}

    private View colorSettingRow(String title,String key,int def,Runnable changed){
        int value=prefs.getInt(key,def);LinearLayout row=settingsRow(title,String.format(Locale.US,"#%06X",0xFFFFFF&value));TextView swatch=new TextView(this);swatch.setBackground(surface(value,99));row.addView(swatch,0,new LinearLayout.LayoutParams(dp(28),dp(28)));row.setOnClickListener(v->{press(v);showColorPicker(title,key,value,changed);});return row;
    }
    private void showColorPicker(String title,String key,int current,Runnable changed){
        int[] palette={MAUVE,BLUE,GREEN,PEACH,TEAL,PINK,YELLOW,RED,theme.rosewater,theme.flamingo,theme.sky,theme.lavender};
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(14),dp(18),dp(8));android.widget.GridLayout chips=new android.widget.GridLayout(this);chips.setColumnCount(4);
        EditText hex=new EditText(this);hex.setSingleLine(true);hex.setText(String.format(Locale.US,"#%06X",0xFFFFFF&current));hex.setTextColor(TEXT);hex.setHintTextColor(MUTED);hex.setInputType(InputType.TYPE_CLASS_TEXT);hex.setBackground(surface(SURFACE2,16));hex.setPadding(dp(12),0,dp(12),0);
        for(int color:palette){TextView chip=new TextView(this);chip.setBackground(surface(color,99));android.widget.GridLayout.LayoutParams cp=new android.widget.GridLayout.LayoutParams();cp.width=dp(52);cp.height=dp(48);cp.setMargins(dp(4),dp(4),dp(4),dp(4));chip.setContentDescription(String.format(Locale.US,"Couleur #%06X",color&0xffffff));chips.addView(chip,cp);chip.setOnClickListener(v->hex.setText(String.format(Locale.US,"#%06X",0xFFFFFF&color)));}
        panel.addView(chips,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(50));hp.topMargin=dp(12);panel.addView(hex,hp);
        new RoutixDialogs.Builder(this).setTitle(title).setMessage("Choisis un preset ou saisis librement une couleur hexadécimale (#RRGGBB).").setView(panel).setNegativeButton("Annuler",null).setPositiveButton("Appliquer",(d,w)->{try{String raw=hex.getText().toString().trim();if(!raw.startsWith("#"))raw="#"+raw;int color=Color.parseColor(raw);prefs.edit().putInt(key,color).apply();if(changed!=null)changed.run();showTab("settings",false);}catch(Exception e){toast("Couleur invalide");}}).show();
    }
    private void animateCard(View v,int index){if(v==null||!RoutixTheme.motion(prefs))return;v.setAlpha(0);v.setTranslationY(dp(16));v.setScaleX(.985f);v.setScaleY(.985f);v.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(animDuration()).setStartDelay(Math.min(260,index*35L)).start();}
    private void successPulse(View v){if(v==null||!RoutixTheme.motion(prefs))return;v.animate().scaleX(1.035f).scaleY(1.035f).setDuration(Math.max(80,animDuration()/3)).withEndAction(()->v.animate().scaleX(1).scaleY(1).setDuration(Math.max(100,animDuration()/2)).start()).start();}

    private View choiceRow(String title,String key,int[] values,String[] labels,int def,Runnable changed){int cur=prefs.getInt(key,def),idx=0;for(int i=0;i<values.length;i++)if(values[i]==cur){idx=i;break;}final int selected=idx;LinearLayout row=settingsRow(title,labels[selected]);row.setOnClickListener(v->new RoutixDialogs.Builder(this).setTitle(title).setSingleChoiceItems(labels,selected,(d,w)->{prefs.edit().putInt(key,values[w]).apply();d.dismiss();if(changed!=null)changed.run();showTab("settings",false);}).show());return row;}
    private View stringChoiceRow(String title,String key,String[] values,String[] labels,String def,Runnable changed){String cur=prefs.getString(key,def);int found=0;for(int i=0;i<values.length;i++)if(values[i].equals(cur)){found=i;break;}final int idx=found;LinearLayout row=settingsRow(title,labels[idx]);row.setOnClickListener(v->new RoutixDialogs.Builder(this).setTitle(title).setSingleChoiceItems(labels,idx,(d,w)->{prefs.edit().putString(key,values[w]).apply();d.dismiss();if(changed!=null)changed.run();showTab("settings",false);}).show());return row;}
    private LinearLayout settingsRow(String title,String value){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(12),dp(12),dp(12));row.setBackground(surface(SURFACE,20));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(text(title,14,Typeface.BOLD,TEXT));tx.addView(text(value,11,Typeface.NORMAL,MUTED));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("",18,Typeface.BOLD,MUTED);TerrainIcon chevron=new TerrainIcon("chevron",MUTED);chevron.setBounds(0,0,dp(18),dp(18));arrow.setCompoundDrawables(chevron,null,null,null);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(34)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);row.setLayoutParams(lp);return row;}
    private View toggleRow(String title,String sub,String key,boolean def,Runnable changed){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(12),dp(12),dp(12));row.setBackground(surface(SURFACE,20));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(text(title,14,Typeface.BOLD,TEXT));tx.addView(text(sub,11,Typeface.NORMAL,MUTED));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));TextView state=toggleCapsule(prefs.getBoolean(key,def));row.addView(state,new LinearLayout.LayoutParams(dp(54),dp(32)));row.setOnClickListener(v->{press(v);boolean on=!prefs.getBoolean(key,def);prefs.edit().putBoolean(key,on).apply();state.setText(on?"ON":"OFF");state.setTextColor(on?theme.onAccent():MUTED);state.setBackground(surface(on?accent:SURFACE2,16));if(changed!=null)changed.run();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);row.setLayoutParams(lp);return row;}
    private TextView toggleCapsule(boolean on){TextView t=text(on?"ON":"OFF",10,Typeface.BOLD,on?theme.onAccent():MUTED);t.setGravity(Gravity.CENTER);t.setBackground(surface(on?accent:SURFACE2,16));return t;}

    private void startRecording(){if(tracker==null||!tracker.beginRecording()){ensureLocation();toast("Attends le GPS et autorise la localisation précise");return;}successPulse(recordMain);toast("Enregistrement démarré");showTab("record",true);}
    private void finishRecording(){if(tracker==null||tracker.points.size()<2){toast("Pas assez de points GPS");return;}toast("Sauvegarde de la tournée…");tracker.finish(f->{if(isDestroyed())return;toast(f==null?"Échec : brouillon conservé, réessaie":"Tournée sauvegardée");showTab(f==null?"record":"routes",true);});}
    private void togglePause(){if(tracker!=null)tracker.togglePause();showTab("record",false);}
    private void addEvent(String type,String label){if(tracker==null||!recording||paused||lastLocation==null){toast("Enregistrement GPS requis");return;}tracker.addEvent(type,label);successPulse("REVERSE".equals(type)?reverseButton:twoSidesButton);toast(label+" ajouté");}
    private void addGuidanceFlag(String type,String label){if(tracker==null||!guiding||lastLocation==null){toast("GPS ou tournée indisponible");return;}tracker.addGuidanceEvent(type,label);toast("Repère ajouté");}
    private void setFlagEnabled(boolean on){for(TextView t:new TextView[]{reverseButton,twoSidesButton,pauseButton})if(t!=null){t.setEnabled(on);t.setAlpha(on?1:.38f);}}
    private void offerDraftRecovery(){RouteStore.Summary draft=store.draftSummary();new RoutixDialogs.Builder(this).setTitle("Tournée interrompue").setMessage(draft.points.size()+" points récupérables.").setNegativeButton("Plus tard",null).setPositiveButton("Récupérer",(d,w)->{if(tracker!=null)tracker.recoverDraft(draft);showTab("record",true);}).show();}

    private View planLabPage(){ScrollView sv=new ScrollView(this);LinearLayout p=page(getString(R.string.screen_plan),"");TextView pick=pill("Choisir une photo de plan",accent);pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT_PLAN_IMAGE);});p.addView(pick,new LinearLayout.LayoutParams(-1,dp(52)));if(planPreview==null){TextView info=infoCard("Prêt à tester","Importe une photo ou une capture d’un plan avec les portions de collecte colorées. Routix isolera les traits détectés.");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);p.addView(info,lp);}else{ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageBitmap(planAnalysis==null?planPreview:PlanImageAnalyzer.overlay(planPreview,planAnalysis));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=dp(12);p.addView(image,ip);boolean ok=planAnalysis!=null&&planAnalysis.usable();String status=planAnalysis==null?"Analyse impossible":ok?"Traits colorés détectés":"Pas assez de traits détectés";String detail=planAnalysis==null?"—":planAnalysis.stroke.size()+" échantillons • couverture "+String.format(Locale.FRANCE,"%.2f",planAnalysis.coverage*100)+" %";TextView result=infoCard(status,detail);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);p.addView(result,rp);TextView note=text(ok?"Étape image validée. Le recalage sur les vraies rues sera ajusté avec tes plans réels.":"Essaie une photo plus droite, nette et bien éclairée.",12,Typeface.NORMAL,ok?GREEN:PEACH);note.setPadding(0,dp(12),0,0);p.addView(note);}sv.addView(p);return sv;}
    private void importPlanImage(android.net.Uri uri){toast("Analyse du plan…");new Thread(()->{Bitmap bitmap=null;PlanImageAnalyzer.Result result=null;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){bitmap=android.graphics.BitmapFactory.decodeStream(in);if(bitmap!=null){int max=1800;float scale=Math.min(1f,max/(float)Math.max(bitmap.getWidth(),bitmap.getHeight()));if(scale<1)bitmap=Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(bitmap.getWidth()*scale)),Math.max(1,(int)(bitmap.getHeight()*scale)),true);result=PlanImageAnalyzer.analyze(bitmap);}}catch(Exception e){DiagnosticLog.error("plan image import",e);}final Bitmap b=bitmap;final PlanImageAnalyzer.Result r=result;runOnUiThread(()->{planPreview=b;planAnalysis=r;toast(b==null?"Image illisible":r!=null&&r.usable()?"Plan analysé":"Analyse terminée • détection faible");showTab("planlab",false);});},"plan-image").start();}
    private void importGpx(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT_GPX);}
    @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;if(r==IMPORT_PLAN_IMAGE){importPlanImage(data.getData());return;}if(r==IMPORT_GPX){Uri u=data.getData();toast("Analyse du GPX…");new Thread(()->{File f=store.importGpx(u);runOnUiThread(()->{toast(f==null?"Import impossible":"GPX importé");if(f!=null)prefs.edit().putString("gpx_lab_file",f.getName()).apply();if(!isDestroyed())showTab("routes",false);});},"routix-import").start();}}

    private void startGuidance(RouteStore.Summary selected){
        if(selected==null||selected.file==null||tracker==null||!tracker.ready){toast("Trace ou GPS indisponible");return;}
        RouteStore.Summary fresh=store.parse(selected.file);
        if(fresh.points.size()<2){toast("Cette tournée ne contient pas de tracé valide");return;}
        clearApproach();finishSummaryShown=false;nextActionDistance=Float.MAX_VALUE;
        tracker.beginGuidance(fresh);guidingCameraStart();showTab("map",true);drawRemaining(tracker.guidanceState);recenter();
        if(prefs.getBoolean("guide_to_start",true))requestApproachIfNeeded(fresh,tracker.location!=null?tracker.location:lastLocation);
    }
    private void guidingCameraStart(){guidanceCameraFollow=true;headingUp=true;if(modern!=null)modern.heading(true);}

    private void requestApproachIfNeeded(RouteStore.Summary selected,Location origin){
        if(selected==null||selected.points.isEmpty())return;approachRouteName=selected.file.getName();
        if(!DepartureNavigation.isFreshFix(origin)){approachPending=true;DiagnosticLog.info("approach waiting for fresh GPS: "+approachRouteName);if(guideCue!=null)guideCue.setText("GPS en cours d’acquisition…");return;}
        RouteStore.Point first=selected.points.get(0);float d=DepartureNavigation.distanceTo(origin,first);
        if(d<=85){approachPending=false;return;}
        if(!Float.isFinite(d)||d>DepartureNavigation.MAX_APPROACH_DISTANCE_M){approachPending=false;DiagnosticLog.info("approach rejected implausible distance="+d+" route="+approachRouteName);toast("Départ à "+formatDistance(d)+" : guidage d’approche ignoré, vérifie la tournée");return;}
        approachPending=false;approachingStart=true;final long token=++approachGeneration;final String routeName=selected.file.getName();Location frozen=new Location(origin);
        if(guideCue!=null)guideCue.setText("Calcul de l’itinéraire vers le départ…");
        DepartureNavigation.calculate(frozen,first,result->runOnUiThread(()->{
            if(isDestroyed()||token!=approachGeneration||!guiding||tracker==null||tracker.route==null)return;
            RouteStore.Summary current=tracker.route;if(current.file==null||!routeName.equals(current.file.getName())||current.points.isEmpty())return;
            RouteStore.Point currentFirst=current.points.get(0);
            if(result==null||!result.usable()||!result.matchesTarget(currentFirst)){DiagnosticLog.info("approach callback rejected: route/target changed");approachingStart=false;clearApproach();return;}
            approachRoute=result;approachIndex=0;updateApproach(lastLocation!=null?lastLocation:frozen);toast(result.roadRouted?"Trajet vers le départ prêt":"Réseau indisponible : direction directe");
        }));
    }
    private void maybeStartPendingApproach(Location l){
        if(!approachPending||!DepartureNavigation.isFreshFix(l)||tracker==null||tracker.route==null||tracker.route.file==null)return;
        if(approachRouteName!=null&&!approachRouteName.equals(tracker.route.file.getName())){DiagnosticLog.info("discarded pending approach for stale route");clearApproach();return;}
        requestApproachIfNeeded(tracker.route,l);
    }

    private void updateRouteGuidance(Location l){if(!guiding||tracker==null)return;GuidanceEngine.State state=tracker.guidanceState;if(state==null){drawRemaining(null);return;}
        maybeStartPendingApproach(l);
        if(approachingStart&&l!=null&&guidingRoute!=null&&!guidingRoute.points.isEmpty()){RouteStore.Point first=guidingRoute.points.get(0);float[] toStart=new float[1];Location.distanceBetween(l.getLatitude(),l.getLongitude(),first.lat,first.lon,toStart);
            if(toStart[0]<=45){approachingStart=false;clearApproach();toast("Départ atteint • guidage de tournée");}
            else updateApproach(l);
        }
        drawRemaining(state);
        GuidanceEngine.Maneuver maneuver=guidance==null?null:guidance.nextManeuver(state);
        if(approachingStart){
            nextActionDistance=(float)distanceToApproachEnd(l);if(guideDistance!=null)guideDistance.setText(formatDistance(nextActionDistance)+"\nDÉPART");if(guideProgress!=null)guideProgress.setText("→\nAPPROCHE");
            if(guideCue!=null)guideCue.setText("↑  Rejoignez le départ\n"+formatDistance(nextActionDistance));
        }else{
            if(guideDistance!=null)guideDistance.setText(formatDistance(state.remainingM)+"\nRESTANT");int coverage=tracker.guidanceCoverage==null?state.progressPercent:tracker.guidanceCoverage.percent();int missed=tracker.guidanceCoverage==null?0:tracker.guidanceCoverage.missedCountBefore(state.nearestIndex);if(guideProgress!=null)guideProgress.setText(coverage+" %\nCOUVERT"+(missed>0?" • "+missed+" À REVOIR":""));
            nextActionDistance=maneuver==null?Float.MAX_VALUE:maneuver.distanceM;
            if(guideCue!=null){
                if(state.poorAccuracy)guideCue.setText("GPS imprécis • progression conservée");
                else if(state.rerouted)guideCue.setText("✓  Itinéraire adapté • continuez");
                else if(state.offRoute)guideCue.setText("Recherche d’un passage cohérent…");
                else if(missed>0&&missed<=3)guideCue.setText("⚠  "+missed+" portion"+(missed>1?"s":"")+" précédente"+(missed>1?"s":"")+" à revoir");
                else if(state.finished)guideCue.setText("Tournée terminée");
                else if(state.nextEvent!=null&&state.distanceToNextEventM<nextActionDistance)guideCue.setText("⚑  "+state.nextEvent.label+"\nDans "+formatDistance(state.distanceToNextEventM));
                else if(maneuver!=null)guideCue.setText(maneuverGlyph(maneuver)+"  "+maneuver.instruction+"\nDans "+formatDistance(maneuver.distanceM));
            }
        }
        followCamera(l,nextActionDistance);
        if(state.finished&&!finishSummaryShown&&!approachingStart){finishSummaryShown=true;showFinishSummary();}
    }
    private String maneuverGlyph(GuidanceEngine.Maneuver m){if(m==null)return "↑";if(m.direction<0)return "↰";if(m.direction==1)return "↱";if(m.direction==2)return "↶";return "↑";}

    private final List<Polyline> extraRecordingLines=new ArrayList<>();
    private void drawRecordingTrace(){
        if(recordingLine==null)return;
        if(!recording||tracker==null||tracker.points.size()<2){
            recordingLine.setPoints(Collections.emptyList());
            for(Polyline line:extraRecordingLines)map.getOverlays().remove(line);
            extraRecordingLines.clear();
            if(modern!=null)modern.setRecordingSegments(Collections.emptyList());
            map.invalidate();return;
        }
        List<List<GeoPoint>> segments=new ArrayList<>();
        List<GeoPoint> active=new ArrayList<>();
        segments.add(active);
        List<RouteStore.Point> source=tracker.points;
        int stride=Math.max(1,(int)Math.ceil(source.size()/(double)MAX_DISPLAY_ROUTE_POINTS));
        for(int i=0;i<source.size();i++){
            RouteStore.Point p=source.get(i);
            if(p.breakBefore&&!active.isEmpty()){active=new ArrayList<>();segments.add(active);}
            if(i%stride==0||p.breakBefore||i==source.size()-1)
                active.add(new GeoPoint(p.lat,p.lon));
        }
        while(extraRecordingLines.size()<segments.size()-1){
            Polyline line=new Polyline();
            line.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
            line.getOutlinePaint().setStrokeJoin(Paint.Join.ROUND);
            extraRecordingLines.add(line);map.getOverlays().add(line);
        }
        while(extraRecordingLines.size()>segments.size()-1)
            map.getOverlays().remove(extraRecordingLines.remove(extraRecordingLines.size()-1));
        int color=recordingColor(), width=dp(Math.max(5,prefs.getInt("route_width",10)-2));
        for(int i=0;i<segments.size();i++){
            Polyline line=i==0?recordingLine:extraRecordingLines.get(i-1);
            line.setPoints(offline?segments.get(i):Collections.emptyList());
            line.getOutlinePaint().setColor(color);line.getOutlinePaint().setAlpha(180);
            line.getOutlinePaint().setStrokeWidth(width);
        }
        if(modern!=null)modern.setRecordingSegments(segments);
        map.invalidate();
    }

    private void drawRemaining(GuidanceEngine.State state){if(!guiding||guidingRoute==null||!"map".equals(selectedTab))return;boolean erase=prefs.getBoolean("erase_passed",true);int start=!erase||state==null?0:Math.max(0,Math.min(guidingRoute.points.size()-2,state.nearestIndex));int from=start+(erase&&state!=null?1:0);List<GeoPoint> pts=new ArrayList<>();if(state!=null&&erase&&Double.isFinite(state.position.lat)&&Double.isFinite(state.position.lon))pts.add(new GeoPoint(state.position.lat,state.position.lon));int available=Math.max(0,guidingRoute.points.size()-from),slots=Math.max(2,MAX_DISPLAY_ROUTE_POINTS-pts.size()),stride=Math.max(1,(int)Math.ceil(available/(double)slots));for(int i=from;i<guidingRoute.points.size();i+=stride){RouteStore.Point p=guidingRoute.points.get(i);pts.add(new GeoPoint(p.lat,p.lon));}if(from<guidingRoute.points.size()){RouteStore.Point last=guidingRoute.points.get(guidingRoute.points.size()-1);if(pts.isEmpty()||pts.get(pts.size()-1).getLatitude()!=last.lat||pts.get(pts.size()-1).getLongitude()!=last.lon)pts.add(new GeoPoint(last.lat,last.lon));}if(modern!=null){modern.setRoute(pts);modern.setEvents(guidingRoute.events);}remainingLine.setPoints(offline?pts:Collections.emptyList());remainingLine.getOutlinePaint().setColor(guidanceColor());remainingLine.getOutlinePaint().setStrokeWidth(dp(prefs.getInt("route_width",10)));String traceStyle=prefs.getString("guidance_trace_style","arrows");remainingLine.getOutlinePaint().setPathEffect("dashed".equals(traceStyle)?new android.graphics.DashPathEffect(new float[]{dp(14),dp(10)},0):null);if(offline&&"arrows".equals(traceStyle))routeArrows.setPoints(pts);else routeArrows.setPoints(new ArrayList<>());List<GeoPoint> ahead=firstMeters(pts,50);aheadLine.setPoints(offline?ahead:Collections.emptyList());aheadLine.getOutlinePaint().setColor(aheadColor());aheadLine.getOutlinePaint().setStrokeWidth(dp(prefs.getInt("route_width",10)+2));aheadLine.getOutlinePaint().setPathEffect(null);if(modern!=null)modern.setAheadRoute(ahead);map.invalidate();}
    private void updateApproach(Location l){if(!approachingStart||approachRoute==null||!approachRoute.usable())return;approachIndex=DepartureNavigation.advanceIndex(approachRoute.points,approachIndex,l);List<GeoPoint> pts=DepartureNavigation.remaining(approachRoute,approachIndex,l);
        if(modern!=null)modern.setApproach(pts);approachLine.setPoints(offline?pts:Collections.emptyList());approachLine.getOutlinePaint().setColor(TEAL);approachLine.getOutlinePaint().setStrokeWidth(dp(8));if(offline)approachArrows.setPoints(pts);else approachArrows.setPoints(Collections.emptyList());map.invalidate();}
    private double distanceToApproachEnd(Location l){if(l==null||guidingRoute==null||guidingRoute.points.isEmpty())return 0;RouteStore.Point first=guidingRoute.points.get(0);float[] d=new float[1];Location.distanceBetween(l.getLatitude(),l.getLongitude(),first.lat,first.lon,d);return d[0];}
    private void clearApproach(){approachGeneration++;approachRoute=null;approachIndex=0;approachingStart=false;approachPending=false;approachRouteName=null;approachLine.setPoints(Collections.emptyList());approachArrows.setPoints(Collections.emptyList());if(modern!=null)modern.setApproach(Collections.emptyList());map.invalidate();}
    private List<GeoPoint> firstMeters(List<GeoPoint> input,float meters){List<GeoPoint> out=new ArrayList<>();if(input==null||input.isEmpty())return out;out.add(input.get(0));float used=0;for(int i=1;i<input.size()&&used<meters;i++){GeoPoint a=input.get(i-1),b=input.get(i);float[] d=new float[1];Location.distanceBetween(a.getLatitude(),a.getLongitude(),b.getLatitude(),b.getLongitude(),d);if(used+d[0]<=meters){out.add(b);used+=d[0];}else{double t=d[0]<=0?0:(meters-used)/d[0];out.add(new GeoPoint(a.getLatitude()+(b.getLatitude()-a.getLatitude())*t,a.getLongitude()+(b.getLongitude()-a.getLongitude())*t));break;}}return out;}
    private void redrawGuidance(){if(tracker!=null)drawRemaining(tracker.guidanceState);}
    private void showFinishSummary(){
        if(tracker==null||guidingRoute==null||isFinishing())return;
        long end=System.currentTimeMillis(),start=tracker.guidanceStartedAt>0?tracker.guidanceStartedAt:end;long duration=Math.max(0,end-start);
        double travelled=tracker.guidanceTravelDistance;int reverse=0,two=0;for(RouteStore.Event e:guidingRoute.events){if("REVERSE".equals(e.type))reverse++;else if("TWO_SIDES".equals(e.type))two++;}new SessionStatsStore(prefs).add(new SessionStatsStore.Entry(start,end,duration,travelled,0,tracker.guidanceReverseAdded,tracker.guidanceTwoSidesAdded,store.displayName(guidingRoute.file)));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(20),dp(20),dp(20),dp(12));panel.setBackground(surface(SURFACE,28));
        panel.addView(text("Tournée terminée",27,Typeface.BOLD,TEXT));TextView sub=text(formatClock(start)+" → "+formatClock(end),12,Typeface.NORMAL,MUTED);sub.setPadding(0,dp(4),0,dp(14));panel.addView(sub);
        RoutePreviewView preview=new RoutePreviewView(this);preview.setPoints(guidingRoute.points);panel.addView(preview,new LinearLayout.LayoutParams(-1,dp(135)));
        LinearLayout stats=new LinearLayout(this);stats.setPadding(0,dp(12),0,dp(8));stats.addView(summaryStat("DURÉE",formatDuration(duration)),new LinearLayout.LayoutParams(0,-2,1));stats.addView(summaryStat("PARCOURUE",formatDistance(travelled)),new LinearLayout.LayoutParams(0,-2,1));stats.addView(summaryStat("REPÈRES",String.valueOf(reverse+two)),new LinearLayout.LayoutParams(0,-2,1));panel.addView(stats);
        panel.addView(infoCard("Repères ajoutés","Marche arrière : "+reverse+"   •   2 côtés : "+two));
        AlertDialog dialog=new RoutixDialogs.Builder(this).setView(scrollPanel(panel)).setCancelable(false)
                .setNegativeButton("Voir le détail",(d,w)->PrintablePlan.open(this,guidingRoute,store.displayName(guidingRoute.file)))
                .setNeutralButton("Partager GPX",(d,w)->share(guidingRoute.file))
                .setPositiveButton("Enregistrer et quitter",(d,w)->{stopGuidance();showTab("routes",true);}).create();
        dialog.show();RoutixDialogs.style(dialog,this);successPulse(panel);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(surface(BG,22));w.setDimAmount(.62f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
    }
    private static String formatClock(long ms){return new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(ms));}

    private void resumeGuidanceHere(){if(tracker==null||!tracker.reposition()){toast("Rapproche-toi du tracé avec un GPS précis");return;}guidanceCameraFollow=true;recenter();toast("Progression reprise au point le plus proche");}
    private void stopGuidance(){clearApproach();finishSummaryShown=false;if(tracker!=null)tracker.stopGuidance();remainingLine.setPoints(new ArrayList<>());aheadLine.setPoints(new ArrayList<>());routeArrows.setPoints(new ArrayList<>());if(modern!=null){modern.setRoute(new ArrayList<>());modern.setAheadRoute(new ArrayList<>());modern.setEvents(Collections.emptyList());}showTab("map",true);}
    private void followCamera(Location l,float actionDistance){if(l==null)return;if(modern!=null)modern.update(l,guiding,actionDistance);if(offline&&guidanceCameraFollow){long now=android.os.SystemClock.elapsedRealtime();boolean moving=l.hasSpeed()&&l.getSpeed()>.8f;long throttle=prefs.getBoolean("battery_saver",true)?(moving?900:3500):500;if(now-lastOfflineCameraMs>=throttle){lastOfflineCameraMs=now;map.getController().animateTo(new GeoPoint(l.getLatitude(),l.getLongitude()));double z=actionDistance<55?19:actionDistance<150?18.5:actionDistance<350?18:17.4;map.getController().setZoom(z);if(headingUp&&l.hasBearing()&&moving)map.setMapOrientation((float)MapStyles.smoothBearing(map.getMapOrientation(),360-l.getBearing()));}}}
    public void onLocationChanged(@NonNull Location l){lastLocation=l;if(headerSpeed!=null)headerSpeed.update(l);RoadRestrictionProvider.Snapshot rs=roadRestrictions.snapshot(l.getLatitude(),l.getLongitude());if(speedLimit!=null){speedLimit.setText(rs.speed);speedLimit.setContentDescription("Limitation "+("—".equals(rs.speed)?"inconnue":rs.speed+" kilomètres heure"));}if(modern!=null)modern.setBridges(rs.bridges);if(guiding)updateRouteGuidance(l);else if(modern!=null)modern.update(l,false);if(offline){if(offlinePosition==null){offlinePosition=new org.osmdroid.views.overlay.Marker(map);offlinePosition.setIcon(new android.graphics.drawable.BitmapDrawable(getResources(),createPositionIcon(prefs.getString("position_icon","Camion"))));offlinePosition.setAnchor(.5f,.5f);map.getOverlays().add(offlinePosition);}offlinePosition.setPosition(new GeoPoint(l.getLatitude(),l.getLongitude()));map.invalidate();}}
    private org.osmdroid.views.overlay.Marker offlinePosition;
    private void ensureLocation(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION);else{ensureNotificationPermission();if(tracker!=null&&visible)tracker.observe(this::syncTracking);}}
    private void ensureNotificationPermission(){if(android.os.Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED||prefs.getBoolean("notification_permission_prompted",false))return;prefs.edit().putBoolean("notification_permission_prompted",true).apply();ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION);}
    @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(r,p,g);if(r==LOCATION_PERMISSION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)ensureLocation();}
    private void recenter(){guidanceCameraFollow=true;if(lastLocation==null){toast("GPS en attente");return;}if(modern!=null)modern.recenter(lastLocation,guiding);map.getController().animateTo(new GeoPoint(lastLocation.getLatitude(),lastLocation.getLongitude()));map.getController().setZoom(18.0);}
    private final android.content.ServiceConnection connection=new android.content.ServiceConnection(){
        public void onServiceConnected(android.content.ComponentName name,android.os.IBinder binder){tracker=((TrackingService.LocalBinder)binder).service();if(visible)tracker.observe(RoutixActivity.this::syncTracking);}
        public void onServiceDisconnected(android.content.ComponentName name){tracker=null;DiagnosticLog.info("tracking service disconnected");}
    };
    private void syncTracking(){if(tracker==null||!tracker.ready||isDestroyed())return;boolean changed=recording!=tracker.recording||paused!=tracker.paused||guiding!=(tracker.guidance!=null);recording=tracker.recording;paused=tracker.paused;recordingStarted=tracker.started;recordedDistance=tracker.distance;guiding=tracker.guidance!=null;guidingRoute=tracker.route;guidance=tracker.guidance;
        // Copy only the counters into the existing recording UI; service owns the point lists.
        if(recordDistance!=null)recordDistance.setText(formatDistance(recordedDistance)+"\nDISTANCE");if(recordPoints!=null)recordPoints.setText(tracker.points.size()+"\nPOINTS");if(recordEvents!=null)recordEvents.setText(tracker.events.size()+"\nREPÈRES");
        if(changed)showTab(selectedTab,false);if(recordMain!=null)recordMain.setEnabled(!tracker.saving);drawRecordingTrace();if(tracker.location!=null)onLocationChanged(tracker.location);else if(guiding)drawRemaining(tracker.guidanceState);applyRuntimePrefs();
        if(!recoveryOffered){recoveryOffered=true;if(!recording&&store.hasDraft())offerDraftRecovery();}
    }

    private ScrollView scrollPanel(View panel){ScrollView scroll=new ScrollView(this);scroll.addView(panel);return scroll;}
    private LinearLayout page(String title,String sub){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);int pad=20;p.setPadding(dp(pad),dp(prefs.getBoolean("compact_ui",false)?12:18),dp(pad),dp(28));p.addView(text(title,30,Typeface.BOLD,TEXT));TextView s=text(sub,12,Typeface.NORMAL,MUTED);s.setPadding(0,dp(4),0,dp(14));p.addView(s);return p;}
    private TextView section(String s){TextView t=text(s.toUpperCase(Locale.ROOT),10,Typeface.BOLD,accent);t.setPadding(0,dp(20),0,dp(8));return t;}
    private TextView text(String s,float size,int style,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(Math.max(12,size));t.setTextColor(theme.readable(color));t.setTypeface(Typeface.create("sans-serif",style));t.setIncludeFontPadding(false);t.setLineSpacing(dp(2),1);return t;}
    private TextView pill(String s,int color){TextView t=text(s,13,Typeface.BOLD,(color==accent||color==GREEN||color==PEACH||color==RED)?RoutixTheme.ink(color):TEXT);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(52));t.setPadding(dp(8),dp(8),dp(8),dp(8));t.setMaxLines(2);androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(t,11,15,1,android.util.TypedValue.COMPLEX_UNIT_SP);t.setBackground(surface(color,12));return t;}
    private TextView compactMetric(String value,String label){TextView t=text(value+"  "+label,12,Typeface.BOLD,TEXT);t.setGravity(Gravity.CENTER);t.setSingleLine(true);t.setBackground(surface(SURFACE2,14));return t;}
    private TextView metric(String value,String label){TextView t=text(value+"\n"+label,13,Typeface.BOLD,TEXT);t.setGravity(Gravity.CENTER);t.setBackground(surface(SURFACE,20));return t;}
    private TextView bigMetric(String value,String label,int color){TextView t=text(value+"\n"+label,18,Typeface.BOLD,TEXT);t.setGravity(Gravity.CENTER);t.setBackground(surface(theme.tint(color),24));return t;}
    private TextView icon(String s){TextView t=text("",19,Typeface.BOLD,TEXT);t.setGravity(Gravity.CENTER);t.setPadding(dp(13),dp(13),dp(13),dp(13));String kind="◎".equals(s)?"locate":("↑".equals(s)||"N".equals(s))?"north":"more";TerrainIcon glyph=new TerrainIcon(kind,TEXT);glyph.setBounds(0,0,dp(22),dp(22));t.setCompoundDrawables(glyph,null,null,null);t.setBackground(surface(SURFACE2,12));return t;}

    private TextView infoCard(String title,String body){TextView t=text(title+"\n"+body,12,Typeface.NORMAL,MUTED);t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setBackground(surface(SURFACE,20));return t;}
    private View stage(String number,String title,String sub,int color){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(12),dp(10),dp(12),dp(10));r.setBackground(surface(SURFACE,18));TextView n=text(number,12,Typeface.BOLD,color);n.setGravity(Gravity.CENTER);n.setBackground(surface(SURFACE2,14));r.addView(n,new LinearLayout.LayoutParams(dp(42),dp(42)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(11),0,0,0);tx.addView(text(title,13,Typeface.BOLD,TEXT));tx.addView(text(sub,11,Typeface.NORMAL,MUTED));r.addView(tx,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(7);r.setLayoutParams(lp);return r;}
    private View summaryStat(String label,String value){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.addView(text(value,13,Typeface.BOLD,TEXT));c.addView(text(label,9,Typeface.BOLD,MUTED));return c;}
    private GradientDrawable surface(int color,float radius){return RoutixTheme.surface(theme,color,radius>=90?99:radius<=16?12:22,getResources().getDisplayMetrics().density);}

    private void press(View v){if(v==null)return;if(prefs.getBoolean("haptics",true))v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);if(RoutixTheme.motion(prefs)&&!(guiding&&prefs.getBoolean("battery_saver",true)))v.animate().scaleX(.94f).scaleY(.94f).setDuration(70).withEndAction(()->v.animate().scaleX(1).scaleY(1).setDuration(150).start()).start();}
    private int animDuration(){return prefs.getInt("anim_speed",220);}
    private int routeColor(){return guidanceColor();}
    private int guidanceColor(){return prefs.getInt("guidance_color",prefs.getInt("route_color",accent));}
    private int recordingColor(){return prefs.getInt("recording_color",TEAL);}
    private int aheadColor(){return prefs.getInt("ahead_color",PEACH);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static String formatDistance(double m){return m<1000?Math.round(m)+" m":String.format(Locale.FRANCE,"%.1f km",m/1000d);}
    private static String formatMinutes(int m){return String.format(Locale.FRANCE,"%dh%02d",m/60,m%60);}
    private static String formatDuration(long ms){long min=Math.max(0,ms/60000);return String.format(Locale.FRANCE,"%dh%02d",min/60,min%60);}
    private static String formatDate(long ms){return new SimpleDateFormat("dd MMM yyyy • HH:mm",Locale.FRANCE).format(new Date(ms));}
    private static String timeLabel(int minutes){int m=((minutes%1440)+1440)%1440;return String.format(Locale.FRANCE,"%02d:%02d",m/60,m%60);}
    private static int parsePositive(String s){try{return Math.max(0,Integer.parseInt(s.trim()));}catch(Exception e){return 0;}}
    private static int parseClock(String raw){if(raw==null)return-1;String s=raw.trim().replace("h",":").replace("H",":");try{int h,m;if(s.contains(":")){String[] p=s.split(":");if(p.length<2)return-1;h=Integer.parseInt(p[0]);m=Integer.parseInt(p[1]);}else{String d=s.replaceAll("[^0-9]","");if(d.length()<3||d.length()>4)return-1;int v=Integer.parseInt(d);h=v/100;m=v%100;}return h>=0&&h<24&&m>=0&&m<60?h*60+m:-1;}catch(Exception e){return-1;}}
    private static int nowMinutes(){Calendar c=Calendar.getInstance();return c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);}
    private static String todayKey(){return new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).format(new Date());}
    private static String dayLabel(String date){try{return new SimpleDateFormat("EEEE d MMMM",Locale.getDefault()).format(new SimpleDateFormat("yyyy-MM-dd",Locale.FRANCE).parse(date));}catch(Exception e){return date;}}
    private static String monthLabel(String month){try{return new SimpleDateFormat("MMMM yyyy",Locale.getDefault()).format(new SimpleDateFormat("yyyy-MM",Locale.FRANCE).parse(month));}catch(Exception e){return month;}}

    private void rebuildChrome(){if(root==null)return;if(topBar!=null)root.removeView(topBar);if(dock!=null)root.removeView(dock);topBar=buildTopBar();dock=buildDock();root.addView(topBar);root.addView(dock);topBar.bringToFront();contentHost.bringToFront();dock.bringToFront();updateDock();positionChrome();applyRuntimePrefs();}
    private void applyRuntimePrefs(){boolean saver=prefs.getBoolean("battery_saver",true);if((recording||guiding)&&!saver&&prefs.getBoolean("keep_screen",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(mapLogo!=null)mapLogo.setVisibility("map".equals(selectedTab)&&false?View.VISIBLE:View.GONE);if(headerSpeed!=null)headerSpeed.setVisibility(prefs.getBoolean("show_speed",true)?View.VISIBLE:View.GONE);}
    private void positionChrome(){if(root==null||topBar==null||dock==null||contentHost==null)return;boolean compact=guiding&&"map".equals(selectedTab);int top=systemTop+dp(compact?4:8),bottom=systemBottom+dp(compact?4:8),topH=dp(compact?50:64),dockH=dp(compact?50:70);FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,topH,Gravity.TOP);hp.setMargins(dp(10),top,dp(10),0);topBar.setLayoutParams(hp);FrameLayout.LayoutParams dpv=new FrameLayout.LayoutParams(-1,dockH,Gravity.BOTTOM);dpv.setMargins(dp(10),0,dp(10),bottom);dock.setLayoutParams(dpv);if(modern!=null)modern.inset(top+topH);if(mapLogo!=null){FrameLayout.LayoutParams ml=new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.TOP|Gravity.START);ml.setMargins(dp(14),top+topH+dp(10),0,0);mapLogo.setLayoutParams(ml);}if("map".equals(selectedTab)){FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);cp.setMargins(dp(compact?6:10),0,dp(compact?6:10),bottom+dockH+dp(compact?5:8));contentHost.setLayoutParams(cp);}else{FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-1);cp.setMargins(0,top+topH+dp(8),0,bottom+dockH+dp(8));contentHost.setLayoutParams(cp);}}

    private void createModernMap(){if(modern!=null)return;modern=new ModernMapController(this,root,createPositionIcon(prefs.getString("position_icon","Camion")),mapState);modern.onStart();modern.setVisible(!offline&&"map".equals(selectedTab));positionChrome();}
    @Override protected void onStart(){super.onStart();if(modern==null)createModernMap();else modern.onStart();bound=bindService(new Intent(this,TrackingService.class),connection,BIND_AUTO_CREATE);}
    @Override protected void onResume(){super.onResume();visible=true;if(map!=null&&offline)map.onResume();if(modern!=null)modern.onResume();if(tracker!=null)tracker.observe(this::syncTracking);}
    @Override protected void onPause(){visible=false;if(tracker!=null)tracker.observe(null);if(modern!=null)modern.onPause();if(map!=null)map.onPause();super.onPause();}
    @Override protected void onStop(){visible=false;if(tracker!=null)tracker.observe(null);if(bound){unbindService(connection);bound=false;tracker=null;}if(modern!=null)modern.onStop();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("workspace",selectedTab);if(detailFile!=null)out.putString("detail_file",detailFile.getName());if(modern!=null)modern.onSaveInstanceState(out);}
    @Override public void onLowMemory(){super.onLowMemory();if(modern!=null)modern.onLowMemory();if(map!=null)map.getTileProvider().clearTileCache();}
    @Override protected void onDestroy(){guidanceSession++;if(modern!=null)modern.onDestroy();if(map!=null)map.onDetach();super.onDestroy();}
}
