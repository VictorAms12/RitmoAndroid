package com.ritmo.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        boolean bootLike = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        boolean clockChanged = Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action);
        if (!bootLike && !clockChanged) return;

        try {
            Store store = new Store(context);
            ReminderScheduler.rescheduleAll(context, store);
            RoutineReminderScheduler.rescheduleAll(context, store);
        } catch (Throwable ignored) { }

        if (!bootLike) return;
        try {
            SharedPreferences focus =
                    context.getSharedPreferences("ritmo_focus", Context.MODE_PRIVATE);
            if (focus.getBoolean("active", false)
                    && focus.getBoolean("running", false)
                    && focus.getLong("endAt", 0L) > System.currentTimeMillis()) {
                FocusTimerService.start(context);
            }
        } catch (Throwable ignored) { }
    }
}
