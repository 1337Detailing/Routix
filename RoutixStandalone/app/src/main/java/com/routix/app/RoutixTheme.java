package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import androidx.core.graphics.ColorUtils;

/**
 * Routix UI system. Material You-inspired surfaces, accessible contrast and
 * balanced light/dark palettes. UI-only: tracking and route data are untouched.
 */
final class RoutixTheme {
    static final int BLACK=Color.rgb(12,18,28), SAND=Color.rgb(159,184,250);
    static final int CARD=Color.rgb(22,30,44), CARD_HIGH=Color.rgb(32,42,58);
    static final int INK=Color.rgb(238,243,251), MUTED=Color.rgb(170,184,202);
    static final int OUTLINE=Color.rgb(62,76,96);
    static final int CORNER=20, CORNER_LARGE=28, TAP_MIN=48;
    static final int MOTION_SHORT=160, MOTION_MEDIUM=260;

    static final class Tokens {
        final String flavor;
        final int background,surface,content,secondaryContent,success,error,warning,info,navigation;
        final int base,mantle,crust,surface0,surface1,surface2,overlay0,overlay1,overlay2,text,subtext1,subtext0;
        final int rosewater,flamingo,pink,mauve,red,maroon,peach,yellow,green,teal,sky,sapphire,blue,lavender,accent;

        Tokens(boolean light){this(light,0);}
        Tokens(boolean light,int accentOverride){
            flavor=light?"map-light":"sand";
            base=light?Color.rgb(245,247,252):BLACK;
            mantle=light?Color.WHITE:CARD;
            crust=light?Color.rgb(232,237,247):Color.rgb(8,13,22);
            surface0=light?Color.rgb(234,239,249):CARD_HIGH;
            surface1=light?Color.rgb(212,221,235):OUTLINE;
            surface2=light?Color.rgb(217,226,240):Color.rgb(42,54,72);
            overlay0=light?Color.rgb(184,195,214):Color.rgb(78,92,112);
            overlay1=light?Color.rgb(148,163,186):Color.rgb(105,120,139);
            overlay2=light?Color.rgb(112,130,156):Color.rgb(135,151,172);
            text=light?Color.rgb(26,38,57):INK;
            subtext1=light?Color.rgb(69,84,107):Color.rgb(201,212,226);
            subtext0=light?Color.rgb(86,103,127):MUTED;
            rosewater=light?Color.rgb(164,96,136):Color.rgb(238,179,206);flamingo=rosewater;
            pink=light?Color.rgb(179,88,135):Color.rgb(237,169,202);
            mauve=light?Color.rgb(110,87,192):Color.rgb(193,175,250);
            red=light?Color.rgb(177,49,70):Color.rgb(255,151,161);maroon=red;
            peach=light?Color.rgb(145,96,31):Color.rgb(248,192,111);
            yellow=light?Color.rgb(139,105,32):Color.rgb(235,201,119);
            green=light?Color.rgb(25,119,90):Color.rgb(126,219,176);
            teal=light?Color.rgb(20,119,125):Color.rgb(125,215,220);
            sky=light?Color.rgb(46,107,172):Color.rgb(160,204,252);
            sapphire=sky;blue=sky;lavender=mauve;
            int primary=accentOverride!=0?accentOverride:(light?Color.rgb(49,91,193):Color.rgb(164,190,255));
            // Dynamic system accents can have poor contrast; normalize toward the
            // dark or light accessible UI foreground before using them on buttons.
            if(ColorUtils.calculateContrast(primary,base)<3.0)
                primary=ColorUtils.blendARGB(primary,light?Color.BLACK:Color.WHITE,.32f);
            accent=primary;
            background=base;surface=mantle;content=text;secondaryContent=subtext0;
            success=green;error=red;warning=peach;info=blue;navigation=accent;
        }
        int readable(int color){
            if(color==Color.BLACK||color==Color.WHITE)return color;
            return ColorUtils.calculateContrast(color,base)>=4.5&&
                ColorUtils.calculateContrast(color,mantle)>=4.5?color:text;
        }
        int muted(){return subtext0;}
        int onAccent(){return ink(accent);}
        int tint(int color){return ColorUtils.blendARGB(mantle,color,.13f);}
        boolean light(){return "map-light".equals(flavor);}
    }
    static Tokens from(SharedPreferences p){
        String mode=p==null?"dark":p.getString("app_theme","dark");
        boolean light="light".equals(mode)||("system".equals(mode)&&
            (Resources.getSystem().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES);
        return new Tokens(light);
    }
    static Tokens from(Context c){
        SharedPreferences p=c.getSharedPreferences("routix",0);
        String mode=p.getString("app_theme","dark");
        boolean light="light".equals(mode)||("system".equals(mode)&&
            (c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES);
        int dynamic=0;
        if(Build.VERSION.SDK_INT>=31&&p.getBoolean("dynamic_colors",false)){
            int id=c.getResources().getIdentifier(light?"system_accent1_600":"system_accent1_200","color","android");
            if(id!=0)try{dynamic=c.getColor(id);}catch(Resources.NotFoundException ignored){}
        }
        return new Tokens(light,dynamic);
    }
    static Tokens lightMap(){return new Tokens(true);}
    static Tokens flavor(String flavor,String ignored){return "map-light".equals(flavor)?lightMap():new Tokens(false);}
    static String[] flavors(){return new String[]{"sand","map-light"};}
    static String[] accents(){return new String[]{"Ocean"};}
    static void setFlavor(SharedPreferences p,String flavor){p.edit().putString("app_theme","map-light".equals(flavor)?"light":"dark").apply();}
    static void setAccent(SharedPreferences p,String accent){p.edit().putBoolean("dynamic_colors",false).apply();}
    static int ink(int color){return ColorUtils.calculateContrast(Color.BLACK,color)>=4.5?Color.BLACK:Color.WHITE;}
    static GradientDrawable surface(Tokens t,int color,int radiusDp,float density){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(Math.max(4,radiusDp)*density);
        if(color==t.mantle||color==t.surface0)
            d.setStroke(Math.max(1,Math.round(density)),t.surface1);
        return d;
    }
    static boolean motion(SharedPreferences p){
        return p.getBoolean("animations",true)&&android.animation.ValueAnimator.areAnimatorsEnabled();
    }
    static int activityStyle(SharedPreferences p){return R.style.Theme_Routix;}
    static void systemBars(android.app.Activity a,Tokens t){
        a.getWindow().setStatusBarColor(t.base);a.getWindow().setNavigationBarColor(t.base);
        androidx.core.view.WindowInsetsControllerCompat c=androidx.core.view.WindowCompat
            .getInsetsController(a.getWindow(),a.getWindow().getDecorView());
        c.setAppearanceLightStatusBars(t.light());c.setAppearanceLightNavigationBars(t.light());
    }
}
