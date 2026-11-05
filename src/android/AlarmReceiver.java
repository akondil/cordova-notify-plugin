package com.solocode.alarmnotify;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {

  private static final String TAG = "AlarmNotify";
  private static final String CHANNEL_ID = "alarm_notify_channel";
  private static final String CHANNEL_NAME = "Alarm notifications";

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) {
      return;
    }

    int id = intent.getIntExtra("id", -1);
    if (id <= 0) {
      return;
    }

    String title = intent.getStringExtra("title");
    String text = intent.getStringExtra("text");

    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm == null) {
      return;
    }

    ensureChannel(nm);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(resolveSmallIcon(context))
        .setContentTitle(title != null ? title : "Alarm")
        .setContentText(text != null ? text : "")
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL);

    if (text != null && text.length() > 0) {
      builder.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
    }

    PendingIntent contentIntent = createContentIntent(context, id);
    if (contentIntent != null) {
      builder.setContentIntent(contentIntent);
    }

    try {
      nm.notify(notificationId(id), builder.build());
      AlarmNotify.removeAlarm(context, id);
    } catch (Exception e) {
      Log.w(TAG, "Could not show alarm notification for id " + id, e);
    }
  }

  private static void ensureChannel(NotificationManager nm) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }
    if (nm.getNotificationChannel(CHANNEL_ID) != null) {
      return;
    }

    NotificationChannel channel = new NotificationChannel(
        CHANNEL_ID,
        CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH);
    channel.setDescription("Scheduled alarm notifications");
    channel.enableVibration(true);
    nm.createNotificationChannel(channel);
  }

  private static int resolveSmallIcon(Context context) {
    int icon = context.getResources().getIdentifier(
        "alarm_notify_icon", "drawable", context.getPackageName());
    return icon != 0 ? icon : context.getApplicationInfo().icon;
  }

  private static PendingIntent createContentIntent(Context context, int id) {
    Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    if (launch == null) {
      return null;
    }

    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    launch.putExtra("alarmNotifyId", id);

    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= 23) {
      flags |= PendingIntent.FLAG_IMMUTABLE;
    }
    return PendingIntent.getActivity(context, notificationId(id), launch, flags);
  }

  private static int notificationId(int alarmId) {
    return ("com.solocode.alarmnotify:" + alarmId).hashCode() & 0x7fffffff;
  }
}
