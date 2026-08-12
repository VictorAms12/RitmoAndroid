package com.ritmo.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReminderScheduler {
    public static void schedule(Context context, Store.Task task) {
        cancel(context, task.id);
        if (task.reminderMinutes < 0 || task.time == null || task.time.trim().isEmpty()) return;
        try {
            Date due = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(task.date + " " + task.time);
            if (due == null) return;
            long when = due.getTime() - task.reminderMinutes * 60_000L;
            if (when <= System.currentTimeMillis()) return;
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(context, task.id, task.title));
        } catch (Exception ignored) { }
    }

    public static void cancel(Context context, long taskId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(context, taskId, ""));
    }

    public static void rescheduleAll(Context context, Store store) {
        for (Store.Task task : store.tasks) schedule(context, task);
    }

    private static PendingIntent pending(Context context, long id, String title) {
        Intent i = new Intent(context, ReminderReceiver.class);
        i.putExtra("taskId", id);
        i.putExtra("title", title);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, (int)(id & 0x7fffffff), i, flags);
    }
}
