'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const Module = require('module');

let calls = [];

const originalLoad = Module._load;
Module._load = function (request, parent, isMain) {
  if (request === 'cordova/exec') {
    return function (success, error, service, action, args) {
      calls.push({ success: success, error: error, service: service, action: action, args: args });
    };
  }
  return originalLoad.call(this, request, parent, isMain);
};

const alarmNotify = require('../www/alarmNotify.js');

test.after(function () {
  Module._load = originalLoad;
});

test.beforeEach(function () {
  calls = [];
});

async function rejectsWith(promise, pattern) {
  try {
    await promise;
    assert.fail('Expected the promise to reject');
  } catch (error) {
    assert.equal(typeof error, 'string');
    assert.match(error, pattern);
  }
}

test('schedule accepts an options object and resolves with the native result', async function () {
  const at = Date.now() + 60000;
  const promise = alarmNotify.schedule({ id: 7, title: 'Meeting', text: 'Standup', at: at });

  assert.equal(calls.length, 1);
  assert.equal(calls[0].service, 'AlarmNotify');
  assert.equal(calls[0].action, 'schedule');
  assert.deepEqual(calls[0].args, [7, 'Meeting', 'Standup', at]);

  calls[0].success('scheduled 7');
  assert.equal(await promise, 'scheduled 7');
});

test('schedule converts a Date trigger time to milliseconds', async function () {
  const date = new Date(Date.now() + 60000);
  const promise = alarmNotify.schedule({ id: 8, title: 'T', text: 'X', at: date });

  assert.deepEqual(calls[0].args, [8, 'T', 'X', date.getTime()]);
  calls[0].success('ok');
  await promise;
});

test('schedule applies defaults for optional title and text', async function () {
  const at = Date.now() + 60000;
  const promise = alarmNotify.schedule({ id: 9, at: at });

  assert.deepEqual(calls[0].args, [9, 'Alarm', '', at]);
  calls[0].success('ok');
  await promise;
});

test('positional schedule without callbacks returns a Promise', async function () {
  const at = Date.now() + 60000;
  const promise = alarmNotify.schedule(10, 'Title', 'Text', at);

  assert.equal(calls[0].action, 'schedule');
  assert.deepEqual(calls[0].args, [10, 'Title', 'Text', at]);

  calls[0].success('ok');
  assert.equal(await promise, 'ok');
});

test('legacy schedule with callbacks returns undefined', function () {
  const at = Date.now() + 60000;
  let successMessage = null;

  const result = alarmNotify.schedule(
    11,
    'Title',
    'Text',
    at,
    function (message) { successMessage = message; },
    function () {}
  );

  assert.equal(result, undefined);
  calls[0].success('legacy-ok');
  assert.equal(successMessage, 'legacy-ok');
});

test('schedule rejects an invalid id before calling native code', async function () {
  await rejectsWith(
    alarmNotify.schedule({ title: 'No id', at: Date.now() + 60000 }),
    /INVALID_ID/
  );
  assert.equal(calls.length, 0);
});

test('schedule rejects a trigger time in the past', async function () {
  await rejectsWith(
    alarmNotify.schedule({ id: 1, at: Date.now() - 1000 }),
    /INVALID_TRIGGER_TIME/
  );
  assert.equal(calls.length, 0);
});

test('schedule rejects a string trigger time', async function () {
  await rejectsWith(
    alarmNotify.schedule({ id: 1, at: '2030-01-01T00:00:00Z' }),
    /INVALID_TRIGGER_TIME/
  );
  assert.equal(calls.length, 0);
});

test('schedule rejects a non-string title', async function () {
  await rejectsWith(
    alarmNotify.schedule({ id: 1, title: 42, at: Date.now() + 60000 }),
    /INVALID_TITLE/
  );
  assert.equal(calls.length, 0);
});

test('cancel returns a Promise and validates the id', async function () {
  const promise = alarmNotify.cancel(3);
  assert.equal(calls[0].action, 'cancel');
  assert.deepEqual(calls[0].args, [3]);
  calls[0].success('cancelled 3');
  assert.equal(await promise, 'cancelled 3');
});

test('cancel rejects an invalid id', async function () {
  await rejectsWith(alarmNotify.cancel(0), /INVALID_ID/);
  assert.equal(calls.length, 0);
});

test('legacy cancel uses callbacks', function () {
  let successMessage = null;
  const result = alarmNotify.cancel(
    4,
    function (message) { successMessage = message; },
    function () {}
  );

  assert.equal(result, undefined);
  calls[0].success('cancelled 4');
  assert.equal(successMessage, 'cancelled 4');
});

test('checkPermissions resolves with the native permission state', async function () {
  const promise = alarmNotify.checkPermissions();
  assert.equal(calls[0].action, 'checkPermissions');

  const state = { notifications: true, exactAlarms: false };
  calls[0].success(state);
  assert.deepEqual(await promise, state);
});

test('openExactAlarmSettings resolves with the native result', async function () {
  const promise = alarmNotify.openExactAlarmSettings();
  assert.equal(calls[0].action, 'openExactAlarmSettings');

  calls[0].success('opened');
  assert.equal(await promise, 'opened');
});
