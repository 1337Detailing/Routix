package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import androidx.core.graphics.ColorUtils;

/** Shared matte-black and sand design tokens for every native screen. */
final class RoutixTheme {
    static final int BLACK=Color.rgb(10,10,10), SAND=Color.rgb(216,196,138);
    static final int CARD=Color.rgb(21,21,19), CARD_HIGH=Color.rgb(28,28,25);
    static final int INK=Color.rgb(235,233,227), MUTED=Color.rgb(170,169,161);
    static final int OUTLINE=Color.rgb(56,56,49);
    static final int CORNER=16, CORNER_LARGE=24, TAP_MIN=48;
    static final int MOTION_SHORT=180, MOTION_MEDIUM=300;
    static final class Tokens {
        final String flavor;
        final int background,surface,content,secondaryContent,success,error,warning,info,navigation;
        final int base,mantle,crust,surface0,surface1,surface2,overlay0,overlay1,overlay2,text,subtext1,subtext0;
        final int rosewater,flamingo,pink,mauve,red,maroon,peach,yellow,green,teal,sky,sapphire,blue,lavender,accent;
        Tokens(boolean light) {
            flavor=light?"map-light":"sand";
            base=light?Color.rgb(245,242,233):BLACK;
            mantle=light?Color.rgb(236,232,221):CARD;
            crust=light?Color.rgb(249,247,241):Color.rgb(5,5,5);
            surface0=light?Color.rgb(222,217,205):CARD_HIGH;
            surface1=light?Color.rgb(205,200,190):OUTLINE;
            surface2=light?Color.rgb(194,189,180):Color.rgb(47,47,42);
            overlay0=light?Color.rgb(172,169,161):Color.rgb(74,74,67);
            overlay1=light?Color.rgb(145,143,136):Color.rgb(102,101,92);
            overlay2=light?Color.rgb(113,113,107):Color.rgb(133,132,126);
            text=light?Color.rgb(31,30,27):INK;
            subtext1=light?Color.rgb(77,75,69):Color.rgb(197,194,184);
            subtext0=light?Color.rgb(91,88,81):MUTED;
            rosewater=Color.rgb(222,192,170);flamingo=rosewater;
            pink=Color.rgb(199,159,159);mauve=SAND;
            red=Color.rgb(224,121,116);maroon=red;
            peach=Color.rgb(223,174,120);yellow=SAND;
            green=Color.rgb(150,200,159);teal=Color.rgb(139,187,175);
            sky=Color.rgb(151,184,198);sapphire=SAND;blue=sky;lavender=SAND;
            accent=SAND;
            background=base;surface=mantle;content=text;secondaryContent=subtext0;
            success=green;error=red;warning=peach;info=blue;navigation=SAND;
        }
        int readable(int color) {
            if (color==Color.BLACK) return color; // Ink reserved for sand-filled controls.
            return ColorUtils.calculateContrast(color,base)>=4.5 &&
                ColorUtils.calculateContrast(color,mantle)>=4.5?color:text;
        }
        int muted(){return subtext0;}
        int onAccent(){return ink(accent);}
        int tint(int color){return ColorUtils.blendARGB(base,color,.16f);}
        boolean light(){return "map-light".equals(flavor);}
    }
    static Tokens from(SharedPreferences unused){return new Tokens(false);}
    static Tokens from(Context context){return from(context.getSharedPreferences("routix",0));}
    static Tokens lightMap(){return new Tokens(true);}
    static Tokens flavor(String flavor,String accent){return "map-light".equals(flavor)?lightMap():new Tokens(false);}
    static String[] flavors(){return new String[]{"Sand"};}
    static String[] accents(){return new String[]{"Sand"};}
    static void setFlavor(SharedPreferences p,String flavor){p.edit().remove("theme_flavor").apply();}
    static void setAccent(SharedPreferences p,String accent){p.edit().remove("accent_name").apply();}
    static int ink(int color){return ColorUtils.calculateContrast(Color.BLACK,color)>=4.5?Color.BLACK:Color.WHITE;}
    static GradientDrawable surface(Tokens t,int color,int radiusDp,float density){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(Math.max(4,radiusDp)*density);
        if(color==t.base||color==t.mantle||color==t.surface0)
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
        c.setAppearanceLightStatusBars(false);c.setAppearanceLightNavigationBars(false);
    }
}
