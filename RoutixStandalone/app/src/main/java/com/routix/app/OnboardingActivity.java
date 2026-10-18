package com.routix.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/** Permission-only onboarding. Scrolls on small displays and at large font scales. */
public class OnboardingActivity extends AppCompatActivity {
    private static final int LOCATION_REQUEST=41;
    private SharedPreferences prefs;private RoutixTheme.Tokens theme;private TextView action,status;
    @Override protected void onCreate(Bundle state){
        prefs=getSharedPreferences("routix",MODE_PRIVATE);setTheme(RoutixTheme.activityStyle(prefs));super.onCreate(state);theme=RoutixTheme.from(prefs);
        if(prefs.getBoolean("onboarding_complete",false)&&hasLocation()){launchApp();return;}
        RoutixTheme.systemBars(this,theme);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(theme.base);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(24),dp(28),dp(24),dp(24));
        TextView brand=label("routix.",28,Typeface.BOLD,theme.text);page.addView(brand);
        TextView eyebrow=label("TOURNÉES DE COLLECTE",12,Typeface.BOLD,theme.secondaryContent);add(page,eyebrow,36);
        TextView title=label("Chaque rue.\nTon chemin.",36,Typeface.BOLD,theme.text);title.setLetterSpacing(-.035f);add(page,title,20);
        add(page,label("Autorise ta position précise pour enregistrer tes parcours et retrouver chaque rue.",16,Typeface.NORMAL,theme.subtext1),18);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(20),dp(20),dp(20),dp(20));card.setBackground(RoutixTheme.surface(theme,theme.mantle,22,getResources().getDisplayMetrics().density));
        card.addView(label("01  Localisation précise",16,Typeface.BOLD,theme.accent));add(card,label("Le GPS reste actif pendant une tournée, même écran éteint. Tes parcours restent sur cet appareil tant que tu ne les partages pas.",14,Typeface.NORMAL,theme.subtext1),12);
        status=label("",13,Typeface.NORMAL,theme.subtext1);add(card,status,16);add(page,card,28);
        page.addView(new View(this),new LinearLayout.LayoutParams(1,dp(24),1));
        action=label("Autoriser et continuer",16,Typeface.BOLD,theme.onAccent());action.setGravity(Gravity.CENTER);action.setPadding(dp(16),dp(16),dp(16),dp(16));action.setMinHeight(dp(58));action.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(theme.tint(theme.accent)),RoutixTheme.surface(theme,theme.accent,12,getResources().getDisplayMetrics().density),null));action.setOnClickListener(v->requestLocation());page.addView(action,new LinearLayout.LayoutParams(-1,-2));
        TextView foot=label("Enregistre.  Retrouve.  Repars.",12,Typeface.NORMAL,theme.muted());foot.setGravity(Gravity.CENTER);add(page,foot,20);scroll.addView(page);setContentView(scroll);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,in)->{androidx.core.graphics.Insets b=in.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()|androidx.core.view.WindowInsetsCompat.Type.displayCutout());v.setPadding(0,b.top,0,b.bottom);return in;});androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(),false);refresh();
    }
    private boolean hasLocation(){return ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private boolean settingsRequired(){return prefs.getBoolean("location_requested",false)&&!ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_FINE_LOCATION);}
    private void requestLocation(){
        if(hasLocation()){complete();return;}
        if(settingsRequired()){startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));return;}
        prefs.edit().putBoolean("location_requested",true).apply();ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==LOCATION_REQUEST){if(hasLocation())complete();else refresh();}}
    @Override protected void onResume(){super.onResume();if(action!=null){if(hasLocation())complete();else refresh();}}
    private void complete(){prefs.edit().putBoolean("onboarding_complete",true).putBoolean("privacy_notice_seen",true).putBoolean("location_usage_accepted",true).apply();launchApp();}
    private void refresh(){if(action==null)return;boolean settings=settingsRequired();status.setText(settings?"Active Position précise dans les autorisations Android pour continuer.":"Choisis « Lorsque vous utilisez l’application » et active Position précise.");action.setText(settings?"Ouvrir les autorisations":"Autoriser et continuer");}
    private void launchApp(){startActivity(new Intent(this,RoutixActivity.class));finish();if(RoutixTheme.motion(prefs))overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);parent.addView(child,lp);}
    private TextView label(String value,float size,int style,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTypeface(Typeface.create("sans-serif",style));t.setTextColor(theme.readable(color));t.setIncludeFontPadding(false);t.setLineSpacing(dp(3),1);return t;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
