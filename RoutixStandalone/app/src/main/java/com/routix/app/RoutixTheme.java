package com.routix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import androidx.core.graphics.ColorUtils;

/** Premium graphite UI tokens shared by all native Routix screens. */
final class RoutixTheme {
    static final int BLACK=Color.rgb(11,11,17), SAND=Color.rgb(189,167,255); // legacy name preserves source compatibility
    static final int CARD=Color.rgb(23,23,32), CARD_HIGH=Color.rgb(34,33,45);
    static final int INK=Color.rgb(246,244,251), MUTED=Color.rgb(183,179,196);
    static final int OUTLINE=Color.rgb(56,54,73);
    static final int CORNER=18, CORNER_LARGE=26, TAP_MIN=48;
    static final int MOTION_SHORT=180, MOTION_MEDIUM=300;
    static final class Tokens {
        final String flavor;
        final int background,surface,content,secondaryContent,success,error,warning,info,navigation;
        final int base,mantle,crust,surface0,surface1,surface2,overlay0,overlay1,overlay2,text,subtext1,subtext0;
        final int rosewater,flamingo,pink,mauve,red,maroon,peach,yellow,green,teal,sky,sapphire,blue,lavender,accent;
        Tokens(boolean light) {
            flavor=light?"map-light":"sand"; // legacy persisted value; visible design is graphite
            base=light?Color.rgb(246,246,250):BLACK;
            mantle=light?Color.rgb(235,235,243):CARD;
            crust=light?Color.rgb(252,251,255):Color.rgb(6,6,10);
            surface0=light?Color.rgb(224,223,236):CARD_HIGH;
            surface1=light?Color.rgb(208,205,224):OUTLINE;
            surface2=light?Color.rgb(195,191,211):Color.rgb(45,43,59);
            overlay0=light?Color.rgb(170,167,188):Color.rgb(80,76,101);
            overlay1=light?Color.rgb(144,139,165):Color.rgb(108,103,128);
            overlay2=light?Color.rgb(110,107,133):Color.rgb(146,140,164);
            text=light?Color.rgb(32,28,47):INK;
            subtext1=light?Color.rgb(83,79,106):Color.rgb(210,206,222);
            subtext0=light?Color.rgb(102,97,124):MUTED;
            rosewater=Color.rgb(236,201,221);flamingo=rosewater;
            pink=Color.rgb(226,172,218);mauve=SAND;
            red=Color.rgb(241,137,151);maroon=red;
            peach=Color.rgb(241,191,146);yellow=Color.rgb(243,208,146);
            green=Color.rgb(152,223,183);teal=Color.rgb(142,214,210);
            sky=Color.rgb(157,190,247);sapphire=sky;blue=sky;lavender=SAND;
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
    static String[] flavors(){return new String[]{"Graphite"};}
    static String[] accents(){return new String[]{"Violet"};}
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
    /** Selected navigation surfaces remain subtle; the tint is not a filled CTA. */
    static GradientDrawable selected(Tokens t, float density) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(ColorUtils.blendARGB(t.mantle,t.accent,.16f));
        d.setCornerRadius(16f*density);
        d.setStroke(Math.max(1,Math.round(density)),ColorUtils.blendARGB(t.mantle,t.accent,.42f));
        return d;
    }
    static android.graphics.drawable.RippleDrawable touch(Tokens t,int color,int radiusDp,float density){
        int ripple=ColorUtils.setAlphaComponent(t.accent,56);
        return new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(ripple),
            surface(t,color,radiusDp,density),null);
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
