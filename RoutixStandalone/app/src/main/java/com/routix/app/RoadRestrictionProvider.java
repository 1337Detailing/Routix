package com.routix.app;
import org.json.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Cached OSM road restrictions around the vehicle. Network refresh is sparse; matching is local. */
final class RoadRestrictionProvider{
 static final class Bridge{final double lat,lon;final String height,name;Bridge(double a,double o,String h,String n){lat=a;lon=o;height=h;name=n;}}
 static final class Snapshot{final String speed;final List<Bridge> bridges;Snapshot(String s,List<Bridge>b){speed=s;bridges=b;}}
 private static final class Way{final List<RouteStore.Point> p;final String speed;Way(List<RouteStore.Point>x,String s){p=x;speed=s;}}
 private final Object lock=new Object();private List<Way> ways=Collections.emptyList();private List<Bridge> bridges=Collections.emptyList();private double centerLat=999,centerLon=999;private long fetchedAt;private volatile long lastAttempt;private volatile boolean loading;private Snapshot cached;private double cachedLat,cachedLon;private long cachedAt;
 Snapshot snapshot(double lat,double lon){
  maybeRefresh(lat,lon);
  long now=android.os.SystemClock.elapsedRealtime();
  List<Way>w;List<Bridge>b;
  synchronized(lock){
   if(cached!=null&&now-cachedAt<1500&&dist(lat,lon,cachedLat,cachedLon)<8)return cached;
   w=ways;b=bridges;
  }
  String speed="—";double best=28;
  for(Way x:w)if(x.speed!=null){double d=distanceToWay(lat,lon,x.p);if(d<best){best=d;speed=x.speed;}}
  List<Bridge> near=new ArrayList<>();for(Bridge x:b)if(dist(lat,lon,x.lat,x.lon)<1800)near.add(x);
  Snapshot result=new Snapshot(speed,near);
  synchronized(lock){cached=result;cachedLat=lat;cachedLon=lon;cachedAt=now;}
  return result;
 }
 private void maybeRefresh(double lat,double lon){
  long now=System.currentTimeMillis();
  if(loading||now-lastAttempt<60000)return;
  if(now-fetchedAt<300000&&dist(lat,lon,centerLat,centerLon)<900)return;
  loading=true;lastAttempt=now;
  new Thread(()->{try{load(lat,lon);}catch(Exception e){DiagnosticLog.error("OSM restrictions",e);}finally{loading=false;}},"osm-road-restrictions").start();
 }
 private void load(double lat,double lon)throws Exception{String q="[out:json][timeout:18];(way(around:2200,"+lat+","+lon+")[highway][maxspeed];way(around:2200,"+lat+","+lon+")[highway][maxheight];way(around:2200,"+lat+","+lon+")[highway][maxheight:physical];node(around:2200,"+lat+","+lon+")[barrier=height_restrictor];);out tags geom;";JSONObject root=new JSONObject(post(q));JSONArray a=root.optJSONArray("elements");List<Way> nw=new ArrayList<>();List<Bridge> nb=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i),t=e.optJSONObject("tags");if(t==null)continue;String speed=parseSpeed(t.optString("maxspeed:hgv",t.optString("maxspeed","")));String h=height(t);JSONArray g=e.optJSONArray("geometry");List<RouteStore.Point> pts=new ArrayList<>();if(g!=null)for(int j=0;j<g.length();j++){JSONObject n=g.getJSONObject(j);pts.add(new RouteStore.Point(n.getDouble("lat"),n.getDouble("lon"),0,0));}if(speed!=null&&pts.size()>1)nw.add(new Way(pts,speed));if(h!=null){double la=e.optDouble("lat",Double.NaN),lo=e.optDouble("lon",Double.NaN);if(!Double.isFinite(la)&&!pts.isEmpty()){RouteStore.Point m=pts.get(pts.size()/2);la=m.lat;lo=m.lon;}if(Double.isFinite(la))nb.add(new Bridge(la,lo,h,t.optString("name","Gabarit")));}}
 synchronized(lock){ways=nw;bridges=nb;centerLat=lat;centerLon=lon;fetchedAt=System.currentTimeMillis();cached=null;}}
 private static String height(JSONObject t){String x=t.optString("maxheight:physical",t.optString("maxheight","")).trim();if(x.isEmpty()||"default".equals(x)||"unsigned".equals(x))return null;return x.matches("[0-9.]+")?x+" m":x;}
 private static String parseSpeed(String x){if(x==null)return null;x=x.trim().toLowerCase(Locale.ROOT);try{if(x.endsWith("mph"))return String.valueOf(Math.round(Double.parseDouble(x.replace("mph","").trim())*1.60934));if(x.matches("[0-9.]+"))return String.valueOf(Math.round(Double.parseDouble(x)));}catch(Exception ignored){}return null;}
 private static double distanceToWay(double lat,double lon,List<RouteStore.Point>p){double d=1e9;RouteStore.Point q=new RouteStore.Point(lat,lon,0,0);for(int i=1;i<p.size();i++)d=Math.min(d,seg(q,p.get(i-1),p.get(i)));return d;}
 private static double seg(RouteStore.Point p,RouteStore.Point a,RouteStore.Point b){double k=111320,c=Math.cos(Math.toRadians(p.lat)),x=(p.lon-a.lon)*k*c,y=(p.lat-a.lat)*k,bx=(b.lon-a.lon)*k*c,by=(b.lat-a.lat)*k,t=(bx*bx+by*by)==0?0:(x*bx+y*by)/(bx*bx+by*by);t=Math.max(0,Math.min(1,t));return Math.hypot(x-t*bx,y-t*by);}
 private static double dist(double a,double o,double b,double p){if(Math.abs(a)>90||Math.abs(b)>90)return 1e9;double x=Math.toRadians(p-o)*Math.cos(Math.toRadians((a+b)/2)),y=Math.toRadians(b-a);return 6371000*Math.hypot(x,y);}
 private static String post(String q)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL("https://overpass-api.de/api/interpreter").openConnection();c.setConnectTimeout(10000);c.setReadTimeout(22000);c.setDoOutput(true);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");c.setRequestProperty("User-Agent","Routix/2.1");try(OutputStream o=c.getOutputStream()){o.write(("data="+URLEncoder.encode(q,"UTF-8")).getBytes(StandardCharsets.UTF_8));}if(c.getResponseCode()/100!=2)throw new IOException("Overpass "+c.getResponseCode());ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];try(InputStream in=c.getInputStream()){for(int n;(n=in.read(b))>0;)o.write(b,0,n);}return o.toString("UTF-8");}
}