# cordova-notify-plugin

[![npm version](https://badge.fury.io/js/cordova-notify-plugin.svg)](https://www.npmjs.com/package/cordova-notify-plugin)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A Cordova plugin to schedule and cancel local alarm notifications on Android.
Alarms work while the app is closed and are restored after a device reboot.

## Requirements

| Component | Version |
|---|---|
| Cordova CLI | 12.0.0 or newer |
| cordova-android | 12.0.0 or newer |
| Android | 7.0+ (API 24+) |
| Plugin | 1.1.x |

The plugin is Android-only. It uses `androidx.core:core:1.10.1`, which is
compatible with Cordova Android 12's compileSdk 33 and newer Cordova Android
releases.

## Installation

From npm:

```bash
cordova plugin add cordova-notify-plugin
```

From GitHub:

```bash
cordova plugin add https://github.com/akondil/cordova-notify-plugin.git
```

From a local checkout:

```bash
cordova plugin add ../cordova-notify-plugin
```

## Permissions

The plugin adds these permissions to `AndroidManifest.xml`:

| Permission | Purpose |
|---|---|
| `POST_NOTIFICATIONS` | Show the notification (Android 13+) |
| `SCHEDULE_EXACT_ALARM` | Schedule exact-time alarms (Android 12+) |
| `RECEIVE_BOOT_COMPLETED` | Restore future alarms after a reboot |
| `VIBRATE` | Vibrate for alarm notifications |

The plugin does **not** request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and does
not open battery-optimization settings automatically. `setExactAndAllowWhileIdle`
is used to deliver alarms in Doze mode. If your app has a Play-policy-compliant
reason to request a battery exemption, handle that in the host app.

`SCHEDULE_EXACT_ALARM` is a user-granted special permission. On Android 12+,
`schedule()` fails with `EXACT_ALARM_PERMISSION_REQUIRED` when it is not
granted. Use `checkPermissions()` and `openExactAlarmSettings()` to guide the
user, then call `schedule()` again.

## Usage

Run plugin calls after Cordova's `deviceready` event:

```javascript
document.addEventListener('deviceready', function () {
  var alarmNotify = cordova.plugins.alarmNotify;

  // ...
}, false);
```

### Schedule an alarm

```javascript
var alarmNotify = cordova.plugins.alarmNotify;

alarmNotify.schedule({
  id: 1,
  title: 'Meeting',
  text: 'Team standup starts now',
  at: Date.now() + 5 * 60 * 1000
})
.then(function (message) {
  console.log('Scheduled:', message);
})
.catch(function (error) {
  console.error('Error:', error);
});
```

`at` accepts a `Date` or a Unix millisecond timestamp and must be in the future:

```javascript
alarmNotify.schedule({
  id: 2,
  title: 'Reminder',
  text: 'Check emails',
  at: new Date(Date.now() + 60 * 1000)
});
```

The positional form also supports callbacks:

```javascript
alarmNotify.schedule(
  1,                      // id
  'Meeting',              // title
  'Team standup',         // text
  Date.now() + 60000,     // trigger time (ms)
  function (message) { console.log(message); },
  function (error) { console.error(error); }
);
```

Positional calls without callbacks return a Promise:

```javascript
await alarmNotify.schedule(1, 'Meeting', 'Team standup', Date.now() + 60000);
```

### Cancel an alarm

```javascript
await alarmNotify.cancel(1);
```

Callback form:

```javascript
alarmNotify.cancel(
  1,
  function (message) { console.log(message); },
  function (error) { console.error(error); }
);
```

### Check permissions

```javascript
var state = await alarmNotify.checkPermissions();
// state.notifications  -> POST_NOTIFICATIONS (true below Android 13)
// state.exactAlarms    -> exact-alarm access (true below Android 12)

if (!state.exactAlarms) {
  await alarmNotify.openExactAlarmSettings();
  // When the user returns, retry schedule().
}
```

### Open exact-alarm settings

```javascript
await alarmNotify.openExactAlarmSettings();
// resolves with "opened" or "not_required"
```

## API

| Method | Returns | Description |
|---|---|---|
| `schedule(options)` | `Promise<string>` | Schedule an alarm from an options object |
| `schedule(id, title, text, at, success?, error?)` | `Promise<string>\|void` | Positional/callback form |
| `cancel(id)` | `Promise<string>` | Cancel an alarm |
| `cancel(id, success?, error?)` | `Promise<string>\|void` | Callback form |
| `checkPermissions()` | `Promise<{ notifications, exactAlarms }>` | Read permission state |
| `openExactAlarmSettings()` | `Promise<string>` | Open Android's exact-alarm settings screen |

`AlarmOptions`:

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | number | yes | Positive integer. Reusing an id overwrites that alarm |
| `title` | string | no | Notification title; defaults to `Alarm` |
| `text` | string | no | Notification body; defaults to an empty string |
| `at` | Date \| number | yes | Trigger time in the future (Date or Unix ms) |

## Error codes

Errors are returned as strings. Promise-based calls reject with the same string
that callback-based calls receive in their error callback.

| Code | Meaning |
|---|---|
| `INVALID_ID` | `id` is missing or is not a positive integer |
| `INVALID_TRIGGER_TIME` | `at` is missing, invalid or not in the future |
| `INVALID_TITLE` / `INVALID_TEXT` | Title/text is not a string |
| `INVALID_ARGUMENT` | Native call received malformed arguments |
| `POST_NOTIFICATIONS_DENIED` | The user denied the notification permission |
| `EXACT_ALARM_PERMISSION_REQUIRED` | Exact-alarm access is not granted |
| `PERMISSION_REQUEST_IN_PROGRESS` | Another permission request is already open |
| `EXACT_ALARM_SETTINGS_UNAVAILABLE` | The settings screen is not available on this device |
| `PREFERENCES_WRITE_FAILED` | The alarm could not be persisted |
| `ALARM_SERVICE_UNAVAILABLE` | Android's AlarmManager is unavailable |
| `SCHEDULE_FAILED` / `CANCEL_FAILED` | Unexpected native failure |
| `PLUGIN_RESET` | The WebView was reset while a permission request was pending |

## Behavior notes

- **Validation:** `id` must be a positive integer. `at` must be a `Date` or a
  Unix millisecond timestamp in the future. Missing or invalid values are
  rejected instead of silently defaulting.
- **Near-term alarms:** If a permission dialog or settings screen may appear,
  call `checkPermissions()` first. A permission flow can take longer than a
  trigger time only a few seconds away; `schedule()` rejects trigger times that
  have already passed.
- **Notification permission:** On Android 13+, `schedule()` asks for
  `POST_NOTIFICATIONS` if needed. Only one permission request can be pending at
  a time; a second call receives `PERMISSION_REQUEST_IN_PROGRESS`.
- **Exact alarms:** On Android 12+, exact alarms require
  `SCHEDULE_EXACT_ALARM`. If it is missing, `schedule()` rejects with
  `EXACT_ALARM_PERMISSION_REQUIRED` and does not silently schedule an inexact
  alarm. Call `openExactAlarmSettings()`, wait for the user to return, then
  retry `schedule()`.
- **Reboot:** Future alarms are restored after `BOOT_COMPLETED` and
  `MY_PACKAGE_REPLACED`. Alarms whose time has passed are removed instead of
  being restored.
- **App updates:** `MY_PACKAGE_REPLACED` restores future alarms after an app
  update.
- **Force-stop:** Android cancels alarms when the user force-stops the app. The
  boot receiver will not run again until the app is launched. This is an Android
  platform limitation, not a plugin setting.
- **Notification tap:** Tapping the notification opens the app. The alarm id is
  included as the `alarmNotifyId` extra on the launch intent.
- **Notification IDs:** Notification ids are namespaced so they do not collide
  with the host app's own notifications.
- **Storage:** Scheduled alarms are stored in `SharedPreferences` and removed
  after they fire or are canceled.
- **OEM battery savers:** Some manufacturers apply additional background
  restrictions. Exact alarms work in standard Doze mode, but OEM-specific
  settings may still delay delivery.

## TypeScript

Type definitions are included. Use a type-only import:

```typescript
import type { AlarmOptions } from 'cordova-notify-plugin';

const options: AlarmOptions = {
  id: 1,
  title: 'Alarm',
  text: 'Wake up!',
  at: Date.now() + 60_000
};

await cordova.plugins.alarmNotify.schedule(options);

const state = await cordova.plugins.alarmNotify.checkPermissions();
if (!state.exactAlarms) {
  await cordova.plugins.alarmNotify.openExactAlarmSettings();
}
```

## Development

The JavaScript wrapper has no runtime dependencies. Run its tests with Node's
built-in test runner:

```bash
npm test
```

To test the native side, install the plugin into a Cordova Android project and
run it on a device or emulator. At minimum, verify:

- scheduling and canceling an alarm;
- closing the app before the alarm fires;
- rebooting before and after the alarm fires;
- denying and then granting notification permission;
- denying and then granting exact-alarm permission;
- updating the app and checking that future alarms are restored.

## Contributing

Issues and pull requests are welcome:
<https://github.com/akondil/cordova-notify-plugin/issues>

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT. See [LICENSE](LICENSE).
