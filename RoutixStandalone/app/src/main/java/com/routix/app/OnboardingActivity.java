package com.routix.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/** Single-step, system-language location permission screen. */
public final class OnboardingActivity extends AppCompatActivity {
    private static final int LOCATION_REQUEST=41;
    private SharedPreferences prefs;
    private RoutixTheme.Tokens theme;
    private TextView action,status;

    @Override protected void onCreate(Bundle savedState) {
        prefs=getSharedPreferences("routix",MODE_PRIVATE);
        setTheme(RoutixTheme.activityStyle(prefs));
        super.onCreate(savedState);
        theme=RoutixTheme.from(this);
        if(prefs.getBoolean("onboarding_complete",false)&&hasLocation()){launchApp();return;}
        RoutixTheme.systemBars(this,theme);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(theme.base);
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(dp(24),dp(32),dp(24),dp(32));

        ImageView brand=new ImageView(this);
        brand.setImageResource(R.drawable.ic_routix);
        brand.setContentDescription(getString(R.string.app_name));
        LinearLayout.LayoutParams logo=new LinearLayout.LayoutParams(dp(94),dp(94));
        logo.gravity=Gravity.CENTER_HORIZONTAL;
        page.addView(brand,logo);

        TextView name=label(getString(R.string.app_name),34,Typeface.BOLD,theme.text);
        name.setGravity(Gravity.CENTER);
        add(page,name,15);
        TextView tagline=label("GPS   •   TOURNÉES   •   GUIDAGE",11,Typeface.BOLD,theme.muted());
        tagline.setLetterSpacing(.09f);
        tagline.setGravity(Gravity.CENTER);
        add(page,tagline,8);

        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(19),dp(22),dp(19),dp(20));
        card.setBackground(RoutixTheme.surface(theme,theme.mantle,28,getResources().getDisplayMetrics().density));
        card.setElevation(dp(5));
        LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);
        cardLp.topMargin=dp(40);
        page.addView(card,cardLp);

        TextView step=label("01  /  01",12,Typeface.BOLD,theme.accent);
        step.setLetterSpacing(.12f);
        card.addView(step);
        TextView permission=label(getString(R.string.permission_location_title),22,Typeface.BOLD,theme.text);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);
        titleLp.topMargin=dp(14);
        card.addView(permission,titleLp);

        TextView detail=label(getString(R.string.permission_location_explanation),14,Typeface.NORMAL,theme.muted());
        LinearLayout.LayoutParams detailLp=new LinearLayout.LayoutParams(-1,-2);
        detailLp.topMargin=dp(11);
        card.addView(detail,detailLp);

        status=label("",13,Typeface.NORMAL,theme.muted());
        LinearLayout.LayoutParams statusLp=new LinearLayout.LayoutParams(-1,-2);
        statusLp.topMargin=dp(14);
        card.addView(status,statusLp);

        action=label(getString(R.string.permission_continue),16,Typeface.BOLD,theme.onAccent());
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(14),dp(14),dp(14),dp(14));
        action.setMinHeight(dp(60));
        action.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(theme.tint(theme.accent)),
            RoutixTheme.surface(theme,theme.accent,20,getResources().getDisplayMetrics().density),null));
        action.setOnClickListener(v->requestLocation());
        action.setFocusable(true);
        LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(-1,dp(60));
        actionLp.topMargin=dp(24);
        card.addView(action,actionLp);

        scroll.addView(page);
        setContentView(scroll);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,in)->{
            androidx.core.graphics.Insets b=in.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()|
                androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            v.setPadding(0,b.top,0,b.bottom);
            return in;
        });
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        refresh();
    }
    private boolean hasLocation() {
        return ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
            ==PackageManager.PERMISSION_GRANTED;
    }
    private boolean settingsRequired() {
        return prefs.getBoolean("location_requested",false)
            &&!ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_FINE_LOCATION);
    }
    private void requestLocation() {
        if(hasLocation()){complete();return;}
        if(settingsRequired()){
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:"+getPackageName())));
            return;
        }
        prefs.edit().putBoolean("location_requested",true).apply();
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] result) {
        super.onRequestPermissionsResult(code,permissions,result);
        if(code==LOCATION_REQUEST){if(hasLocation())complete();else refresh();}
    }
    @Override protected void onResume() {
        super.onResume();
        if(action!=null){if(hasLocation())complete();else refresh();}
    }
    private void refresh() {
        if(action==null)return;
        boolean settings=settingsRequired();
        status.setText(settings?R.string.permission_status_settings:R.string.permission_location_status);
        action.setText(settings?R.string.permission_settings:R.string.permission_continue);
    }
    private void complete() {
        prefs.edit().putBoolean("onboarding_complete",true).putBoolean("privacy_notice_seen",true)
            .putBoolean("location_usage_accepted",true).apply();
        launchApp();
    }
    private void launchApp() {
        startActivity(new Intent(this,RoutixActivity.class));finish();
        if(RoutixTheme.motion(prefs))overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);
    }
    private void add(LinearLayout parent,View child,int marginTop) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(marginTop);parent.addView(child,lp);
    }
    private TextView label(String value,float size,int weight,int color) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
        t.setTypeface(Typeface.create("sans-serif",weight));
        t.setTextColor(theme.readable(color));t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(3),1f);return t;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
