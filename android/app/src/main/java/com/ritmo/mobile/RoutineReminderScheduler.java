package com.ritmo.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.text.SimpleDateFormat;
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
            long when = reminderTime(date, routine);
            if (when <= System.currentTimeMillis()) {
                date = nextDueDateAfter(routine, date);
                if (date == null) return;
                when = reminderTime(date, routine);
            }
            if (when <= System.currentTimeMillis()) return;

            // schedule() may intentionally schedule the next occurrence after the
            // habit was completed today. Do not apply the snooze-only "done today"
            // guard here, otherwise tomorrow's reminder silently disappears.
            schedulePending(context, routine, when);
        } catch (Exception ignored) { }
    }

    private static long reminderTime(String date, Store.Routine routine) throws Exception {
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        parser.setLenient(false);
        Date due = parser.parse(date + " " + routine.time);
        if (due == null) return 0L;
        return due.getTime() - routine.reminderMinutes * 60_000L;
    }

    public static void scheduleAt(Context context, Store.Routine routine, long when) {
        // scheduleAt is used by the "Adiar 10 min" action for the current habit.
        if (routine == null || routine.doneOn(Store.today()) || when <= System.currentTimeMillis()) return;
        schedulePending(context, routine, when);
    }

    private static void schedulePending(Context context, Store.Routine routine, long when) {
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
                if (reminderTime(d, routine) > System.currentTimeMillis()) return d;
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
