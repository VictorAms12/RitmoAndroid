package com.ritmo.mobile;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class RitmoWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        update(context, manager, appWidgetIds);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, RitmoWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids != null && ids.length > 0) update(context, manager, ids);
    }

    private static void update(Context context, AppWidgetManager manager, int[] ids) {
        Store store = new Store(context);
        String today = Store.today();
        int total = store.taskCountOn(today);
        int done = store.completedOn(today);
        int pct = total == 0 ? 0 : Math.round(done * 100f / total);
        int pending = Math.max(0, total - done);
        Store.Routine best = store.bestRoutineByStreak();
        int streak = best == null ? 0 : best.streak(today);
        int focus = store.focusMinutesOn(today);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 5011, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_ritmo);
            views.setTextViewText(R.id.widgetPercent, pct + "%");
            views.setTextViewText(R.id.widgetPending, pending + " pendente" + (pending == 1 ? "" : "s") + " · 🔥 " + streak);
            String focusText = focus <= 0 ? "sem foco registrado" : (focus < 60 ? focus + " min de foco" : (focus / 60) + "h " + (focus % 60) + "min de foco");
            views.setTextViewText(R.id.widgetSummary, done + " de " + total + " tarefas · " + focusText);
            views.setOnClickPendingIntent(R.id.widgetRoot, pi);
            manager.updateAppWidget(id, views);
        }
    }
}
