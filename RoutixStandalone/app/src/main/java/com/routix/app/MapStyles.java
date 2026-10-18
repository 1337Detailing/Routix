package com.routix.app;

import android.content.SharedPreferences;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.Layer;
import static org.maplibre.android.style.layers.PropertyFactory.*;

final class MapStyles {
    static RoutixTheme.Tokens tokens(SharedPreferences p){
        RoutixTheme.Tokens t=RoutixTheme.from(p);String appearance=p.getString("map_appearance","theme");
        if(p.getBoolean("auto_night",false)){int hour=java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);appearance=hour>=20||hour<7?"dark":"light";}
        return "light".equals(appearance)?RoutixTheme.lightMap():"dark".equals(appearance)?RoutixTheme.from(p):t;
    }
    static boolean dark(SharedPreferences prefs){return !tokens(prefs).light();}
    static String uri(SharedPreferences prefs){return "asset://maps/"+(dark(prefs)?"dark":"light")+".json";}
    static void apply(Style s,RoutixTheme.Tokens t){
        for(Layer l:s.getLayers())switch(l.getId()){
            case "background":l.setProperties(backgroundColor(t.base));break;
            case "parks":case "woods":l.setProperties(fillColor(t.tint(t.green)));break;
            case "water":l.setProperties(fillColor(t.tint(t.blue)));break;
            case "buildings":l.setProperties(fillColor(t.mantle));break;
            case "roads-outline":l.setProperties(lineColor(t.surface0));break;
            case "roads":l.setProperties(lineColor(t.overlay0));break;
            case "road-names":case "places":l.setProperties(textColor(t.text),textHaloColor(t.base));break;
        }
    }
    static String offlineTheme(String xml,RoutixTheme.Tokens t){return xml.replace("{{base}}",hex(t.base)).replace("{{park}}",hex(t.tint(t.green))).replace("{{water}}",hex(t.tint(t.blue))).replace("{{building}}",hex(t.mantle)).replace("{{outline}}",hex(t.surface0)).replace("{{road}}",hex(t.overlay0)).replace("{{text}}",hex(t.text));}
    private static String hex(int color){return String.format(java.util.Locale.US,"#%06X",color&0xffffff);}
    static double smoothBearing(double previous,double target){double delta=((target-previous+540)%360)-180;return (previous+delta*.22+360)%360;}
}
