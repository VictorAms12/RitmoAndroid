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
        if (task == null) return;
        cancel(context, task.id);
        if (task.inbox || task.reminderMinutes < 0 || task.time == null || task.time.trim().isEmpty()) return;
        if ("done".equals(task.status) && "none".equals(task.recurrence)) return;
        try {
            String date = task.date;
            if ("done".equals(task.status)) {
                int guard = 0;
                do {
                    date = Store.nextOccurrence(date, task.recurrence);
                    guard++;
                } while (guard < 370 && reminderTime(date, task) <= System.currentTimeMillis());
            }
            long when = reminderTime(date, task);
            if (when <= System.currentTimeMillis()) return;
            schedulePending(context, task, when);
        } catch (Exception ignored) { }
    }

    private static long reminderTime(String date, Store.Task task) throws Exception {
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        parser.setLenient(false);
        Date due = parser.parse(date + " " + task.time);
        if (due == null) return 0L;
        return due.getTime() - task.reminderMinutes * 60_000L;
    }

    public static void scheduleAt(Context context, Store.Task task, long when) {
        if (task == null || "done".equals(task.status) || task.inbox || when <= System.currentTimeMillis()) return;
        schedulePending(context, task, when);
    }

    private static void schedulePending(Context context, Store.Task task, long when) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(context, task.id, task.title));
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
        i.setAction(ReminderReceiver.ACTION_REMIND);
        i.putExtra("taskId", id);
        i.putExtra("title", title);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, (int)(id & 0x7fffffff), i, flags);
    }
}
