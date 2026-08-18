package com.ritmo.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class FocusTimerService extends Service {
    private static final String CHANNEL = "ritmo_focus_live";
    private static final int NOTIFICATION_ID = 2401;
    private static final String PREFS = "ritmo_focus";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean active = p.getBoolean("active", false);
            boolean running = p.getBoolean("running", false);
            long endAt = p.getLong("endAt", 0L);

            if (!active || !running) {
                stopForegroundCompat(true);
                stopSelf();
                return;
            }

            if (endAt > 0L && endAt <= System.currentTimeMillis()) {
                p.edit()
                        .putBoolean("active", true)
                        .putBoolean("running", false)
                        .putInt("remainingSeconds", 0)
                        .apply();
                showCompletedNotification(p);
                stopForegroundCompat(false);
                stopSelf();
                return;
            }

            long delay = Math.max(500L, endAt - System.currentTimeMillis());
            handler.postDelayed(this, delay);
        }
    };

    public static void start(Context context) {
        Intent intent = new Intent(context, FocusTimerService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, FocusTimerService.class));
        } catch (Throwable ignored) { }
        try {
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIFICATION_ID);
        } catch (Throwable ignored) { }
    }

    @Override public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(ticker);
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        startForeground(NOTIFICATION_ID, buildRunningNotification(p));
        handler.post(ticker);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL,
                "Sessão de foco",
                NotificationManager.IMPORTANCE_LOW
        );
        ch.setDescription("Cronômetro ativo do modo foco do Ritmo");
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
    }

    private PendingIntent contentIntent() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 2401, open, flags);
    }

    private Notification buildRunningNotification(SharedPreferences p) {
        long endAt = p.getLong("endAt", System.currentTimeMillis());
        String mode = p.getString("mode", "Modo foco");
        String title = p.getString("title", "Sessão de foco");

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        b.setSmallIcon(R.drawable.ic_focus)
                .setContentTitle(title)
                .setContentText(mode + " · toque para voltar")
                .setContentIntent(contentIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setWhen(endAt)
                .setUsesChronometer(true);

        if (Build.VERSION.SDK_INT >= 24) {
            b.setChronometerCountDown(true);
        }

        return b.build();
    }

    private void showCompletedNotification(SharedPreferences p) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        b.setSmallIcon(R.drawable.ic_check)
                .setContentTitle("Sessão concluída")
                .setContentText(p.getString("title", "Sessão de foco"))
                .setContentIntent(contentIntent())
                .setOngoing(false)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER);

        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, b.build());
    }

    private void stopForegroundCompat(boolean remove) {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(remove ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(remove);
        }
    }
}
