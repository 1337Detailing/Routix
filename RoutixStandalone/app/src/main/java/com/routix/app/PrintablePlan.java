package com.routix.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Street-labelled paper atlas and PNG export of the original GPS trace. */
final class PrintablePlan {
    private static final int W=1240,H=1754,BLUE=Color.rgb(0,75,165);
    private static final String[] OVERPASS_ENDPOINTS={"https://overpass-api.de/api/interpreter","https://overpass.kumi.systems/api/interpreter","https://overpass.nchc.org.tw/api/interpreter"};
    private final Activity activity;
    private final RouteStore.Summary route;
    private final String title;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final List<PaperRoute.Point> trace=new ArrayList<>();
    private List<PaperRoute.Road> roads;
    private List<PaperRoute.Step> steps;
    private List<int[]> sheets;
    private final ArrayList<File> images=new ArrayList<>();
    private File pdf;
    private double lat0,lon0,lonScale;
    private AlertDialog progress;
    private PrintablePlan(Activity a,RouteStore.Summary r,String title){activity=a;route=r;this.title=title;}

    static void open(Activity a,RouteStore.Summary r,String title){
        if(r.points.size()<2){Toast.makeText(a,"Pas assez de points GPS",Toast.LENGTH_LONG).show();return;}
        new RoutixDialogs.Builder(a).setTitle("Plan imprimable Routix")
            .setMessage("Créer des images A4 et un PDF avec les rues numérotées, dans l’ordre de la tournée.\n\nPour trouver les noms, la zone géographique du parcours est envoyée au service OpenStreetMap Overpass. Routix réutilise son cache local et essaie plusieurs sources OpenStreetMap si nécessaire. Connexion nécessaire uniquement quand les rues de cette zone ne sont pas encore en cache. Les fichiers restent sur ce téléphone jusqu’au partage.")
            .setNegativeButton("Annuler",null).setPositiveButton("Générer le plan",(d,w)->new PrintablePlan(a,r,title).start()).show();
    }
    private boolean active(){return !cancelled.get()&&!activity.isFinishing()&&!activity.isDestroyed();}
    private void check() throws IOException {if(!active())throw new IOException("Génération annulée");}
    private void start(){
        progress=new RoutixDialogs.Builder(activity).setTitle("Création du plan…")
            .setMessage("Récupération des rues puis création des pages A4.")
            .setNegativeButton("Annuler",(d,w)->cancelled.set(true)).create();
        progress.setOnCancelListener(d->cancelled.set(true));progress.show();
        new Thread(()->{
            try{generate();activity.runOnUiThread(()->{if(active()){progress.dismiss();preview(0);}});}
            catch(Exception e){activity.runOnUiThread(()->{if(active()){progress.dismiss();new RoutixDialogs.Builder(activity).setTitle("Plan non généré").setMessage(e.getMessage()==null?"Impossible de créer le plan. Réessaie avec une connexion Internet.":e.getMessage()).setPositiveButton("Fermer",null).show();}});}
        },"Routix-paper-plan").start();
    }
    private PaperRoute.Point project(double lat,double lon){return new PaperRoute.Point((lon-lon0)*lonScale,(lat-lat0)*111320);}
    static List<PaperRoute.Step> streetSteps(Activity activity,RouteStore.Summary route)throws Exception{
        if(route.points.size()<2)throw new IOException("Trace insuffisante");
        PrintablePlan plan=new PrintablePlan(activity,route,"");
        plan.lat0=route.points.get(0).lat;plan.lon0=route.points.get(0).lon;plan.lonScale=111320*Math.cos(Math.toRadians(plan.lat0));
        double south=90,north=-90,west=180,east=-180;
        for(RouteStore.Point p:route.points){
            if(!Double.isFinite(p.lat)||!Double.isFinite(p.lon)||Math.abs(p.lat)>85||Math.abs(p.lon)>180)throw new IOException("Coordonnées invalides");
            plan.trace.add(plan.project(p.lat,p.lon));south=Math.min(south,p.lat);north=Math.max(north,p.lat);west=Math.min(west,p.lon);east=Math.max(east,p.lon);
        }
        if(plan.trace.size()>100000||(north-south)*111320*(east-west)*plan.lonScale>100000000||north-south>.3||east-west>.5)throw new IOException("Zone trop vaste pour rechercher les rues (100 km² maximum)");
        String bbox=String.format(Locale.US,"%.6f,%.6f,%.6f,%.6f",south-.002,west-.003,north+.002,east+.003);
        return PaperRoute.steps(plan.trace,plan.readRoads(bbox));
    }
    private void generate() throws Exception {
        lat0=route.points.get(0).lat;lon0=route.points.get(0).lon;lonScale=111320*Math.cos(Math.toRadians(lat0));
        double south=90,north=-90,west=180,east=-180;
        for(RouteStore.Point p:route.points){
            if(!Double.isFinite(p.lat)||!Double.isFinite(p.lon)||Math.abs(p.lat)>85||Math.abs(p.lon)>180)throw new IOException("Le fichier contient des coordonnées GPS invalides.");
            trace.add(project(p.lat,p.lon));south=Math.min(south,p.lat);north=Math.max(north,p.lat);west=Math.min(west,p.lon);east=Math.max(east,p.lon);
        }
        if(trace.size()>100000)throw new IOException("Cette tournée est trop volumineuse pour cet export (100 000 points maximum).");
        if((north-south)*111320*(east-west)*lonScale>100000000||north-south>.3||east-west>.5)throw new IOException("Cette tournée couvre une zone trop vaste. Exporte des tournées plus courtes (zone de 100 km² maximum).");
        south-=.002;north+=.002;west-=.003;east+=.003;
        String bbox=String.format(Locale.US,"%.6f,%.6f,%.6f,%.6f",south,west,north,east);
        roads=readRoads(bbox);check();
        steps=PaperRoute.steps(trace,roads);sheets=PaperRoute.sheets(trace,steps);
        if(sheets.size()>100)throw new IOException("Plus de 100 pages seraient nécessaires. Utilise une tournée plus courte.");
        File dir=new File(activity.getCacheDir(),"paper-plans/"+UUID.randomUUID());
        if(!dir.mkdirs())throw new IOException("Espace de stockage indisponible.");
        pdf=new File(dir,"Routix-plan.pdf");
        PdfDocument doc=new PdfDocument();
        try{
            for(int i=0;i<sheets.size();i++){
                check();final int page=i+1;activity.runOnUiThread(()->{if(active())progress.setMessage("Création de la page "+page+" / "+sheets.size());});
                Bitmap bitmap=Bitmap.createBitmap(W,H,Bitmap.Config.RGB_565);
                try{
                    render(new Canvas(bitmap),i);
                    File png=new File(dir,String.format(Locale.ROOT,"Routix-plan-%03d.png",i+1));
                    try(OutputStream out=new FileOutputStream(png)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Échec de l’image");}images.add(png);
                    PdfDocument.Page p=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,i+1).create());
                    p.getCanvas().scale(595f/W,842f/H);render(p.getCanvas(),i);doc.finishPage(p);
                }finally{bitmap.recycle();}
            }
            try(OutputStream out=new FileOutputStream(pdf)){doc.writeTo(out);}
        }finally{doc.close();}
    }
    private List<PaperRoute.Road> readRoads(String bbox) throws Exception {
        File cacheDir=new File(activity.getFilesDir(),"paper-road-data");cacheDir.mkdirs();
        String hash=android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(bbox.getBytes(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE);
        File cached=new File(cacheDir,hash+".json");String json=null;
        // Street data is durable: OSM road geometry remains useful offline even when old.
        if(cached.exists())try{json=new String(Files.readAllBytes(cached.toPath()),StandardCharsets.UTF_8);validateRoadJson(json);}catch(Exception ignored){json=null;}
        if(json==null){
            String query="[out:json][timeout:35];way[highway][highway!~\"^(footway|cycleway|steps|path|bridleway|proposed|construction)$\"]("+bbox+");out geom;";
            ArrayList<String> failures=new ArrayList<>();
            for(String endpoint:OVERPASS_ENDPOINTS){
                check();HttpURLConnection connection=null;
                try{
                    connection=(HttpURLConnection)new URL(endpoint).openConnection();connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setConnectTimeout(10000);connection.setReadTimeout(45000);
                    connection.setRequestProperty("User-Agent","Routix/2.1 printable-plan");connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
                    try(OutputStream out=connection.getOutputStream()){out.write(("data="+URLEncoder.encode(query,"UTF-8")).getBytes(StandardCharsets.UTF_8));}
                    int code=connection.getResponseCode();if(code!=200){failures.add(code+" "+new URL(endpoint).getHost());continue;}
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){check();bytes.write(buffer,0,n);if(bytes.size()>24*1024*1024)throw new IOException("Trop de données cartographiques pour cette zone.");}}
                    String candidate=bytes.toString("UTF-8");validateRoadJson(candidate);json=candidate;break;
                }catch(Exception e){failures.add((connection==null?"réseau":new URL(endpoint).getHost())+": "+e.getClass().getSimpleName());}
                finally{if(connection!=null)connection.disconnect();}
            }
            if(json==null)throw new IOException("Impossible de récupérer les rues. Vérifie la connexion puis réessaie. Routix a testé plusieurs serveurs cartographiques.");
            RouteArchive.atomic(cached,json.getBytes(StandardCharsets.UTF_8));
        }
        return parseRoads(json);
    }
    private void validateRoadJson(String json)throws Exception{
        JSONObject root=new JSONObject(json);if(root.has("remark"))throw new IOException("Réponse cartographique incomplète");
        JSONArray elements=root.optJSONArray("elements");if(elements==null||elements.length()==0)throw new IOException("Aucune rue trouvée");
    }
    private List<PaperRoute.Road> parseRoads(String json)throws Exception{
        JSONObject root=new JSONObject(json);List<PaperRoute.Road> result=new ArrayList<>();JSONArray elements=root.getJSONArray("elements");
        for(int i=0;i<elements.length();i++){JSONObject e=elements.getJSONObject(i),tags=e.optJSONObject("tags");JSONArray geometry=e.optJSONArray("geometry");if(tags==null||geometry==null)continue;List<PaperRoute.Point> line=new ArrayList<>();
            for(int j=0;j<geometry.length();j++){JSONObject p=geometry.getJSONObject(j);line.add(project(p.getDouble("lat"),p.getDouble("lon")));}if(line.size()>1)result.add(new PaperRoute.Road(e.optString("id"),tags.optString("name",tags.optString("ref","")),line));}
        if(result.isEmpty())throw new IOException("Aucune rue carrossable trouvée dans cette zone.");return result;
    }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private void text(Canvas c,String text,float x,float y,float size,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));c.drawText(text,x,y,paint);}
    private void fitted(Canvas c,String text,float x,float y,float max,float size){paint.setTextSize(size);while(paint.measureText(text)>max&&size>16)paint.setTextSize(--size);text(c,text,x,y,size,Color.BLACK);}
    private double minX,maxY,scale;
    private float px(PaperRoute.Point p){return (float)(60+(p.x-minX)*scale);}
    private float py(PaperRoute.Point p){return (float)(190+(maxY-p.y)*scale);}
    private void line(Canvas c,PaperRoute.Point a,PaperRoute.Point b,int color,float width){paint.setColor(color);paint.setStrokeWidth(width);c.drawLine(px(a),py(a),px(b),py(b),paint);}
    private void badge(Canvas c,String value,float x,float y,int color){paint.setColor(Color.WHITE);c.drawCircle(x,y,23,paint);paint.setColor(color);c.drawCircle(x,y,20,paint);paint.setTextSize(22);float width=paint.measureText(value);text(c,value,x-width/2,y+8,22,Color.WHITE);}
    private void render(Canvas c,int page){
        int[] range=sheets.get(page);int start=range[0],end=range[1];
        c.drawColor(Color.WHITE);fitted(c,title,60,65,1100,36);text(c,"ROUTIX • Plan "+(page+1)+" / "+sheets.size()+" • Suivre les numéros dans l’ordre",60,110,25,BLUE);
        text(c,"N ↑  •  D : départ  •  A : arrivée  •  MA : marche arrière  •  2C : deux côtés",60,155,22,Color.DKGRAY);
        double maxX=-Double.MAX_VALUE,minY=Double.MAX_VALUE;minX=Double.MAX_VALUE;maxY=-Double.MAX_VALUE;
        for(int i=start;i<=end;i++){PaperRoute.Point p=trace.get(i);minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);}
        double width=Math.max(200,maxX-minX+150),height=Math.max(200,maxY-minY+150),cx=(minX+maxX)/2,cy=(minY+maxY)/2;
        scale=Math.min(1120/width,920/height);minX=cx-560/scale;maxY=cy+460/scale;
        c.save();c.clipRect(60,190,1180,1110);paint.setColor(Color.rgb(248,248,244));c.drawRect(60,190,1180,1110,paint);
        for(PaperRoute.Road r:roads)for(int j=1;j<r.points.size();j++){line(c,r.points.get(j-1),r.points.get(j),Color.LTGRAY,12);line(c,r.points.get(j-1),r.points.get(j),Color.WHITE,8);}
        List<RectF> occupied=new ArrayList<>();
        for(PaperRoute.Road r:roads){
            if(r.name.equals("Voie sans nom"))continue;
            for(int j=1;j<r.points.size();j++){
                PaperRoute.Point a=r.points.get(j-1),b=r.points.get(j);float x=(px(a)+px(b))/2,y=(py(a)+py(b))/2;paint.setTextSize(21);float tw=paint.measureText(r.name);
                RectF box=new RectF(x-tw/2-4,y-24,x+tw/2+4,y+5);boolean collision=false;for(RectF used:occupied)if(RectF.intersects(box,used)){collision=true;break;}
                if(box.left<65||box.right>1175||box.top<200||box.bottom>1100||collision)continue;
                paint.setColor(Color.WHITE);c.drawRect(box,paint);text(c,r.name,x-tw/2,y,21,Color.DKGRAY);occupied.add(box);break;
            }
        }
        for(int i=start+1;i<=end;i++){line(c,trace.get(i-1),trace.get(i),Color.WHITE,9);line(c,trace.get(i-1),trace.get(i),BLUE,5);}
        double arrowAt=0;for(int i=start+1;i<=end;i++){
            PaperRoute.Point a=trace.get(i-1),b=trace.get(i);arrowAt+=PaperRoute.distance(a,b)*scale;
            if(arrowAt>110){arrowAt=0;float x=px(b),y=py(b);double angle=Math.atan2(py(b)-py(a),px(b)-px(a));paint.setColor(BLUE);paint.setStrokeWidth(4);c.drawLine(x,y,x-(float)(14*Math.cos(angle-.5)),y-(float)(14*Math.sin(angle-.5)),paint);c.drawLine(x,y,x-(float)(14*Math.cos(angle+.5)),y-(float)(14*Math.sin(angle+.5)),paint);}
        }
        for(RouteStore.Event event:route.events){
            int nearest=0;double best=Double.MAX_VALUE;PaperRoute.Point p=project(event.lat,event.lon);
            for(int i=0;i<trace.size();i++){double d=event.time>0&&route.points.get(i).time>0?Math.abs((double)event.time-route.points.get(i).time):PaperRoute.distance(p,trace.get(i));if(d<best){best=d;nearest=i;}}
            if(nearest>=start&&nearest<=end)badge(c,"REVERSE".equals(event.type)?"MA":"2C",px(p),py(p),Color.rgb(145,70,0));
        }
        List<PaperRoute.Step> shown=new ArrayList<>();PaperRoute.Step current=steps.get(0);
        for(PaperRoute.Step s:steps){if(s.index<=start)current=s;if(s.index>start&&s.index<=end)shown.add(s);}shown.add(0,current);
        List<RectF> markers=new ArrayList<>();
        for(PaperRoute.Step s:shown){
            PaperRoute.Point p=trace.get(Math.max(start,s.index));float x=px(p),y=py(p),bx=x,by=y;
            for(int attempt=0;attempt<100;attempt++){
                double angle=attempt*2.4,radius=attempt==0?0:28+7*Math.sqrt(attempt);bx=Math.max(85,Math.min(1155,x+(float)(Math.cos(angle)*radius)));by=Math.max(220,Math.min(1080,y+(float)(Math.sin(angle)*radius)));
                RectF box=new RectF(bx-24,by-24,bx+24,by+24);boolean collides=false;for(RectF used:markers)if(RectF.intersects(box,used)){collides=true;break;}if(!collides){markers.add(box);break;}
            }
            paint.setStrokeWidth(2);paint.setColor(Color.BLACK);c.drawLine(x,y,bx,by,paint);badge(c,""+s.number,bx,by,Color.BLACK);
        }
        if(start==0)badge(c,"D",px(trace.get(0))-28,py(trace.get(0))+28,Color.rgb(0,110,40));
        if(end==trace.size()-1)badge(c,"A",px(trace.get(end))+28,py(trace.get(end))+28,Color.rgb(170,0,20));
        c.restore();text(c,"Ordre de passage — repères noirs sur le plan",60,1160,27,BLUE);
        int row=0;for(PaperRoute.Step s:shown){String prefix=s.index<start?"Continuer : ":s.number==1?"Départ : ":"Prendre : ";fitted(c,s.number+". "+prefix+s.name,65,1210+row++*39,1100,25);}
        text(c,page==sheets.size()-1?"Fin de tournée : repère A.":"Continuer sur la page "+(page+2)+" (le dernier tronçon est repris).",60,1635,23,Color.BLACK);
        text(c,"Rues estimées depuis le GPS : vérifier les mentions « à vérifier » et les voies sans nom.",60,1675,19,Color.DKGRAY);
        text(c,"© OpenStreetMap contributors • openstreetmap.org/copyright • Tracé GPS original",60,1715,19,Color.DKGRAY);
    }
    private void preview(int index){
        if(!active())return;
        RoutixTheme.Tokens theme=RoutixTheme.from(activity);LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(theme.base);
        TextView heading=new TextView(activity);heading.setTextColor(theme.text);heading.setTextSize(14);heading.setPadding(16,12,16,12);heading.setText("Page "+(index+1)+" / "+images.size()+" • PNG haute résolution + PDF A4");layout.addView(heading);
        ImageView view=new ImageView(activity);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;Bitmap bitmap=BitmapFactory.decodeFile(images.get(index).getAbsolutePath(),options);view.setImageBitmap(bitmap);view.setAdjustViewBounds(true);
        ScrollView scroll=new ScrollView(activity);scroll.addView(view);layout.addView(scroll,new LinearLayout.LayoutParams(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.55)));
        LinearLayout nav=new LinearLayout(activity);Button prev=new Button(activity);prev.setText("Précédente");prev.setEnabled(index>0);Button next=new Button(activity);next.setText("Suivante");next.setEnabled(index+1<images.size());for(Button button:new Button[]{prev,next}){button.setAllCaps(false);button.setTextColor(theme.onAccent());button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(theme.accent));button.setMinHeight((int)(52*activity.getResources().getDisplayMetrics().density));nav.addView(button,new LinearLayout.LayoutParams(0,-2,1));}layout.addView(nav);
        AlertDialog dialog=new RoutixDialogs.Builder(activity).setTitle("Plan imprimable").setView(layout).setNegativeButton("Fermer",null)
            .setNeutralButton("Imprimer",(d,w)->print()).setPositiveButton("Partager",(d,w)->new RoutixDialogs.Builder(activity).setItems(new String[]{"Images PNG (toutes les pages)","PDF A4"},(a,choice)->share(choice==0)).show()).create();
        prev.setOnClickListener(v->{dialog.dismiss();preview(index-1);});next.setOnClickListener(v->{dialog.dismiss();preview(index+1);});dialog.setOnDismissListener(d->{view.setImageDrawable(null);if(bitmap!=null)bitmap.recycle();});dialog.show();
    }
    private void share(boolean png){
        ArrayList<Uri> uris=new ArrayList<>();for(File f:png?images:Collections.singletonList(pdf))uris.add(FileProvider.getUriForFile(activity,activity.getPackageName()+".files",f));
        Intent intent=new Intent(uris.size()>1?Intent.ACTION_SEND_MULTIPLE:Intent.ACTION_SEND);intent.setType(png?"image/png":"application/pdf");
        if(uris.size()>1)intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);else intent.putExtra(Intent.EXTRA_STREAM,uris.get(0));
        ClipData clip=ClipData.newUri(activity.getContentResolver(),"Plan Routix",uris.get(0));for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));intent.setClipData(clip);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{activity.startActivity(Intent.createChooser(intent,"Envoyer le plan imprimable"));}catch(android.content.ActivityNotFoundException e){Toast.makeText(activity,"Aucune application de partage disponible",Toast.LENGTH_LONG).show();}
    }
    private void print(){
        PrintManager manager=(PrintManager)activity.getSystemService(Activity.PRINT_SERVICE);
        if(manager==null){Toast.makeText(activity,"Impression indisponible",Toast.LENGTH_LONG).show();return;}
        manager.print("Routix - "+title,new PrintDocumentAdapter(){
            @Override public void onLayout(PrintAttributes old,PrintAttributes attrs,CancellationSignal signal,LayoutResultCallback callback,Bundle extras){
                if(signal.isCanceled()){callback.onLayoutCancelled();return;}callback.onLayoutFinished(new PrintDocumentInfo.Builder("Routix-plan.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(images.size()).build(),!attrs.equals(old));
            }
            @Override public void onWrite(PageRange[] ranges,ParcelFileDescriptor destination,CancellationSignal signal,WriteResultCallback callback){
                new Thread(()->{
                    ArrayList<PageRange> written=new ArrayList<>();
                    PdfDocument doc=new PdfDocument();
                    try{
                        for(int i=0;i<images.size();i++){
                            if(signal.isCanceled()){callback.onWriteCancelled();return;}boolean selected=false;for(PageRange range:ranges)if(i>=range.getStart()&&i<=range.getEnd())selected=true;if(!selected)continue;
                            PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,i+1).create());page.getCanvas().translate(15,15);page.getCanvas().scale(565f/W,812f/H);render(page.getCanvas(),i);doc.finishPage(page);written.add(new PageRange(i,i));
                        }
                        try(OutputStream out=new FileOutputStream(destination.getFileDescriptor())){doc.writeTo(out);}if(signal.isCanceled())callback.onWriteCancelled();else callback.onWriteFinished(written.toArray(new PageRange[0]));
                    }catch(Exception e){callback.onWriteFailed("Impossible d’imprimer le plan");}finally{doc.close();}
                },"Routix-print").start();
            }
        },new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(PrintAttributes.COLOR_MODE_COLOR).build());
    }
}
