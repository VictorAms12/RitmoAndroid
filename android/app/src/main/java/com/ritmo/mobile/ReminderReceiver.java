package com.ritmo.mobile;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "ritmo_reminders";
    public static final String ACTION_REMIND = "com.ritmo.mobile.REMIND";
    public static final String ACTION_COMPLETE = "com.ritmo.mobile.COMPLETE";
    public static final String ACTION_SNOOZE = "com.ritmo.mobile.SNOOZE";
    public static final String ACTION_ROUTINE_REMIND = "com.ritmo.mobile.ROUTINE_REMIND";
    public static final String ACTION_ROUTINE_COMPLETE = "com.ritmo.mobile.ROUTINE_COMPLETE";
    public static final String ACTION_ROUTINE_SNOOZE = "com.ritmo.mobile.ROUTINE_SNOOZE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_ROUTINE_REMIND.equals(action) || ACTION_ROUTINE_COMPLETE.equals(action) || ACTION_ROUTINE_SNOOZE.equals(action)) {
            handleRoutine(context, intent, action);
            return;
        }
        handleTask(context, intent, action);
    }

    private void handleTask(Context context, Intent intent, String action) {
        long taskId = intent.getLongExtra("taskId", 0L);
        int notificationId = taskNotificationId(taskId);

        if (ACTION_COMPLETE.equals(action)) {
            Store store = new Store(context);
            Store.Task task = store.findTask(taskId);
            if (task != null && !"done".equals(task.status)) store.setTaskStatus(task, "done");
            cancelNotification(context, notificationId);
            return;
        }

        if (ACTION_SNOOZE.equals(action)) {
            Store store = new Store(context);
            Store.Task task = store.findTask(taskId);
            if (task != null) ReminderScheduler.scheduleAt(context, task, System.currentTimeMillis() + 10 * 60_000L);
            cancelNotification(context, notificationId);
            return;
        }

        Store currentStore = new Store(context);
        Store.Task currentTask = currentStore.findTask(taskId);
        if (currentTask == null || "done".equals(currentTask.status) || currentTask.inbox) {
            cancelNotification(context, notificationId);
            return;
        }
        String title = currentTask.title;
        if (title == null || title.trim().isEmpty()) title = "Você tem uma tarefa agora";
        ensureChannel(context);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        PendingIntent contentIntent = openAppIntent(context, 9001 + notificationId);
        Intent complete = new Intent(context, ReminderReceiver.class).setAction(ACTION_COMPLETE).putExtra("taskId", taskId);
        PendingIntent completePi = PendingIntent.getBroadcast(context, 100000 + notificationId, complete, flags());
        Intent snooze = new Intent(context, ReminderReceiver.class).setAction(ACTION_SNOOZE).putExtra("taskId", taskId);
        PendingIntent snoozePi = PendingIntent.getBroadcast(context, 200000 + notificationId, snooze, flags());

        android.app.Notification.Builder b = builder(context)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Ritmo · Tarefa")
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .addAction(R.drawable.ic_check, "Concluir", completePi)
                .addAction(R.drawable.ic_bell, "Adiar 10 min", snoozePi);
        try { nm.notify(notificationId, b.build()); } catch (SecurityException ignored) { }
    }

    private void handleRoutine(Context context, Intent intent, String action) {
        long routineId = intent.getLongExtra("routineId", 0L);
        int notificationId = routineNotificationId(routineId);
        Store store = new Store(context);
        Store.Routine routine = store.findRoutine(routineId);

        if (ACTION_ROUTINE_COMPLETE.equals(action)) {
            if (routine != null && !routine.doneOn(Store.today())) {
                routine.toggle(Store.today());
                store.save();
            }
            if (routine != null) RoutineReminderScheduler.schedule(context, routine);
            cancelNotification(context, notificationId);
            return;
        }

        if (ACTION_ROUTINE_SNOOZE.equals(action)) {
            if (routine != null) RoutineReminderScheduler.scheduleAt(context, routine, System.currentTimeMillis() + 10 * 60_000L);
            cancelNotification(context, notificationId);
            return;
        }

        if (routine == null || !routine.dueOn(Store.today()) || routine.doneOn(Store.today())) {
            cancelNotification(context, notificationId);
            if (routine != null) RoutineReminderScheduler.schedule(context, routine);
            return;
        }
        String title = routine.title;
        if (title == null || title.trim().isEmpty()) title = "Hora da sua rotina";
        ensureChannel(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        PendingIntent contentIntent = openAppIntent(context, 300000 + notificationId);
        Intent complete = new Intent(context, ReminderReceiver.class).setAction(ACTION_ROUTINE_COMPLETE).putExtra("routineId", routineId);
        PendingIntent completePi = PendingIntent.getBroadcast(context, 400000 + notificationId, complete, flags());
        Intent snooze = new Intent(context, ReminderReceiver.class).setAction(ACTION_ROUTINE_SNOOZE).putExtra("routineId", routineId);
        PendingIntent snoozePi = PendingIntent.getBroadcast(context, 500000 + notificationId, snooze, flags());

        android.app.Notification.Builder b = builder(context)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Ritmo · Hábito")
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .addAction(R.drawable.ic_check, "Concluir hábito", completePi)
                .addAction(R.drawable.ic_bell, "Adiar 10 min", snoozePi);
        try { nm.notify(notificationId, b.build()); } catch (SecurityException ignored) { }

        if (routine != null) RoutineReminderScheduler.schedule(context, routine);
    }

    private static PendingIntent openAppIntent(Context context, int requestCode) {
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, requestCode, open, flags());
    }

    private static int flags() { return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE; }

    private static android.app.Notification.Builder builder(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, CHANNEL_ID)
                : new android.app.Notification.Builder(context);
    }

    private static void ensureChannel(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lembretes do Ritmo", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Avisos de tarefas, compromissos e hábitos");
        nm.createNotificationChannel(channel);
    }

    private static void cancelNotification(Context context, int id) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(id);
    }

    private static int taskNotificationId(long taskId) {
        int id = (int)(taskId & 0x3fffffff);
        return id == 0 ? 1 : id;
    }

    private static int routineNotificationId(long routineId) {
        int id = (int)(Math.abs(routineId) & 0x1fffffff);
        return 1000000000 + (id == 0 ? 1 : id);
    }
}
