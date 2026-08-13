package com.ritmo.mobile;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity {
    private static final String CHANNEL = "ritmo/native";

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);

        new MethodChannel(
                flutterEngine.getDartExecutor().getBinaryMessenger(),
                CHANNEL
        ).setMethodCallHandler((call, result) -> {
            try {
                switch (call.method) {
                    case "loadData": {
                        String raw = getSharedPreferences("ritmo_prefs", MODE_PRIVATE)
                                .getString("ritmo_data", null);
                        result.success(raw);
                        break;
                    }
                    case "saveData": {
                        String raw = call.argument("raw");
                        if (raw == null) raw = "";
                        getSharedPreferences("ritmo_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("ritmo_data", raw)
                                .apply();
                        try {
                            RitmoWidgetProvider.updateAll(this);
                        } catch (Throwable ignored) { }
                        result.success(null);
                        break;
                    }
                    case "loadLegacySettings": {
                        SharedPreferences ui = getSharedPreferences("ritmo_ui", MODE_PRIVATE);
                        SharedPreferences planner = getSharedPreferences("ritmo_planner_settings", MODE_PRIVATE);
                        Map<String, Object> map = new HashMap<>();
                        map.put("theme_mode", ui.getString("theme_mode",
                                ui.contains("dark")
                                        ? (ui.getBoolean("dark", true) ? "dark" : "light")
                                        : "dark"));
                        map.put("user_name", ui.getString("user_name", ""));
                        map.put("reduce_motion", ui.getBoolean("reduce_motion", false));
                        map.put("haptics", ui.getBoolean("haptics", true));
                        map.put("autoReplanOverdue", planner.getBoolean("autoReplanOverdue", false));
                        map.put("plannerStartHour", planner.getInt("startHour", 8));
                        map.put("plannerEndHour", planner.getInt("endHour", 22));
                        map.put("plannerCapacityMinutes", planner.getInt("capacityMinutes", 360));
                        map.put("plannerIncludeWeekend", planner.getBoolean("includeWeekend", true));
                        result.success(map);
                        break;
                    }
                    case "requestNotificationPermission": {
                        if (Build.VERSION.SDK_INT >= 33 &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                        != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(
                                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                    7301
                            );
                        }
                        result.success(null);
                        break;
                    }
                    case "syncReminders": {
                        Store store = new Store(this);
                        ReminderScheduler.rescheduleAll(this, store);
                        RoutineReminderScheduler.rescheduleAll(this, store);
                        result.success(null);
                        break;
                    }
                    case "loadFocusState": {
                        SharedPreferences p = getSharedPreferences("ritmo_focus", MODE_PRIVATE);
                        Map<String, Object> map = new HashMap<>();
                        map.put("active", p.getBoolean("active", false));
                        map.put("running", p.getBoolean("running", false));
                        map.put("taskId", p.getLong("taskId", 0L));
                        map.put("title", p.getString("title", "Foco livre"));
                        map.put("mode", p.getString("mode", "Pomodoro 25"));
                        map.put("plannedMinutes", p.getInt("plannedMinutes", 25));
                        map.put("startedAt", p.getLong("startedAt", 0L));
                        map.put("endAt", p.getLong("endAt", 0L));
                        map.put("remainingSeconds", p.getInt("remainingSeconds", 0));
                        result.success(map);
                        break;
                    }
                    case "startFocus": {
                        Number taskId = call.argument("taskId");
                        Number planned = call.argument("plannedMinutes");
                        Number startedAt = call.argument("startedAt");
                        Number endAt = call.argument("endAt");
                        String title = call.argument("title");
                        String mode = call.argument("mode");
                        getSharedPreferences("ritmo_focus", MODE_PRIVATE)
                                .edit()
                                .putBoolean("active", true)
                                .putBoolean("running", true)
                                .putLong("taskId", taskId == null ? 0L : taskId.longValue())
                                .putString("title", title == null ? "Foco livre" : title)
                                .putString("mode", mode == null ? "Pomodoro 25" : mode)
                                .putInt("plannedMinutes", planned == null ? 25 : planned.intValue())
                                .putLong("startedAt", startedAt == null ? System.currentTimeMillis() : startedAt.longValue())
                                .putLong("endAt", endAt == null ? 0L : endAt.longValue())
                                .putInt("remainingSeconds", 0)
                                .apply();
                        FocusTimerService.start(this);
                        result.success(null);
                        break;
                    }
                    case "pauseFocus": {
                        Number taskId = call.argument("taskId");
                        Number planned = call.argument("plannedMinutes");
                        Number startedAt = call.argument("startedAt");
                        Number remaining = call.argument("remainingSeconds");
                        String title = call.argument("title");
                        String mode = call.argument("mode");
                        getSharedPreferences("ritmo_focus", MODE_PRIVATE)
                                .edit()
                                .putBoolean("active", true)
                                .putBoolean("running", false)
                                .putLong("taskId", taskId == null ? 0L : taskId.longValue())
                                .putString("title", title == null ? "Foco livre" : title)
                                .putString("mode", mode == null ? "Pomodoro 25" : mode)
                                .putInt("plannedMinutes", planned == null ? 25 : planned.intValue())
                                .putLong("startedAt", startedAt == null ? System.currentTimeMillis() : startedAt.longValue())
                                .putLong("endAt", 0L)
                                .putInt("remainingSeconds", remaining == null ? 0 : remaining.intValue())
                                .apply();
                        FocusTimerService.stop(this);
                        result.success(null);
                        break;
                    }
                    case "stopFocus": {
                        getSharedPreferences("ritmo_focus", MODE_PRIVATE)
                                .edit()
                                .clear()
                                .apply();
                        FocusTimerService.stop(this);
                        result.success(null);
                        break;
                    }
                    default:
                        result.notImplemented();
                }
            } catch (Throwable error) {
                result.error("RITMO_NATIVE", error.getMessage(), null);
            }
        });
    }
}
