export interface AlarmOptions {
  /** Unique alarm identifier. Must be a positive integer. Reusing an id overwrites the previous alarm. */
  id: number;
  /** Notification title shown in the status bar. Defaults to "Alarm". */
  title?: string;
  /** Notification body text. Defaults to an empty string. */
  text?: string;
  /** Trigger time as a Date object or Unix millisecond timestamp. Must be in the future. */
  at: Date | number;
}

export interface AlarmPermissionState {
  /** Whether POST_NOTIFICATIONS is granted (always true below Android 13). */
  notifications: boolean;
  /** Whether exact alarms are allowed (always true below Android 12). */
  exactAlarms: boolean;
}

export interface AlarmNotifyPlugin {
  /**
   * Schedule a local alarm notification.
   *
   * @example
   * await cordova.plugins.alarmNotify.schedule({
   *   id: 1,
   *   title: 'Meeting',
   *   text: 'Team standup',
   *   at: new Date(Date.now() + 60000),
   * });
   */
  schedule(options: AlarmOptions): Promise<string>;
  schedule(
    id: number,
    title: string,
    text: string,
    at: Date | number
  ): Promise<string>;
  schedule(
    id: number,
    title: string,
    text: string,
    at: Date | number,
    success: (msg: string) => void,
    error: (err: string) => void
  ): void;

  /**
   * Cancel a previously scheduled alarm.
   *
   * @example
   * await cordova.plugins.alarmNotify.cancel(1);
   */
  cancel(id: number): Promise<string>;
  cancel(
    id: number,
    success: (msg: string) => void,
    error: (err: string) => void
  ): void;

  /**
   * Read the current notification and exact-alarm permission state.
   *
   * @example
   * const state = await cordova.plugins.alarmNotify.checkPermissions();
   * if (!state.exactAlarms) {
   *   await cordova.plugins.alarmNotify.openExactAlarmSettings();
   * }
   */
  checkPermissions(): Promise<AlarmPermissionState>;
  checkPermissions(
    success: (state: AlarmPermissionState) => void,
    error: (err: string) => void
  ): void;

  /**
   * Open the Android "Alarms & reminders" settings screen.
   * Schedule again after the user returns and grants the permission.
   */
  openExactAlarmSettings(): Promise<string>;
  openExactAlarmSettings(
    success: (msg: string) => void,
    error: (err: string) => void
  ): void;
}

declare global {
  interface CordovaPlugins {
    alarmNotify: AlarmNotifyPlugin;
  }
}
