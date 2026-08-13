import 'package:flutter/services.dart';

class NativeBridge {
  static const _channel = MethodChannel('ritmo/native');

  static Future<String?> loadData() async {
    try {
      return await _channel.invokeMethod<String>('loadData');
    } on PlatformException {
      return null;
    } on MissingPluginException {
      return null;
    }
  }

  static Future<void> saveData(String raw) async {
    try {
      await _channel.invokeMethod<void>('saveData', {'raw': raw});
    } on PlatformException {
      // Flutter fallback storage remains available if the Android bridge fails.
    } on MissingPluginException {
      // Other platforms use Flutter storage only.
    }
  }

  static Future<Map<String, dynamic>> loadLegacySettings() async {
    try {
      final map =
          await _channel.invokeMapMethod<String, dynamic>('loadLegacySettings');
      return map ?? <String, dynamic>{};
    } on PlatformException {
      return <String, dynamic>{};
    } on MissingPluginException {
      return <String, dynamic>{};
    }
  }

  static Future<void> requestNotificationPermission() async {
    try {
      await _channel.invokeMethod<void>('requestNotificationPermission');
    } on PlatformException {
      // Permission errors are non-fatal and can be retried from Settings.
    } on MissingPluginException {
      // No-op outside Android.
    }
  }

  static Future<void> syncReminders() async {
    try {
      await _channel.invokeMethod<void>('syncReminders');
    } on PlatformException {
      // Reminder sync can retry on the next save or app start.
    } on MissingPluginException {
      // No-op outside Android.
    }
  }

  static Future<Map<String, dynamic>> loadFocusState() async {
    try {
      final map =
          await _channel.invokeMapMethod<String, dynamic>('loadFocusState');
      return map ?? <String, dynamic>{};
    } on PlatformException {
      return <String, dynamic>{};
    } on MissingPluginException {
      return <String, dynamic>{};
    }
  }

  static Future<void> startFocus({
    required int taskId,
    required String title,
    required String mode,
    required int plannedMinutes,
    required int startedAt,
    required int endAt,
  }) async {
    try {
      await _channel.invokeMethod<void>('startFocus', {
        'taskId': taskId,
        'title': title,
        'mode': mode,
        'plannedMinutes': plannedMinutes,
        'startedAt': startedAt,
        'endAt': endAt,
      });
    } on PlatformException {
      // Dart still keeps the session state if Android cannot start the service.
    } on MissingPluginException {
      // Non-Android platforms keep the timer in Flutter.
    }
  }

  static Future<void> pauseFocus({
    required int taskId,
    required String title,
    required String mode,
    required int plannedMinutes,
    required int startedAt,
    required int remainingSeconds,
  }) async {
    try {
      await _channel.invokeMethod<void>('pauseFocus', {
        'taskId': taskId,
        'title': title,
        'mode': mode,
        'plannedMinutes': plannedMinutes,
        'startedAt': startedAt,
        'remainingSeconds': remainingSeconds,
      });
    } on PlatformException {
      // Keep the paused state in Dart even if Android persistence fails.
    } on MissingPluginException {
      // No-op outside Android.
    }
  }

  static Future<void> stopFocus() async {
    try {
      await _channel.invokeMethod<void>('stopFocus');
    } on PlatformException {
      // Safe to ignore if the native service is already stopped.
    } on MissingPluginException {
      // No-op outside Android.
    }
  }
}
