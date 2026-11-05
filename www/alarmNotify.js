var exec = require('cordova/exec');

var SERVICE = 'AlarmNotify';

function isFiniteNumber(value) {
  return typeof value === 'number' && isFinite(value);
}

function isPositiveInteger(value) {
  return isFiniteNumber(value) &&
    value > 0 &&
    value <= 2147483647 &&
    Math.floor(value) === value;
}

function isPlainObject(value) {
  return value !== null &&
    typeof value === 'object' &&
    !Array.isArray(value) &&
    !(value instanceof Date);
}

function normalizeAt(at) {
  if (at instanceof Date) {
    return at.getTime();
  }
  return at;
}

function validateSchedule(id, title, text, at) {
  if (!isPositiveInteger(id)) {
    return 'INVALID_ID: id must be a positive integer';
  }

  var atMillis = normalizeAt(at);
  if (!isFiniteNumber(atMillis)) {
    return 'INVALID_TRIGGER_TIME: at must be a Date or a Unix millisecond timestamp';
  }
  if (atMillis <= Date.now()) {
    return 'INVALID_TRIGGER_TIME: at must be in the future';
  }

  if (title !== null && title !== undefined && typeof title !== 'string') {
    return 'INVALID_TITLE: title must be a string';
  }
  if (text !== null && text !== undefined && typeof text !== 'string') {
    return 'INVALID_TEXT: text must be a string';
  }

  return {
    id: id,
    title: title === null || title === undefined ? 'Alarm' : title,
    text: text === null || text === undefined ? '' : text,
    at: Math.floor(atMillis)
  };
}

function validateId(id) {
  return isPositiveInteger(id) ? null : 'INVALID_ID: id must be a positive integer';
}

function invoke(action, args, success, error) {
  if (typeof success === 'function' || typeof error === 'function') {
    exec(success, error, SERVICE, action, args);
    return;
  }
  return new Promise(function (resolve, reject) {
    exec(resolve, reject, SERVICE, action, args);
  });
}

/**
 * Schedule a local alarm notification.
 *
 * Modern usage (returns Promise):
 *   alarmNotify.schedule({ id: 1, title: 'Meeting', text: 'Standup', at: new Date(Date.now() + 60000) })
 *
 * Legacy usage (callbacks):
 *   alarmNotify.schedule(1, 'Meeting', 'Standup', Date.now() + 60000, onSuccess, onError)
 *
 * Legacy usage without callbacks also returns a Promise.
 *
 * @param {Object|number} optionsOrId  Options object or alarm id (legacy).
 * @param {string}        [title]      Notification title (legacy).
 * @param {string}        [text]       Notification body (legacy).
 * @param {number|Date}   [atMillis]   Trigger time as Unix ms timestamp or Date (legacy).
 * @param {Function}      [success]    Success callback (legacy).
 * @param {Function}      [error]      Error callback (legacy).
 * @returns {Promise<string>|undefined}
 */
exports.schedule = function (optionsOrId, title, text, atMillis, success, error) {
  if (isPlainObject(optionsOrId)) {
    return new Promise(function (resolve, reject) {
      var result = validateSchedule(
        optionsOrId.id,
        optionsOrId.title,
        optionsOrId.text,
        optionsOrId.at
      );
      if (typeof result === 'string') {
        reject(result);
        return;
      }
      exec(resolve, reject, SERVICE, 'schedule', [result.id, result.title, result.text, result.at]);
    });
  }

  var result = validateSchedule(optionsOrId, title, text, atMillis);

  if (typeof success !== 'function' && typeof error !== 'function') {
    return new Promise(function (resolve, reject) {
      if (typeof result === 'string') {
        reject(result);
        return;
      }
      exec(resolve, reject, SERVICE, 'schedule', [result.id, result.title, result.text, result.at]);
    });
  }

  if (typeof result === 'string') {
    if (typeof error === 'function') {
      error(result);
      return;
    }
    throw new TypeError(result);
  }

  exec(success, error, SERVICE, 'schedule', [result.id, result.title, result.text, result.at]);
};

/**
 * Cancel a previously scheduled alarm.
 *
 * @param {number}   id        Alarm id to cancel.
 * @param {Function} [success] Success callback (legacy).
 * @param {Function} [error]   Error callback (legacy).
 * @returns {Promise<string>|undefined}
 */
exports.cancel = function (id, success, error) {
  var validationError = validateId(id);

  if (typeof success !== 'function' && typeof error !== 'function') {
    return new Promise(function (resolve, reject) {
      if (validationError) {
        reject(validationError);
        return;
      }
      exec(resolve, reject, SERVICE, 'cancel', [id]);
    });
  }

  if (validationError) {
    if (typeof error === 'function') {
      error(validationError);
      return;
    }
    throw new TypeError(validationError);
  }

  exec(success, error, SERVICE, 'cancel', [id]);
};

/**
 * Read the current notification and exact-alarm permission state.
 *
 * @param {Function} [success] Receives { notifications: boolean, exactAlarms: boolean }.
 * @param {Function} [error]   Error callback (legacy).
 * @returns {Promise<{notifications: boolean, exactAlarms: boolean}>|undefined}
 */
exports.checkPermissions = function (success, error) {
  return invoke('checkPermissions', [], success, error);
};

/**
 * Open the Android "Alarms & reminders" settings screen.
 * Schedule again after the user returns and grants the permission.
 *
 * @param {Function} [success] Receives "opened" or "not_required".
 * @param {Function} [error]   Error callback (legacy).
 * @returns {Promise<string>|undefined}
 */
exports.openExactAlarmSettings = function (success, error) {
  return invoke('openExactAlarmSettings', [], success, error);
};
