package com.routix.app;

import android.app.NotificationManager;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import java.util.ArrayList;

/** Optional notification enhancements when a platform exposes the required API. */
final class NotificationCapsuleCompat {
    private static final String SCENE="TRAIN";
    private NotificationCapsuleCompat(){}

    static boolean supportsScene() {
        try {
            NotificationManager.class.getMethod("setSuperXInfosSceneList",
                java.util.List.class,java.util.List.class,java.util.List.class,java.util.List.class);
            return true;
        } catch (NoSuchMethodException | SecurityException ignored) {
            return false;
        }
    }

    static void registerScene(Context context){
        if(!supportsScene())return;
        try{
            NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            java.lang.reflect.Method method=NotificationManager.class.getMethod("setSuperXInfosSceneList",java.util.List.class,java.util.List.class,java.util.List.class,java.util.List.class);
            method.invoke(nm,new ArrayList<>(java.util.Collections.singletonList(SCENE)),new ArrayList<>(java.util.Collections.singletonList("true")),new ArrayList<>(java.util.Collections.singletonList(context.getPackageName())),new ArrayList<>(java.util.Collections.singletonList("true")));
        }catch(Throwable ignored){}
    }

    static Bundle guidance(Context context,String title,String content,int progress){
        Bundle out=new Bundle();int p=Math.max(0,Math.min(100,progress));Icon icon=Icon.createWithResource(context,R.drawable.ic_tracking);
        out.putInt("notification.superx.operation",0);out.putBoolean("notification.superx.showNotify",true);out.putInt("notification.superx.template",2);out.putString("notification.superx.scene",SCENE);out.putInt("notification.superx.changedRecord",0);
        Bundle base=new Bundle();base.putCharSequence("notification.superx.baseInfos.title",title);base.putCharSequence("notification.superx.baseInfos.content",content);base.putParcelable("notification.superx.baseInfos.icon",icon);out.putBundle("notification.superx.baseInfos",base);
        Bundle capsule=new Bundle();capsule.putInt("notification.superx.capsule.state",1);capsule.putCharSequence("notification.superx.capsule.content",title);capsule.putParcelable("notification.superx.capsule.icon",icon);out.putBundle("notification.superx.capsule",capsule);
        Bundle infos=new Bundle();infos.putInt("notification.superx.infos.progress",p);infos.putParcelableArrayList("notification.superx.infos.nodeIcon",new ArrayList<>(java.util.Arrays.asList(icon,icon)));infos.putParcelable("notification.superx.infos.indicatorIcon",icon);infos.putInt("notification.superx.infos.indicatorLoc",1);out.putBundle("notification.superx.infos",infos);
        Bundle shortInfos=new Bundle();shortInfos.putString("notification.superx.shortInfos.describeShort",title);shortInfos.putString("notification.superx.shortInfos.coreInfoShort",content);shortInfos.putParcelable("notification.superx.shortInfos.image",icon);out.putBundle("notification.superx.shortInfos",shortInfos);
        Bundle island=new Bundle();island.putInt("island.superx.leftTemplate",1);island.putInt("island.superx.rightTemplate",2);
        Bundle left=new Bundle();left.putString("island.superx.leftInfo.content",title);left.putParcelable("island.superx.leftInfo.icon",icon);island.putBundle("island.superx.leftInfo",left);
        Bundle right=new Bundle();right.putInt("island.superx.rightInfo.progressValue",p);right.putInt("island.superx.rightInfo.progressState",0);island.putBundle("island.superx.rightInfo",right);out.putBundle("notification.superx.island",island);
        return out;
    }

    static Bundle end(){Bundle b=new Bundle();b.putInt("notification.superx.operation",2);return b;}
}
