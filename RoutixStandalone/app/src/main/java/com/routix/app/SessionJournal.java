package com.routix.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

/** SQLite transactions preserve each committed fix and marker across process death. */
final class SessionJournal extends SQLiteOpenHelper {
    SessionJournal(Context c){super(c,"active-session.db",null,2);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE fixes(id INTEGER PRIMARY KEY,lat REAL,lon REAL,time INTEGER,accuracy REAL,break_before INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY,type TEXT,label TEXT,lat REAL,lon REAL,time INTEGER,accuracy REAL)");
        db.execSQL("CREATE TABLE state(key TEXT PRIMARY KEY,value TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){if(old<2)db.execSQL("ALTER TABLE fixes ADD COLUMN break_before INTEGER NOT NULL DEFAULT 0");}
    synchronized void state(String key,String value){ContentValues v=new ContentValues();v.put("key",key);v.put("value",value);getWritableDatabase().insertWithOnConflict("state",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    synchronized String readState(String key,String fallback){try(Cursor c=getReadableDatabase().query("state",new String[]{"value"},"key=?",new String[]{key},null,null,null)){return c.moveToFirst()?c.getString(0):fallback;}}
    synchronized void point(RouteStore.Point p){ContentValues v=fix(p.lat,p.lon,p.time,p.accuracy);v.put("break_before",p.breakBefore?1:0);getWritableDatabase().insertOrThrow("fixes",null,v);}
    synchronized void event(RouteStore.Event e){ContentValues v=fix(e.lat,e.lon,e.time,e.accuracy);v.put("type",e.type);v.put("label",e.label);getWritableDatabase().insertOrThrow("events",null,v);}
    private ContentValues fix(double lat,double lon,long time,float accuracy){ContentValues v=new ContentValues();v.put("lat",lat);v.put("lon",lon);v.put("time",time);v.put("accuracy",accuracy);return v;}
    synchronized void read(List<RouteStore.Point> points,List<RouteStore.Event> events){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT lat,lon,time,accuracy,break_before FROM fixes ORDER BY id",null)){while(c.moveToNext())points.add(new RouteStore.Point(c.getDouble(0),c.getDouble(1),c.getLong(2),c.getFloat(3),c.getInt(4)!=0));}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT type,label,lat,lon,time,accuracy FROM events ORDER BY id",null)){while(c.moveToNext())events.add(new RouteStore.Event(c.getString(0),c.getString(1),c.getDouble(2),c.getDouble(3),c.getLong(4),c.getFloat(5)));}
    }
    synchronized void clearRecording(){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("fixes",null,null);db.delete("events",null,null);state("recording","false");state("paused","false");db.setTransactionSuccessful();}finally{db.endTransaction();}}
}
