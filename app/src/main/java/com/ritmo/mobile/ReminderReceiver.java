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

    @Override
    public void onReceive(Context context, Intent intent) {
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
        PendingIntent contentIntent = PendingIntent.getActivity(context, 9001, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, CHANNEL_ID)
                : new android.app.Notification.Builder(context);
        b.setSmallIcon(com.ritmo.mobile.R.drawable.ic_notification)
                .setContentTitle("Ritmo")
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(android.app.Notification.PRIORITY_HIGH);
        try {
            nm.notify((int)(System.currentTimeMillis() & 0x7fffffff), b.build());
        } catch (SecurityException ignored) { }
    }
}
