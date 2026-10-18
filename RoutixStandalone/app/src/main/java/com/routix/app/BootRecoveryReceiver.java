package com.routix.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/** Restores an interrupted GPS session only when Android permits background location. */
public final class BootRecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
        PendingResult pending=goAsync();
        new Thread(()->{
            SessionJournal journal=null;
            try{
                journal=new SessionJournal(context);
                boolean recording=Boolean.parseBoolean(journal.readState("recording","false"));
                boolean guiding=!journal.readState("guidance","").isEmpty();
                if(!recording&&!guiding)return;
                if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)
                        !=PackageManager.PERMISSION_GRANTED){showResumeNotification(context);return;}
                // Location is a while-in-use permission. Boot starts on recent Android
                // cannot legally recreate a location foreground service without background access.
                if(Build.VERSION.SDK_INT>=29 &&
                    ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        !=PackageManager.PERMISSION_GRANTED){showResumeNotification(context);return;}
                try{ContextCompat.startForegroundService(context,new Intent(context,TrackingService.class));}
                catch(RuntimeException e){DiagnosticLog.error("boot recovery start",e);showResumeNotification(context);}
            }catch(RuntimeException e){DiagnosticLog.error("boot recovery",e);}
            finally{if(journal!=null)journal.close();pending.finish();}
        },"routix-boot-recovery").start();
    }
    private static void showResumeNotification(Context context){
        if(Build.VERSION.SDK_INT>=33 &&
            ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)
                !=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        String id="recovery";
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(
            new NotificationChannel(id,context.getString(R.string.recovery_channel),NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(context,OnboardingActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent p=PendingIntent.getActivity(context,507,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new NotificationCompat.Builder(context,id)
            .setSmallIcon(R.drawable.ic_tracking)
            .setContentTitle(context.getString(R.string.recovery_title))
            .setContentText(context.getString(R.string.recovery_description))
            .setContentIntent(p).setAutoCancel(true).build();
        manager.notify(507,n);
    }
}
