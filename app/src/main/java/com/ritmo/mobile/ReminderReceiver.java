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

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        long taskId = intent.getLongExtra("taskId", 0L);
        int notificationId = notificationId(taskId);

        if (ACTION_COMPLETE.equals(action)) {
            Store store = new Store(context);
            Store.Task task = store.findTask(taskId);
            if (task != null && !"done".equals(task.status)) store.setTaskStatus(task, "done");
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(notificationId);
            return;
        }

        if (ACTION_SNOOZE.equals(action)) {
            Store store = new Store(context);
            Store.Task task = store.findTask(taskId);
            if (task != null) ReminderScheduler.scheduleAt(context, task, System.currentTimeMillis() + 10 * 60_000L);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(notificationId);
            return;
        }

        String title = intent.getStringExtra("title");
        if (title == null || title.trim().isEmpty()) title = "Você tem uma tarefa agora";

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lembretes do Ritmo", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Avisos de tarefas e compromissos");
            nm.createNotificationChannel(channel);
        }

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 9001 + notificationId, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent complete = new Intent(context, ReminderReceiver.class).setAction(ACTION_COMPLETE).putExtra("taskId", taskId);
        PendingIntent completePi = PendingIntent.getBroadcast(context, 100000 + notificationId, complete, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snooze = new Intent(context, ReminderReceiver.class).setAction(ACTION_SNOOZE).putExtra("taskId", taskId);
        PendingIntent snoozePi = PendingIntent.getBroadcast(context, 200000 + notificationId, snooze, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, CHANNEL_ID)
                : new android.app.Notification.Builder(context);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Ritmo")
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .addAction(R.drawable.ic_check, "Concluir", completePi)
                .addAction(R.drawable.ic_bell, "Adiar 10 min", snoozePi);
        try { nm.notify(notificationId, b.build()); } catch (SecurityException ignored) { }
    }

    private static int notificationId(long taskId) {
        int id = (int)(taskId & 0x7fffffff);
        return id == 0 ? 1 : id;
    }
}
