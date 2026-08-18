package com.ritmo.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class RoutineReminderScheduler {
    private RoutineReminderScheduler() { }

    public static void schedule(Context context, Store.Routine routine) {
        if (routine == null) return;
        cancel(context, routine.id);
        if (routine.reminderMinutes < 0 || routine.time == null || routine.time.trim().isEmpty()) return;
        try {
            String date = nextDueDate(routine);
            if (date == null) return;
            Date due = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(date + " " + routine.time);
            if (due == null) return;
            long when = due.getTime() - routine.reminderMinutes * 60_000L;
            if (when <= System.currentTimeMillis()) {
                date = nextDueDateAfter(routine, date);
                if (date == null) return;
                due = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(date + " " + routine.time);
                if (due == null) return;
                when = due.getTime() - routine.reminderMinutes * 60_000L;
            }
            scheduleAt(context, routine, when);
        } catch (Exception ignored) { }
    }

    public static void scheduleAt(Context context, Store.Routine routine, long when) {
        if (routine == null || routine.doneOn(Store.today()) || when <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(context, routine.id, routine.title));
    }

    public static void cancel(Context context, long routineId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(context, routineId, ""));
    }

    public static void rescheduleAll(Context context, Store store) {
        for (Store.Routine routine : store.routines) schedule(context, routine);
    }

    private static PendingIntent pending(Context context, long id, String title) {
        Intent i = new Intent(context, ReminderReceiver.class);
        i.setAction(ReminderReceiver.ACTION_ROUTINE_REMIND);
        i.putExtra("routineId", id);
        i.putExtra("title", title);
        int requestCode = 600000000 + (int)(Math.abs(id) % 100000000L);
        return PendingIntent.getBroadcast(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static String nextDueDate(Store.Routine routine) {
        String today = Store.today();
        for (int i = 0; i < 14; i++) {
            String d = Store.addDays(today, i);
            if (!routine.dueOn(d) || routine.doneOn(d)) continue;
            if (i > 0) return d;
            try {
                Date due = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(d + " " + routine.time);
                if (due != null && due.getTime() - routine.reminderMinutes * 60_000L > System.currentTimeMillis()) return d;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static String nextDueDateAfter(Store.Routine routine, String after) {
        for (int i = 1; i < 15; i++) {
            String d = Store.addDays(after, i);
            if (routine.dueOn(d) && !routine.doneOn(d)) return d;
        }
        return null;
    }
}
