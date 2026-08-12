package com.ritmo.mobile;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class FocusActivity extends Activity {
    private static final String PREFS = "ritmo_focus";

    private Store store;
    private Store.Task task;
    private boolean dark;
    private int BG, PANEL, PANEL2, TEXT, MUTED, LINE, BRAND, BRAND_SOFT, MINT, AMBER, BAD;

    private ProgressRingView ring;
    private TextView timerText, statusText, modeLabel;
    private Button startPause, finish, mode25Button, mode50Button, modeTaskButton;
    private int taskModeMinutes = 90;
    private CheckBox autoComplete;
    private CountDownTimer timer;

    private int plannedMinutes = 25;
    private String mode = "Pomodoro 25";
    private long remainingMillis = 25 * 60_000L;
    private long endAt = 0L;
    private long segmentStartedAt = 0L;
    private long elapsedMillis = 0L;
    private boolean running = false;
    private boolean finished = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        dark = resolveDarkMode();
        setTheme(dark ? R.style.Theme_Ritmo_Dark : R.style.Theme_Ritmo);
        super.onCreate(savedInstanceState);
        applyPalette();
        configureSystemBars();
        store = new Store(this);
        long taskId = getIntent().getLongExtra("taskId", 0L);
        task = store.findTask(taskId);
        restoreState(taskId);
        buildUi();
        resumeTickerIfNeeded();
    }

    @Override protected void onPause() {
        persistState();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (timer != null) timer.cancel();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        persistState();
        super.onBackPressed();
    }

    private boolean resolveDarkMode() {
        SharedPreferences ui = getSharedPreferences("ritmo_ui", MODE_PRIVATE);
        String mode = ui.getString("theme_mode", "");
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        if ("system".equals(mode)) return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return ui.contains("dark") ? ui.getBoolean("dark", true) : true;
    }

    private void applyPalette() {
        if (dark) {
            BG = Color.rgb(7, 26, 20);          // verde profundo
            PANEL = Color.rgb(13, 38, 30);
            PANEL2 = Color.rgb(18, 51, 40);
            TEXT = Color.rgb(240, 248, 244);
            MUTED = Color.rgb(145, 170, 160);
            LINE = Color.rgb(29, 73, 58);
            BRAND = Color.rgb(66, 211, 155);
            BRAND_SOFT = Color.rgb(21, 63, 49);
            MINT = Color.rgb(97, 231, 178);
            AMBER = Color.rgb(229, 178, 81);
            BAD = Color.rgb(240, 125, 125);
        } else {
            BG = Color.rgb(245, 248, 246);
            PANEL = Color.WHITE;
            PANEL2 = Color.rgb(234, 243, 239);
            TEXT = Color.rgb(16, 33, 26);
            MUTED = Color.rgb(97, 115, 107);
            LINE = Color.rgb(220, 232, 226);
            BRAND = Color.rgb(20, 122, 90);
            BRAND_SOFT = Color.rgb(223, 243, 234);
            MINT = Color.rgb(44, 182, 125);
            AMBER = Color.rgb(212, 154, 42);
            BAD = Color.rgb(217, 92, 92);
        }
    }

    private void configureSystemBars() {
        try {
            if (Build.VERSION.SDK_INT < 35) {
                getWindow().setStatusBarColor(BG);
                getWindow().setNavigationBarColor(BG);
            }
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    c.setSystemBarsAppearance(dark ? 0 : mask, mask);
                }
            }
        } catch (Throwable ignored) { }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(10), dp(18), dp(18));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = 0, bottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars());
                top = b.top; bottom = b.bottom;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(dp(18), dp(10) + top, dp(18), dp(18) + bottom);
            return insets;
        });

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton close = new ImageButton(this);
        close.setImageResource(R.drawable.ic_close);
        close.setColorFilter(TEXT);
        close.setBackground(round(PANEL, 14, true));
        close.setPadding(dp(11), dp(11), dp(11), dp(11));
        close.setContentDescription("Voltar");
        close.setOnClickListener(v -> onBackPressed());
        top.addView(close, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);
        titleBox.addView(text("Modo foco", 20, TEXT, true));
        titleBox.addView(text("Uma coisa por vez", 11, MUTED, false));
        top.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top);

        LinearLayout taskCard = card();
        taskCard.setOrientation(LinearLayout.VERTICAL);
        taskCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView eyebrow = text(task == null ? "SESSÃO LIVRE" : "TAREFA EM FOCO", 10, BRAND, true);
        taskCard.addView(eyebrow);
        taskCard.addView(text(task == null ? "Sessão de concentração" : task.title, 17, TEXT, true));
        if (task != null) taskCard.addView(text(task.category + " · planejado " + humanMinutes(task.minutes), 11, MUTED, false));
        LinearLayout.LayoutParams tcp = margin(dp(18), dp(14));
        root.addView(taskCard, tcp);

        TextView backgroundHint = text("Você pode bloquear a tela ou usar outros apps. O cronômetro continua pela notificação do Ritmo.", 10, MUTED, false);
        backgroundHint.setPadding(dp(3), 0, dp(3), dp(12));
        root.addView(backgroundHint);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setGravity(Gravity.CENTER);
        taskModeMinutes = task == null ? 90 : Math.max(5, Math.min(120, task.minutes));
        mode25Button = chip("25 min", plannedMinutes == 25);
        mode50Button = chip("50 min", plannedMinutes == 50);
        modeTaskButton = chip(task == null ? "90 min" : "Tarefa", plannedMinutes == taskModeMinutes);
        modes.addView(mode25Button, weightedChip(0, 4));
        modes.addView(mode50Button, weightedChip(4, 4));
        modes.addView(modeTaskButton, weightedChip(4, 0));
        root.addView(modes, margin(0, dp(14)));
        mode25Button.setOnClickListener(v -> selectMode(25, "Pomodoro 25"));
        mode50Button.setOnClickListener(v -> selectMode(50, "Foco 50"));
        modeTaskButton.setOnClickListener(v -> selectMode(taskModeMinutes, task == null ? "Foco 90" : "Duração da tarefa"));

        FrameLayout timerWrap = new FrameLayout(this);
        ring = new ProgressRingView(this);
        ring.setStrokeDp(9f);
        ring.setShowText(false);
        ring.setColors(MINT, dark ? Color.rgb(29,73,58) : Color.rgb(220,232,226), TEXT);
        timerWrap.addView(ring, new FrameLayout.LayoutParams(dp(238), dp(238), Gravity.CENTER));

        LinearLayout timerLabels = new LinearLayout(this);
        timerLabels.setOrientation(LinearLayout.VERTICAL);
        timerLabels.setGravity(Gravity.CENTER);
        timerText = text(formatTime(remainingMillis), 38, TEXT, true);
        timerText.setGravity(Gravity.CENTER);
        modeLabel = text(mode, 11, MUTED, true);
        modeLabel.setGravity(Gravity.CENTER);
        statusText = text(running ? "Foco em andamento" : "Pronto para começar", 10, running ? MINT : MUTED, false);
        statusText.setGravity(Gravity.CENTER);
        timerLabels.addView(timerText);
        timerLabels.addView(modeLabel);
        timerLabels.addView(statusText);
        timerWrap.addView(timerLabels, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(timerWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        autoComplete = new CheckBox(this);
        autoComplete.setText("Concluir tarefa ao finalizar o foco");
        autoComplete.setTextColor(TEXT);
        autoComplete.setButtonTintList(android.content.res.ColorStateList.valueOf(BRAND));
        autoComplete.setTextSize(12);
        autoComplete.setVisibility(task == null ? View.GONE : View.VISIBLE);
        root.addView(autoComplete, margin(0, dp(12)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        startPause = primaryButton(running ? "Pausar" : "Iniciar foco");
        finish = secondaryButton("Encerrar");
        buttons.addView(startPause, weightedChip(0, 5));
        buttons.addView(finish, weightedChip(5, 0));
        root.addView(buttons);

        startPause.setOnClickListener(v -> toggleRunning());
        finish.setOnClickListener(v -> finishSession(false));
        renderTimer();
        setContentView(root);
    }

    private void selectMode(int minutes, String label) {
        if (running || elapsedMillis > 0L) return;
        plannedMinutes = Math.max(1, minutes);
        mode = label;
        remainingMillis = plannedMinutes * 60_000L;
        finished = false;
        clearPersistedState();
        updateModeButtons();
        renderTimer();
    }

    private void updateModeButtons() {
        if (mode25Button != null) styleModeButton(mode25Button, plannedMinutes == 25);
        if (mode50Button != null) styleModeButton(mode50Button, plannedMinutes == 50);
        if (modeTaskButton != null) styleModeButton(modeTaskButton, plannedMinutes == taskModeMinutes);
    }

    private void styleModeButton(Button button, boolean active) {
        button.setTextColor(active ? (dark ? Color.WHITE : BRAND) : MUTED);
        button.setBackground(round(active ? BRAND_SOFT : PANEL, 999, true));
    }

    private void toggleRunning() {
        if (finished) {
            elapsedMillis = 0L;
            remainingMillis = plannedMinutes * 60_000L;
            finished = false;
        }
        if (running) pauseTimer(); else startTimer();
    }

    private void startTimer() {
        if (remainingMillis <= 0L) remainingMillis = plannedMinutes * 60_000L;
        running = true;
        segmentStartedAt = System.currentTimeMillis();
        endAt = segmentStartedAt + remainingMillis;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        haptic(HapticFeedbackConstants.CLOCK_TICK);
        persistState();
        try { FocusTimerService.start(this); } catch (Throwable ignored) { }
        startTicker();
        renderTimer();
    }

    private void pauseTimer() {
        long now = System.currentTimeMillis();
        if (running) {
            remainingMillis = Math.max(0L, endAt - now);
            elapsedMillis += Math.max(0L, now - segmentStartedAt);
        }
        running = false;
        if (timer != null) timer.cancel();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        renderTimer();
        persistState();
        try { FocusTimerService.stop(this); } catch (Throwable ignored) { }
    }

    private void startTicker() {
        if (timer != null) timer.cancel();
        long now = System.currentTimeMillis();
        remainingMillis = Math.max(0L, endAt - now);
        timer = new CountDownTimer(Math.max(1L, remainingMillis), 250L) {
            @Override public void onTick(long millisUntilFinished) {
                remainingMillis = Math.max(0L, endAt - System.currentTimeMillis());
                renderTimer();
            }
            @Override public void onFinish() {
                if (running) elapsedMillis += Math.max(0L, endAt - segmentStartedAt);
                remainingMillis = 0L;
                running = false;
                finishSession(true);
            }
        }.start();
    }

    private void resumeTickerIfNeeded() {
        if (!running) { renderTimer(); return; }
        long now = System.currentTimeMillis();
        if (endAt <= now) {
            elapsedMillis += Math.max(0L, endAt - segmentStartedAt);
            remainingMillis = 0L;
            running = false;
            finishSession(true);
        } else {
            remainingMillis = endAt - now;
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            persistState();
            try { FocusTimerService.start(this); } catch (Throwable ignored) { }
            startTicker();
        }
    }

    private void finishSession(boolean completedTimer) {
        if (finished) return;
        long now = System.currentTimeMillis();
        long actualMs = elapsedMillis;
        if (running) actualMs += Math.max(0L, now - segmentStartedAt);
        running = false;
        if (timer != null) timer.cancel();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int actualMinutes = actualMs <= 0 ? 0 : Math.max(1, Math.round(actualMs / 60_000f));
        if (actualMinutes > 0) {
            store.addFocusSession(new Store.FocusSession(
                    System.currentTimeMillis(), task == null ? 0L : task.id,
                    task == null ? "Sessão de foco" : task.title,
                    Store.today(), mode, plannedMinutes, actualMinutes,
                    now - actualMs
            ));
        }
        if (task != null && autoComplete != null && autoComplete.isChecked() && !"done".equals(task.status)) {
            store.setTaskStatus(task, "done");
            ReminderScheduler.cancel(this, task.id);
        }
        finished = true;
        clearPersistedState();
        try { FocusTimerService.stop(this); } catch (Throwable ignored) { }
        haptic(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS);
        timerText.setText(completedTimer ? "Concluído" : humanMinutes(actualMinutes));
        statusText.setText(completedTimer ? "Ótimo trabalho. Faça uma pausa curta." : "Sessão registrada");
        statusText.setTextColor(MINT);
        ring.setValue(completedTimer ? 100 : Math.min(100, Math.round(actualMinutes * 100f / Math.max(1, plannedMinutes))));
        startPause.setText("Nova sessão");
        finish.setText("Voltar ao Ritmo");
        finish.setOnClickListener(v -> finish());
    }

    private void renderTimer() {
        if (timerText == null) return;
        timerText.setText(formatTime(remainingMillis));
        modeLabel.setText(mode);
        statusText.setText(running ? "Foco em andamento" : elapsedMillis > 0 ? "Pausado" : "Pronto para começar");
        statusText.setTextColor(running ? MINT : MUTED);
        startPause.setText(running ? "Pausar" : elapsedMillis > 0 ? "Continuar" : "Iniciar foco");
        long total = plannedMinutes * 60_000L;
        int pct = total <= 0 ? 0 : Math.round((total - remainingMillis) * 100f / total);
        ring.setValue(Math.max(0, Math.min(100, pct)));
    }

    private void persistState() {
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        e.putLong("taskId", task == null ? 0L : task.id)
                .putInt("plannedMinutes", plannedMinutes)
                .putString("mode", mode)
                .putLong("remainingMillis", remainingMillis)
                .putLong("endAt", endAt)
                .putLong("segmentStartedAt", segmentStartedAt)
                .putLong("elapsedMillis", elapsedMillis)
                .putBoolean("running", running)
                .putBoolean("active", !finished && (running || elapsedMillis > 0L))
                .apply();
    }

    private void restoreState(long taskId) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean active = p.getBoolean("active", false);
        long storedTask = p.getLong("taskId", -1L);
        if (!active || storedTask != taskId) {
            plannedMinutes = task == null ? 25 : Math.max(5, Math.min(120, task.minutes));
            mode = task == null ? "Pomodoro 25" : "Duração da tarefa";
            if (task == null) plannedMinutes = 25;
            remainingMillis = plannedMinutes * 60_000L;
            return;
        }
        plannedMinutes = p.getInt("plannedMinutes", 25);
        mode = p.getString("mode", "Pomodoro 25");
        remainingMillis = p.getLong("remainingMillis", plannedMinutes * 60_000L);
        endAt = p.getLong("endAt", 0L);
        segmentStartedAt = p.getLong("segmentStartedAt", 0L);
        elapsedMillis = p.getLong("elapsedMillis", 0L);
        running = p.getBoolean("running", false);
    }

    private void clearPersistedState() { getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply(); }

    private String formatTime(long ms) {
        long total = Math.max(0L, (ms + 999L) / 1000L);
        long min = total / 60L;
        long sec = total % 60L;
        return String.format(Locale.US, "%02d:%02d", min, sec);
    }

    private String humanMinutes(int minutes) {
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + "h" : h + "h " + m + "min";
    }

    private void haptic(int constant) { try { startPause.performHapticFeedback(constant); } catch (Throwable ignored) { } }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setLineSpacing(0f, 1.08f);
        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setBackground(round(PANEL, 16, true));
        l.setElevation(dark ? dp(1) : dp(3));
        return l;
    }

    private Button chip(String label, boolean active) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(11); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(active ? (dark ? Color.WHITE : BRAND) : MUTED);
        b.setBackground(round(active ? BRAND_SOFT : PANEL, 999, true));
        b.setMinHeight(dp(44));
        b.setPadding(dp(8), dp(6), dp(8), dp(6));
        return b;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE); b.setBackground(round(BRAND, 12, false)); b.setMinHeight(dp(52));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(TEXT); b.setBackground(round(PANEL, 12, true)); b.setMinHeight(dp(52));
        return b;
    }

    private GradientDrawable round(int color, int radiusDp, boolean stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radiusDp));
        if (stroke) g.setStroke(dp(1), LINE);
        return g;
    }

    private LinearLayout.LayoutParams weightedChip(int left, int right) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1f);
        p.setMargins(dp(left), 0, dp(right), 0);
        return p;
    }

    private LinearLayout.LayoutParams margin(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, top, 0, bottom); return p;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
