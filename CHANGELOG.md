# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.1] - 2026-11-05

### Added

- GitHub Actions OIDC publishing workflow for npm releases with provenance.
  No runtime changes.

## [1.1.0] - 2026-11-05

### Added

- `checkPermissions()` returns notification and exact-alarm permission state.
- `openExactAlarmSettings()` opens the Android "Alarms & reminders" settings screen.
- A dedicated monochrome notification icon instead of the app launcher icon.
- A notification content intent, so tapping an alarm opens the app.
- "Missing notification permission", "exact alarm permission" and "storage write"
  error codes.

### Changed

- Requires Cordova Android 12+ and Android 7.0 (API 24) or newer.
- `androidx.core:core` is pinned to 1.10.1 so Cordova Android 12 (compileSdk 33)
  builds without requiring compileSdk 34.
- `schedule()` now rejects missing/invalid ids, missing trigger times and trigger
  times in the past instead of silently defaulting.
- Exact alarms are no longer scheduled as silent inexact alarms when the exact
  alarm permission is missing; the call fails with
  `EXACT_ALARM_PERMISSION_REQUIRED`.
- The plugin no longer requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` or opens
  battery optimization settings automatically.

### Fixed

- Alarm records are removed after an alarm fires and when stale records are found
  during boot.
- Multiple `schedule()` calls made while the notification permission dialog is
  open no longer overwrite each other's callbacks.
- Alarm state writes use `commit()` so reboot recovery is more reliable.
- `MY_PACKAGE_REPLACED` is handled so alarms can be restored after an app update.
- Notification ids are namespaced to avoid colliding with host-app notifications.
- Android 7.x notifications now request sound/vibration instead of relying on an
  API 26+ notification channel.

### Removed

- Automatic battery-optimization exemption requests and the associated manifest
  permission.

## [1.0.1] - 2026-05-07

### Changed

- Published to npm as `cordova-notify-plugin@1.0.1`.

## [1.0.0] - 2026-04-16

### Added

- Initial Android implementation: schedule/cancel local alarm notifications,
  Promise and callback APIs, reboot persistence and TypeScript definitions.
