package com.routix.app;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Exports real route geometry without connecting GPS segments across outages. */
final class RouteExporter {
    private RouteExporter(){}
    static File write(Context context,RouteStore.Summary route,String displayName,boolean kml)throws IOException{
        if(route==null||route.points.isEmpty())throw new IOException("Empty route");
        File dir=new File(context.getCacheDir(),"exports");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create export directory");
        String safe=(displayName==null?"route":displayName).replaceAll("[^\\p{L}\\p{N}._ -]","_").trim();
        if(safe.isEmpty())safe="route";
        if(safe.length()>72)safe=safe.substring(0,72);
        File target=new File(dir,safe+(kml?".kml":".csv"));
        File temp=new File(dir,target.getName()+".tmp");
        try(BufferedWriter w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(temp),StandardCharsets.UTF_8))){
            if(kml)kml(w,route,displayName);else csv(w,route);
        }catch(IOException e){temp.delete();throw e;}
        if(!temp.renameTo(target)){temp.delete();throw new IOException("Export could not be saved");}
        return target;
    }
    private static void csv(Writer w,RouteStore.Summary r)throws IOException{
        w.write("kind,time_utc,latitude,longitude,accuracy_m,segment_start,type,label\n");
        for(RouteStore.Point p:r.points){
            w.write("point,");w.write(RouteStore.iso(p.time));w.write(',');
            w.write(Double.toString(p.lat));w.write(',');w.write(Double.toString(p.lon));w.write(',');
            w.write(Float.toString(p.accuracy));w.write(',');w.write(p.breakBefore?"true":"false");
            w.write(",,\n");
        }
        for(RouteStore.Event e:r.events){
            w.write("event,");w.write(RouteStore.iso(e.time));w.write(',');
            w.write(Double.toString(e.lat));w.write(',');w.write(Double.toString(e.lon));w.write(',');
            w.write(Float.toString(e.accuracy));w.write(",,");
            cell(w,e.type);w.write(',');cell(w,e.label);w.write('\n');
        }
    }
    private static void cell(Writer w,String value)throws IOException{
        w.write('"');w.write((value==null?"":value).replace("\"","\"\""));w.write('"');
    }
    private static void kml(Writer w,RouteStore.Summary r,String displayName)throws IOException{
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        w.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><name>");
        w.write(RouteStore.escape(displayName));w.write("</name>\n");
        List<RouteStore.Point> points=r.points;
        int segment=0;
        for(int start=0;start<points.size();){
            int end=start+1;
            while(end<points.size()&&!points.get(end).breakBefore)end++;
            if(end-start>=2){
                w.write("<Placemark><name>Segment ");w.write(Integer.toString(++segment));
                w.write("</name><LineString><coordinates>");
                for(int i=start;i<end;i++){
                    RouteStore.Point p=points.get(i);
                    w.write(Double.toString(p.lon));w.write(',');
                    w.write(Double.toString(p.lat));w.write(",0 ");
                }
                w.write("</coordinates></LineString></Placemark>\n");
            }
            start=end;
        }
        for(RouteStore.Event e:r.events){
            w.write("<Placemark><name>");w.write(RouteStore.escape(e.label));
            w.write("</name><description>");w.write(RouteStore.escape(e.type));
            w.write("</description><Point><coordinates>");w.write(Double.toString(e.lon));
            w.write(',');w.write(Double.toString(e.lat));
            w.write(",0</coordinates></Point></Placemark>\n");
        }
        w.write("</Document></kml>\n");
    }
}
