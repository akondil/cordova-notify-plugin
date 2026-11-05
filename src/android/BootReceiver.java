package com.solocode.alarmnotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;

import java.util.Set;

public class BootReceiver extends BroadcastReceiver {

  private static final String TAG = "AlarmNotify";

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) {
      return;
    }

    String action = intent.getAction();
    if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
        && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
      return;
    }

    Set<String> ids = AlarmNotify.getSavedAlarmIds(context);
    long now = System.currentTimeMillis();

    for (String idStr : ids) {
      try {
        JSONObject stored = AlarmNotify.getSavedAlarm(context, idStr);
        if (stored == null) {
          removeStored(context, idStr, -1);
          continue;
        }

        int id = stored.optInt("id", -1);
        String title = stored.optString("title", "Alarm");
        String text = stored.optString("text", "");
        long atMillis = stored.optLong("atMillis", 0L);

        if (id <= 0 || atMillis <= now) {
          removeStored(context, idStr, id);
          continue;
        }

        AlarmNotify.scheduleAlarm(context, id, title, text, atMillis);
      } catch (Exception e) {
        // Keep the record: the exact-alarm permission may be granted later.
        Log.w(TAG, "Could not restore alarm " + idStr, e);
      }
    }
  }

  private static void removeStored(Context context, String idStr, int id) {
    try {
      AlarmNotify.removeAlarm(context, id > 0 ? id : Integer.parseInt(idStr));
      if (id > 0 && !String.valueOf(id).equals(idStr)) {
        // Repair a record whose JSON id and key disagree.
        AlarmNotify.removeAlarm(context, Integer.parseInt(idStr));
      }
    } catch (Exception e) {
      Log.w(TAG, "Could not remove stale alarm " + idStr, e);
    }
  }
}
