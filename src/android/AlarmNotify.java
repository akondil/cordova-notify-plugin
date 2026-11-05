package com.solocode.alarmnotify;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class AlarmNotify extends CordovaPlugin {

  private static final int REQ_POST_NOTIF = 7101;

  private static final String PREFS = "AlarmNotifyPrefs";
  private static final String KEY_IDS = "alarm_ids";
  private static final String KEY_ALARM_PREFIX = "alarm_";

  private volatile CallbackContext pendingCallback;
  private volatile JSONArray pendingArgs;

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    if ("schedule".equals(action)) {
      schedule(args, callbackContext);
      return true;
    }
    if ("cancel".equals(action)) {
      cancel(args, callbackContext);
      return true;
    }
    if ("checkPermissions".equals(action)) {
      callbackContext.success(permissionsJson());
      return true;
    }
    if ("openExactAlarmSettings".equals(action)) {
      openExactAlarmSettings(callbackContext);
      return true;
    }
    return false;
  }

  private void schedule(JSONArray args, CallbackContext callbackContext) {
    if (args == null) {
      callbackContext.error("INVALID_ARGUMENT");
      return;
    }

    if (!hasPostNotificationsPermission()) {
      if (pendingCallback != null) {
        callbackContext.error("PERMISSION_REQUEST_IN_PROGRESS");
        return;
      }
      pendingCallback = callbackContext;
      pendingArgs = args;
      PermissionHelper.requestPermission(this, REQ_POST_NOTIF, Manifest.permission.POST_NOTIFICATIONS);
      return;
    }

    if (!canScheduleExactAlarms()) {
      callbackContext.error("EXACT_ALARM_PERMISSION_REQUIRED");
      return;
    }

    scheduleInternal(args, callbackContext);
  }

  private void cancel(JSONArray args, CallbackContext callbackContext) {
    if (args == null) {
      callbackContext.error("INVALID_ARGUMENT");
      return;
    }

    int id = args.optInt(0, -1);
    if (id <= 0) {
      callbackContext.error("INVALID_ID");
      return;
    }

    Context ctx = cordova.getActivity().getApplicationContext();
    try {
      // Persist the cancellation before touching AlarmManager. If the process
      // dies in between, reboot recovery must not restore a canceled alarm.
      removeAlarm(ctx, id);
      cancelAlarm(ctx, id);
      callbackContext.success("cancelled " + id);
    } catch (Exception e) {
      callbackContext.error(errorMessage(e, "CANCEL_FAILED"));
    }
  }

  private JSONObject permissionsJson() throws JSONException {
    JSONObject result = new JSONObject();
    result.put("notifications", hasPostNotificationsPermission());
    result.put("exactAlarms", canScheduleExactAlarms());
    return result;
  }

  private void openExactAlarmSettings(CallbackContext callbackContext) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      callbackContext.success("not_required");
      return;
    }

    try {
      Intent intent = new Intent(
          Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
          Uri.parse("package:" + cordova.getActivity().getPackageName()));
      cordova.getActivity().startActivity(intent);
      callbackContext.success("opened");
    } catch (Exception e) {
      callbackContext.error("EXACT_ALARM_SETTINGS_UNAVAILABLE");
    }
  }

  private boolean hasPostNotificationsPermission() {
    if (Build.VERSION.SDK_INT < 33) {
      return true;
    }
    return cordova.getActivity().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED;
  }

  private boolean canScheduleExactAlarms() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      return true;
    }
    AlarmManager am = (AlarmManager) cordova.getActivity().getSystemService(Context.ALARM_SERVICE);
    return am != null && am.canScheduleExactAlarms();
  }

  @Override
  public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode != REQ_POST_NOTIF) {
      return;
    }

    CallbackContext callback = pendingCallback;
    JSONArray args = pendingArgs;
    pendingCallback = null;
    pendingArgs = null;

    if (callback == null) {
      return;
    }
    if (args == null) {
      callback.error("INVALID_ARGUMENT");
      return;
    }
    if (grantResults == null || grantResults.length == 0
        || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
      callback.error("POST_NOTIFICATIONS_DENIED");
      return;
    }
    if (!canScheduleExactAlarms()) {
      callback.error("EXACT_ALARM_PERMISSION_REQUIRED");
      return;
    }

    scheduleInternal(args, callback);
  }

  @Override
  public void onReset() {
    CallbackContext callback = pendingCallback;
    pendingCallback = null;
    pendingArgs = null;
    if (callback != null) {
      try {
        callback.error("PLUGIN_RESET");
      } catch (Exception ignored) {
        // The WebView may already be gone during a reset.
      }
    }
  }

  private void scheduleInternal(JSONArray args, CallbackContext callback) {
    if (callback == null) {
      return;
    }
    if (args == null) {
      callback.error("INVALID_ARGUMENT");
      return;
    }

    int id = args.optInt(0, -1);
    String title = optString(args, 1, "Alarm");
    String text = optString(args, 2, "");
    long atMillis = args.optLong(3, -1L);

    if (id <= 0) {
      callback.error("INVALID_ID");
      return;
    }
    if (atMillis <= System.currentTimeMillis()) {
      callback.error("INVALID_TRIGGER_TIME");
      return;
    }

    Context ctx = cordova.getActivity().getApplicationContext();
    try {
      scheduleAlarm(ctx, id, title, text, atMillis);
      try {
        saveAlarm(ctx, id, title, text, atMillis);
      } catch (Exception e) {
        // Do not leave an alarm behind that reboot recovery cannot see.
        cancelAlarm(ctx, id);
        throw e;
      }
      callback.success("scheduled " + id + " at " + atMillis);
    } catch (SecurityException e) {
      callback.error("EXACT_ALARM_PERMISSION_REQUIRED");
    } catch (Exception e) {
      callback.error(errorMessage(e, "SCHEDULE_FAILED"));
    }
  }

  private static String errorMessage(Exception e, String fallback) {
    String message = e.getMessage();
    return message != null && message.length() > 0 ? message : fallback;
  }

  private static String optString(JSONArray args, int index, String fallback) {
    return args.isNull(index) ? fallback : args.optString(index, fallback);
  }

  // --------- STATIC HELPERS (BootReceiver and AlarmReceiver also use these) ---------

  public static void scheduleAlarm(Context ctx, int id, String title, String text, long atMillis) {
    if (atMillis <= System.currentTimeMillis()) {
      throw new IllegalArgumentException("INVALID_TRIGGER_TIME");
    }

    AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    if (am == null) {
      throw new IllegalStateException("ALARM_SERVICE_UNAVAILABLE");
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
      throw new SecurityException("EXACT_ALARM_PERMISSION_REQUIRED");
    }

    Intent intent = new Intent(ctx, AlarmReceiver.class);
    intent.putExtra("id", id);
    intent.putExtra("title", title);
    intent.putExtra("text", text);

    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= 23) {
      flags |= PendingIntent.FLAG_IMMUTABLE;
    }
    PendingIntent pi = PendingIntent.getBroadcast(ctx, id, intent, flags);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      am.setExact(AlarmManager.RTC_WAKEUP, atMillis, pi);
    } else {
      am.set(AlarmManager.RTC_WAKEUP, atMillis, pi);
    }
  }

  public static void cancelAlarm(Context ctx, int id) {
    AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    if (am == null) {
      return;
    }

    Intent intent = new Intent(ctx, AlarmReceiver.class);
    int flags = PendingIntent.FLAG_NO_CREATE;
    if (Build.VERSION.SDK_INT >= 23) {
      flags |= PendingIntent.FLAG_IMMUTABLE;
    }
    PendingIntent pi = PendingIntent.getBroadcast(ctx, id, intent, flags);
    if (pi != null) {
      am.cancel(pi);
      pi.cancel();
    }
  }

  private static void saveAlarm(Context ctx, int id, String title, String text, long atMillis) throws JSONException {
    SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    Set<String> ids = new HashSet<>(sp.getStringSet(KEY_IDS, new HashSet<String>()));
    ids.add(String.valueOf(id));

    JSONObject o = new JSONObject();
    o.put("id", id);
    o.put("title", title != null ? title : "Alarm");
    o.put("text", text != null ? text : "");
    o.put("atMillis", atMillis);

    boolean saved = sp.edit()
        .putStringSet(KEY_IDS, ids)
        .putString(KEY_ALARM_PREFIX + id, o.toString())
        .commit();
    if (!saved) {
      throw new IllegalStateException("PREFERENCES_WRITE_FAILED");
    }
  }

  public static void removeAlarm(Context ctx, int id) {
    SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    Set<String> ids = new HashSet<>(sp.getStringSet(KEY_IDS, new HashSet<String>()));
    ids.remove(String.valueOf(id));

    boolean saved = sp.edit()
        .putStringSet(KEY_IDS, ids)
        .remove(KEY_ALARM_PREFIX + id)
        .commit();
    if (!saved) {
      throw new IllegalStateException("PREFERENCES_WRITE_FAILED");
    }
  }

  public static Set<String> getSavedAlarmIds(Context ctx) {
    SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    return new HashSet<>(sp.getStringSet(KEY_IDS, new HashSet<String>()));
  }

  public static JSONObject getSavedAlarm(Context ctx, String idStr) {
    SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    String raw = sp.getString(KEY_ALARM_PREFIX + idStr, null);
    if (raw == null) {
      return null;
    }
    try {
      return new JSONObject(raw);
    } catch (Exception e) {
      return null;
    }
  }
}
