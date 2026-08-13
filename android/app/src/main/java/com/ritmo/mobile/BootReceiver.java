package com.ritmo.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        try {
            Store store = new Store(context);
            ReminderScheduler.rescheduleAll(context, store);
            RoutineReminderScheduler.rescheduleAll(context, store);
        } catch (Throwable ignored) { }

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
