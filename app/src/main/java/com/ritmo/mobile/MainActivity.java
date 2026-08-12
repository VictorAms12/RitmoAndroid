package com.ritmo.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.ClipData;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.EditorInfo;

import java.text.SimpleDateFormat;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private boolean darkMode;
    private int BG, PANEL, PANEL2, TEXT, MUTED, LINE, BRAND, BRAND_DARK, BRAND_SOFT, ACCENT, MINT, GOOD, WARN, BAD, NAV;
    private String themeMode = "system";
    private boolean reduceMotion;
    private Store store;
    private FrameLayout content;
    private LinearLayout bottomNav;
    private String currentPage = "home";
    private String taskFilter = "all";
    private String organizeTab = "stats";
    private String agendaMode = "month";
    private String selectedAgendaDate = Store.today();
    private Calendar visibleMonth = Calendar.getInstance();
    private String taskQuery = "";
    private String taskCategoryFilter = "Todas";
    private TextView topTitleView, topSubtitleView;
    private LinearLayout taskListContainer;
    private View taskEmptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        installCrashRecorder();
        SharedPreferences uiPrefs = getSharedPreferences("ritmo_ui", MODE_PRIVATE);
        String savedThemeMode = uiPrefs.getString("theme_mode", null);
        if (savedThemeMode == null) savedThemeMode = uiPrefs.contains("dark") ? (uiPrefs.getBoolean("dark", false) ? "dark" : "light") : "system";
        themeMode = savedThemeMode;
        darkMode = resolveDarkMode(themeMode);
        reduceMotion = uiPrefs.getBoolean("reduce_motion", false);
        setTheme(darkMode ? com.ritmo.mobile.R.style.Theme_Ritmo_Dark : com.ritmo.mobile.R.style.Theme_Ritmo);
        super.onCreate(savedInstanceState);
        try {
            applyPalette();
            configureSystemBars();
            showStartupSkeleton();

            // Carrega o armazenamento no próximo frame: o skeleton aparece imediatamente
            // sem introduzir spinner ou bloquear a percepção de resposta do app.
            getWindow().getDecorView().post(() -> {
                try {
                    store = new Store(this);
                    buildShell();
                    showPage("home");

                    // Permissões e alarmes ficam para depois da primeira tela útil renderizar.
                    if (content != null) {
                        content.postDelayed(() -> {
                            try { requestNotificationPermissionIfNeeded(); } catch (Throwable ignored) { }
                            try { ReminderScheduler.rescheduleAll(this, store); } catch (Throwable ignored) { }
                            try { RoutineReminderScheduler.rescheduleAll(this, store); } catch (Throwable ignored) { }
                        }, 700);
                    }
                } catch (Throwable e) {
                    recordCrash(e);
                    showRecoveryScreen(e);
                }
            });
        } catch (Throwable e) {
            recordCrash(e);
            showRecoveryScreen(e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (store != null) {
                store.normalizeRecurringTasks();
                if (getSharedPreferences("ritmo_planner_settings", MODE_PRIVATE).getBoolean("autoReplanOverdue", false)) {
                    int moved = store.replanOverdueFlexibleToToday();
                    if (moved > 0) {
                        SmartPlanner.Result result = SmartPlanner.plan(store, plannerSettings());
                        SmartPlanner.apply(this, store, result);
                        try { ReminderScheduler.rescheduleAll(this, store); } catch (Throwable ignored) { }
                    }
                }
                if (content != null) showPage(currentPage);
            }
        } catch (Throwable e) {
            recordCrash(e);
            showRecoveryScreen(e);
        }
    }

    @Override
    public void onBackPressed() {
        if (!"home".equals(currentPage)) { showPage("home"); return; }
        super.onBackPressed();
    }

    private void installCrashRecorder() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try { recordCrash(throwable); } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }

    private void recordCrash(Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            getSharedPreferences("ritmo_diagnostics", MODE_PRIVATE).edit()
                    .putString("last_crash", sw.toString())
                    .putLong("last_crash_time", System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignored) { }
    }

    private void showRecoveryScreen(Throwable e) {
        try {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(20), dp(28), dp(20), dp(28));
            root.setBackgroundColor(BG == 0 ? Color.rgb(244,247,245) : BG);

            TextView title = text("O Ritmo encontrou um erro", 22, TEXT == 0 ? Color.rgb(23,35,31) : TEXT, true);
            root.addView(title);
            TextView msg = text("O modo de recuperação impediu que o app fechasse sem mostrar o motivo. Você pode tentar abrir novamente ou copiar o diagnóstico abaixo.", 13, MUTED == 0 ? Color.DKGRAY : MUTED, false);
            msg.setPadding(0, dp(8), 0, dp(14));
            root.addView(msg);

            String detail = e == null ? "Erro desconhecido" : e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            TextView error = text(detail, 12, BAD == 0 ? Color.rgb(201,79,79) : BAD, true);
            error.setTextIsSelectable(true);
            error.setPadding(dp(12), dp(12), dp(12), dp(12));
            error.setBackground(rounded(PANEL2 == 0 ? Color.rgb(237,243,240) : PANEL2, 12, false));
            root.addView(error, marginBottom(dp(14)));

            Button retry = primaryButton("Tentar abrir novamente");
            retry.setOnClickListener(v -> recreate());
            root.addView(retry, marginBottom(dp(10)));

            Button reset = new Button(this);
            reset.setText("Limpar dados locais e reiniciar");
            reset.setAllCaps(false);
            reset.setOnClickListener(v -> {
                getSharedPreferences("ritmo_prefs", MODE_PRIVATE).edit().clear().apply();
                recreate();
            });
            root.addView(reset);

            ScrollView scroll = new ScrollView(this);
            scroll.addView(root);
            setContentView(scroll);
        } catch (Throwable ignored) { }
    }

    private boolean resolveDarkMode(String mode) {
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    private String userName() {
        String value = getSharedPreferences("ritmo_ui", MODE_PRIVATE).getString("user_name", "");
        return value == null ? "" : value.trim();
    }

    private void applyPalette() {
        if (darkMode) {
            BG = Color.rgb(15, 23, 42);          // #0F172A
            PANEL = Color.rgb(30, 41, 59);       // #1E293B
            PANEL2 = Color.rgb(24, 33, 50);
            TEXT = Color.rgb(248, 250, 252);
            MUTED = Color.rgb(148, 163, 184);
            LINE = Color.rgb(51, 65, 85);
            BRAND = Color.rgb(129, 140, 248);    // Indigo 400
            BRAND_DARK = Color.rgb(79, 70, 229);
            BRAND_SOFT = Color.rgb(49, 46, 89);
            ACCENT = Color.rgb(45, 212, 191);    // Mint
            MINT = ACCENT;
            GOOD = Color.rgb(52, 211, 153);
            WARN = Color.rgb(251, 191, 36);
            BAD = Color.rgb(251, 113, 133);
            NAV = Color.rgb(17, 24, 39);
        } else {
            BG = Color.rgb(248, 249, 250);        // #F8F9FA
            PANEL = Color.WHITE;
            PANEL2 = Color.rgb(241, 245, 249);
            TEXT = Color.rgb(17, 24, 39);         // #111827
            MUTED = Color.rgb(100, 116, 139);
            LINE = Color.rgb(226, 232, 240);
            BRAND = Color.rgb(99, 102, 241);      // Indigo 500
            BRAND_DARK = Color.rgb(79, 70, 229);
            BRAND_SOFT = Color.rgb(238, 242, 255);
            ACCENT = Color.rgb(13, 148, 136);     // Teal 600
            MINT = Color.rgb(16, 185, 129);
            GOOD = Color.rgb(16, 185, 129);
            WARN = Color.rgb(217, 119, 6);
            BAD = Color.rgb(225, 29, 72);
            NAV = Color.WHITE;
        }
    }

    private void configureSystemBars() {
        // Android 15+ força edge-to-edge para apps que miram API 35.
        // Nessas versões evitamos depender dos setters de cor das barras,
        // mantendo apenas a aparência dos ícones e tratando os insets no layout.
        try {
            if (Build.VERSION.SDK_INT < 35) {
                getWindow().setStatusBarColor(BG);
                getWindow().setNavigationBarColor(NAV);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    int appearance = darkMode ? 0 : mask;
                    c.setSystemBarsAppearance(appearance, mask);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int flags = getWindow().getDecorView().getSystemUiVisibility();
                if (darkMode) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                getWindow().getDecorView().setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) { }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2202);
        }
    }

    private void showStartupSkeleton() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(18));
        root.setBackgroundColor(BG);

        root.addView(skeletonBlock(), skeletonParams(148, 22, 18));
        root.addView(skeletonBlock(), skeletonParams(-1, 164, 22));
        root.addView(skeletonBlock(), skeletonParams(104, 14, 12));
        root.addView(skeletonBlock(), skeletonParams(-1, 72, 10));
        root.addView(skeletonBlock(), skeletonParams(-1, 72, 10));
        root.addView(skeletonBlock(), skeletonParams(-1, 72, 10));

        if (!reduceMotion) {
            android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofFloat(root, View.ALPHA, 0.58f, 1f);
            pulse.setDuration(720L);
            pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            pulse.start();
        }
        setContentView(root);
    }

    private View skeletonBlock() {
        View v = new View(this);
        v.setBackground(rounded(PANEL2, 14, false));
        return v;
    }

    private LinearLayout.LayoutParams skeletonParams(int widthDp, int heightDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthDp < 0 ? ViewGroup.LayoutParams.MATCH_PARENT : dp(widthDp), dp(heightDp));
        lp.setMargins(0, 0, 0, dp(bottomDp));
        return lp;
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top; bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        content = new FrameLayout(this);
        content.setBackgroundColor(BG);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        bottomNav = buildBottomNav();
        root.addView(bottomNav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));
        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(12), dp(8));
        bar.setBackgroundColor(BG);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        topTitleView = text("Ritmo", 20, TEXT, true);
        topSubtitleView = text(shortDate(Store.today()), 11, MUTED, false);
        titleBox.addView(topTitleView);
        titleBox.addView(topSubtitleView);
        bar.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView version = text("2.3", 10, BRAND, true);
        version.setPadding(dp(9), dp(5), dp(9), dp(5));
        version.setGravity(Gravity.CENTER);
        version.setBackground(rounded(BRAND_SOFT, 99, false));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.setMargins(0, 0, dp(8), 0);
        bar.addView(version, vp);

        ImageButton theme = iconButton(darkMode ? R.drawable.ic_sun : R.drawable.ic_moon, BRAND);
        theme.setContentDescription("Alternar modo claro e escuro");
        makePressable(theme);
        theme.setOnClickListener(v -> {
            String next = darkMode ? "light" : "dark";
            getSharedPreferences("ritmo_ui", MODE_PRIVATE).edit().putString("theme_mode", next).putBoolean("dark", !darkMode).apply();
            recreate();
        });
        bar.addView(theme, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private void updateTopBar() {
        if (topTitleView == null || topSubtitleView == null) return;
        if ("tasks".equals(currentPage)) {
            topTitleView.setText("Tarefas");
            topSubtitleView.setText("Capture, priorize e conclua");
        } else if ("agenda".equals(currentPage)) {
            topTitleView.setText("Calendário");
            topSubtitleView.setText(fullDate(selectedAgendaDate));
        } else if ("organize".equals(currentPage)) {
            topTitleView.setText("Progresso");
            String label = "kanban".equals(organizeTab) ? "Kanban" : "planner".equals(organizeTab) ? "Planejador" : "projects".equals(organizeTab) ? "Projetos" : "goals".equals(organizeTab) ? "Metas" : "habits".equals(organizeTab) ? "Hábitos" : "Estatísticas";
            topSubtitleView.setText(label + " · sua evolução");
        } else if ("settings".equals(currentPage)) {
            topTitleView.setText("Configurações");
            topSubtitleView.setText("Aparência, acessibilidade e preferências");
        } else {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            String greeting = h < 12 ? "Bom dia" : h < 18 ? "Boa tarde" : "Boa noite";
            String name = userName();
            topTitleView.setText(name.isEmpty() ? greeting : greeting + ", " + name);
            topSubtitleView.setText(fullDate(Store.today()));
        }
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(7), dp(8), dp(7));
        nav.setBackgroundColor(NAV);
        nav.setElevation(dp(10));

        nav.addView(navButton(R.drawable.ic_home, "Hoje", "home"), weighted());
        nav.addView(navButton(R.drawable.ic_calendar, "Calendário", "agenda"), weighted());

        ImageButton plus = new ImageButton(this);
        plus.setImageResource(R.drawable.ic_add);
        plus.setColorFilter(Color.WHITE);
        plus.setScaleType(ImageView.ScaleType.CENTER);
        plus.setBackground(rounded(BRAND, 18, false));
        plus.setElevation(dp(7));
        plus.setContentDescription("Adicionar nova tarefa, hábito ou compromisso");
        makePressable(plus);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(58), dp(56));
        pp.setMargins(dp(6), 0, dp(6), 0);
        nav.addView(plus, pp);
        plus.setOnClickListener(v -> showAddMenu());

        nav.addView(navButton(R.drawable.ic_stats, "Progresso", "organize"), weighted());
        nav.addView(navButton(R.drawable.ic_settings, "Ajustes", "settings"), weighted());
        return nav;
    }

    private View navButton(int iconRes, String label, String page) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(3), dp(5), dp(3), dp(4));
        box.setTag(page);
        box.setContentDescription(label);
        box.setMinimumHeight(dp(56));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(MUTED);
        box.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView txt = text(label, 9, MUTED, true);
        txt.setGravity(Gravity.CENTER);
        txt.setPadding(0, dp(2), 0, 0);
        box.addView(txt);
        makePressable(box);
        box.setOnClickListener(v -> {
            if ("organize".equals(page) && !"organize".equals(currentPage)) organizeTab = "stats";
            showPage(page);
        });
        return box;
    }

    private void highlightNav() {
        if (bottomNav == null) return;
        for (int i = 0; i < bottomNav.getChildCount(); i++) {
            View child = bottomNav.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            boolean active = currentPage.equals(String.valueOf(child.getTag())) || ("tasks".equals(currentPage) && "home".equals(String.valueOf(child.getTag())));
            child.setBackground(active ? rounded(BRAND_SOFT, 14, false) : null);
            LinearLayout box = (LinearLayout) child;
            for (int j = 0; j < box.getChildCount(); j++) {
                View inner = box.getChildAt(j);
                if (inner instanceof TextView) ((TextView) inner).setTextColor(active ? BRAND : MUTED);
                if (inner instanceof ImageView) ((ImageView) inner).setColorFilter(active ? BRAND : MUTED);
            }
        }
    }

    private void showPage(String page) {
        try {
            currentPage = page;
            if (content == null) return;
            View next;
            if ("tasks".equals(page)) next = buildTasksPage();
            else if ("agenda".equals(page)) next = buildAgendaPage();
            else if ("organize".equals(page)) next = buildOrganizePage();
            else if ("settings".equals(page)) next = buildSettingsPage();
            else next = buildHomePage();

            content.removeAllViews();
            content.addView(next, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (!reduceMotion) {
                next.setAlpha(0f);
                next.setTranslationX(dp(10));
                next.animate().alpha(1f).translationX(0f).setDuration(180L).start();
            }
            highlightNav();
            updateTopBar();
        } catch (Throwable e) {
            recordCrash(e);
            showRecoveryScreen(e);
        }
    }

    private View buildHomePage() {
        LinearLayout body = body();
        body.addView(buildHero());

        int overdueFlexible = 0;
        for (Store.Task t : store.tasks) {
            if (!"done".equals(t.status) && t.flexible && "none".equals(t.recurrence) && t.date.compareTo(Store.today()) < 0) overdueFlexible++;
        }
        if (overdueFlexible > 0) {
            body.addView(sectionHeader("Pendências", null, null));
            body.addView(replanCard(overdueFlexible));
        }

        body.addView(sectionHeader("Seu dia", "Todas as tarefas", v -> showPage("tasks")));
        body.addView(dayTimeline());

        body.addView(sectionHeader("Foco", "Ver progresso", v -> {
            organizeTab = "stats";
            showPage("organize");
        }));
        body.addView(focusQuickCard());

        body.addView(sectionHeader("Hábitos de hoje", "Gerenciar", v -> {
            organizeTab = "habits";
            showPage("organize");
        }));
        body.addView(homeRoutineCard());

        body.addView(sectionHeader("Metas em andamento", "Abrir metas", v -> {
            organizeTab = "goals";
            showPage("organize");
        }));
        body.addView(goalsCard(Math.min(3, store.goals.size())));

        body.addView(sectionHeader("Visão da semana", "Estatísticas", v -> {
            organizeTab = "stats";
            showPage("organize");
        }));
        body.addView(weeklyChartCard());
        return wrapScroll(body);
    }

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{darkMode ? Color.rgb(49, 46, 129) : Color.rgb(79, 70, 229),
                        darkMode ? Color.rgb(30, 41, 90) : Color.rgb(99, 102, 241)});
        bg.setCornerRadius(dp(22));
        hero.setBackground(bg);
        hero.setElevation(darkMode ? dp(1) : dp(5));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout intro = new LinearLayout(this);
        intro.setOrientation(LinearLayout.VERTICAL);
        intro.addView(text("HOJE · " + weekdayShort(Store.today()), 10, Color.rgb(199, 210, 254), true));
        String name = userName();
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = h < 12 ? "Bom dia" : h < 18 ? "Boa tarde" : "Boa noite";
        TextView title = text(name.isEmpty() ? greeting + ". Qual é o seu ritmo?" : greeting + ", " + name + ".", isTablet() ? 28 : 24, Color.WHITE, true);
        title.setPadding(0, dp(4), 0, dp(3));
        intro.addView(title);
        intro.addView(text("Progresso é consistência, não perfeição.", 11, Color.rgb(224, 231, 255), false));
        top.addView(intro, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int score = store.combinedDayScore(Store.today());
        ProgressRingView ring = new ProgressRingView(this);
        ring.setValue(score);
        ring.setContentDescription("Progresso de hoje: " + score + "%");
        ring.setStrokeDp(6f);
        ring.setColors(Color.rgb(45, 212, 191), Color.argb(55,255,255,255), Color.WHITE);
        top.addView(ring, new LinearLayout.LayoutParams(dp(88), dp(88)));
        hero.addView(top);

        List<Store.Task> today = tasksForDate(Store.today());
        int done = 0;
        for (Store.Task t : today) if ("done".equals(t.status)) done++;
        Store.Routine best = store.bestRoutineByStreak();
        int streak = best == null ? 0 : best.streak(Store.today());

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(15), 0, 0);
        stats.addView(heroMetric(done + "/" + today.size(), "tarefas"), new LinearLayout.LayoutParams(0, dp(58), 1f));
        stats.addView(heroMetric("🔥 " + streak, "sequência"), new LinearLayout.LayoutParams(0, dp(58), 1f));
        stats.addView(heroMetric(humanMinutes(store.focusMinutesOn(Store.today())), "foco"), new LinearLayout.LayoutParams(0, dp(58), 1f));
        hero.addView(stats);

        return hero;
    }

    private View heroMetric(String value, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6), dp(5), dp(6), dp(5));
        box.setBackground(rounded(Color.argb(28,255,255,255), 12, false));
        TextView v = text(value, 14, Color.WHITE, true); v.setGravity(Gravity.CENTER);
        TextView l = text(label, 9, Color.rgb(224,231,255), false); l.setGravity(Gravity.CENTER);
        box.addView(v); box.addView(l);
        return box;
    }

    private View replanCard(int count) {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(13), dp(12), dp(13));
        View accent = new View(this);
        accent.setBackground(rounded(WARN, 99, false));
        card.addView(accent, new LinearLayout.LayoutParams(dp(4), dp(42)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(8), 0);
        info.addView(text(count + " pendência" + (count == 1 ? "" : "s") + " flexível" + (count == 1 ? "" : "is"), 13, TEXT, true));
        info.addView(text("Replaneje sem perder os prazos definidos.", 10, MUTED, false));
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button action = secondaryButton("Replanejar");
        action.setOnClickListener(v -> {
            int moved = store.replanOverdueFlexibleToToday();
            if (moved > 0) {
                SmartPlanner.Result result = SmartPlanner.plan(store, plannerSettings());
                SmartPlanner.apply(this, store, result);
                try { ReminderScheduler.rescheduleAll(this, store); } catch (Throwable ignored) { }
                Toast.makeText(this, moved + " pendência(s) redistribuída(s).", Toast.LENGTH_SHORT).show();
            }
            showPage("home");
        });
        card.addView(action, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        return card;
    }

    private View dayTimeline() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        List<Store.Task> today = tasksForDate(Store.today());
        Collections.sort(today, Comparator.comparing((Store.Task t) -> safe(t.time, "99:99")));
        if (today.isEmpty()) {
            return emptyState(R.drawable.ic_calendar, "Nenhuma rotina para hoje", "Que tal descansar ou criar algo pequeno para manter o ritmo?", "Criar tarefa", v -> showTaskDialog(null, false, Store.today()));
        }

        String[] names = {"Manhã", "Tarde", "Noite"};
        int[] icons = {R.drawable.ic_sun, R.drawable.ic_focus, R.drawable.ic_moon};
        for (int period = 0; period < 3; period++) {
            LinearLayout group = new LinearLayout(this);
            group.setOrientation(LinearLayout.VERTICAL);
            int count = 0;
            for (Store.Task t : today) if (taskPeriod(t) == period) count++;
            if (count == 0) continue;

            LinearLayout label = new LinearLayout(this);
            label.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[period]); icon.setColorFilter(period == 1 ? WARN : BRAND);
            label.addView(icon, new LinearLayout.LayoutParams(dp(17), dp(17)));
            TextView name = text(names[period] + " · " + count, 11, MUTED, true);
            name.setPadding(dp(7), dp(5), 0, dp(5));
            label.addView(name);
            group.addView(label);
            for (Store.Task t : today) if (taskPeriod(t) == period) group.addView(taskCard(t));
            wrap.addView(group);
        }
        return wrap;
    }

    private int taskPeriod(Store.Task t) {
        int hour = 12;
        try {
            if (t.time != null && t.time.contains(":")) hour = Integer.parseInt(t.time.substring(0, 2));
        } catch (Exception ignored) { }
        if (hour < 12) return 0;
        if (hour < 18) return 1;
        return 2;
    }

    private View focusQuickCard() {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(14), dp(12), dp(14));

        SharedPreferences focusPrefs = getSharedPreferences("ritmo_focus", MODE_PRIVATE);
        boolean activeSession = focusPrefs.getBoolean("active", false);
        long activeTaskId = focusPrefs.getLong("taskId", 0L);

        Store.Task next = activeSession ? store.findTask(activeTaskId) : null;
        if (!activeSession) {
            for (Store.Task t : tasksForDate(Store.today())) {
                if ("done".equals(t.status)) continue;
                if (next == null || safe(t.time, "99:99").compareTo(safe(next.time, "99:99")) < 0) next = t;
            }
        }

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_focus);
        icon.setColorFilter(WARN);
        icon.setPadding(dp(10),dp(10),dp(10),dp(10));
        icon.setBackground(rounded(darkMode ? Color.rgb(67, 53, 30) : Color.rgb(255, 247, 237), 14, false));
        card.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(8), 0);
        String title = activeSession ? (next == null ? "Sessão de foco em andamento" : next.title)
                : (next == null ? "Sessão livre" : next.title);
        info.addView(text(title, 13, TEXT, true));
        String subtitle;
        if (activeSession) {
            subtitle = "Retome seu timer de onde parou";
        } else if (store.focusMinutesOn(Store.today()) > 0) {
            subtitle = humanMinutes(store.focusMinutesOn(Store.today())) + " focados hoje";
        } else {
            subtitle = "Pomodoro, foco longo ou duração da tarefa";
        }
        info.addView(text(subtitle, 10, MUTED, false));
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button start = primaryButton(activeSession ? "Retomar" : "Focar");
        Store.Task target = next;
        start.setOnClickListener(v -> startFocus(target));
        card.addView(start, new LinearLayout.LayoutParams(dp(88), dp(44)));
        return card;
    }

    private void startFocus(Store.Task task) {
        Intent i = new Intent(this, FocusActivity.class);
        if (task != null) i.putExtra("taskId", task.id);
        startActivity(i);
    }

    private View homeRoutineCard() {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(7), dp(14), dp(7));
        int due = 0;
        for (Store.Routine r : store.routines) {
            if (!r.dueOn(Store.today())) continue;
            if (due > 0) card.addView(divider());
            due++;
            card.addView(routineRow(r, true));
        }
        if (due == 0) {
            card.addView(emptyInline("Sem hábitos previstos para hoje. Aproveite o espaço ou crie uma rotina leve."));
        }
        return card;
    }

    private View routineRow(Store.Routine r, boolean homeMode) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        View accent = new View(this);
        accent.setBackground(rounded(routineAccentColor(r.accent), 99, false));
        row.addView(accent, new LinearLayout.LayoutParams(dp(4), dp(42)));

        Button check = smallCheck(r.doneOn(Store.today()));
        check.setEnabled(r.dueOn(Store.today()));
        check.setAlpha(r.dueOn(Store.today()) ? 1f : .35f);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(38), dp(38));
        cp.setMargins(dp(10), 0, 0, 0);
        row.addView(check, cp);

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(dp(10), 0, dp(8), 0);
        TextView title = text(r.title, 13, TEXT, true);
        if (r.doneOn(Store.today())) {
            title.setAlpha(.55f);
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        txt.addView(title);
        String time = r.time == null || r.time.isEmpty() ? "Sem horário" : r.time;
        txt.addView(text(time + " · " + r.category + " · " + humanMinutes(r.minutes), 10, MUTED, false));
        if (!homeMode) {
            String freq = frequencyLabel(r.frequency);
            if ("custom".equals(r.frequency)) freq += " · " + daysMaskLabel(r.daysMask);
            txt.addView(text(freq + (r.reminderMinutes >= 0 ? " · " + reminderLabel(r.reminderMinutes) : ""), 9, BRAND, false));
            if (r.detail != null && !r.detail.isEmpty()) txt.addView(text(r.detail, 10, MUTED, false));
        }
        row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout streak = new LinearLayout(this);
        streak.setOrientation(LinearLayout.VERTICAL);
        streak.setGravity(Gravity.CENTER);
        streak.addView(text("🔥 " + r.streak(Store.today()), 13, WARN, true));
        streak.addView(text("dias", 9, MUTED, false));
        row.addView(streak);

        check.setOnClickListener(v -> animateRoutineToggle(row, check, title, r));
        txt.setOnClickListener(v -> showRoutineDialog(r));
        makePressable(row);
        row.setOnLongClickListener(v -> { confirmDeleteRoutine(r); return true; });
        return row;
    }

    private View metricsRow(List<Store.Task> today) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        int done = 0, minutes = 0;
        for (Store.Task t : today) { if ("done".equals(t.status)) done++; minutes += t.minutes; }
        row.addView(metric(String.valueOf(done), "Concluídas"), weightedMargin(0, 4));
        row.addView(metric(String.valueOf(today.size() - done), "Pendentes"), weightedMargin(4, 4));
        row.addView(metric(humanMinutes(minutes), "Planejado"), weightedMargin(4, 0));
        return row;
    }

    private View metric(String value, String label) {
        LinearLayout box = cardBox(); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.addView(text(value, isTablet() ? 22 : 19, TEXT, true));
        box.addView(text(label, 10, MUTED, false));
        return box;
    }


    private View smartPlanningHomeCard() {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));

        SmartPlanner.Settings settings = plannerSettings();
        int flexible = 0;
        for (Store.Task t : store.tasks) {
            if (!"done".equals(t.status) && t.flexible && "none".equals(t.recurrence)) flexible++;
        }

        int overloaded = 0;
        for (int i = 0; i < 7; i++) {
            String d = Store.addDays(Store.today(), i);
            if (plannerLoadMinutes(d) > settings.capacityMinutes) overloaded++;
        }

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.addView(text(flexible + " tarefa" + (flexible == 1 ? "" : "s") + " flexível" + (flexible == 1 ? "" : "is"), 14, TEXT, true));
        String subtitle = overloaded > 0
                ? overloaded + " dia(s) acima da capacidade nesta semana"
                : "A semana está dentro da capacidade configurada";
        textBox.addView(text(subtitle, 11, overloaded > 0 ? WARN : MUTED, false));
        top.addView(textBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView capacity = text(humanMinutes(settings.capacityMinutes) + "/dia", 11, BRAND, true);
        top.addView(capacity);
        card.addView(top);

        if (flexible > 0 || overloaded > 0) {
            Button plan = primaryButton("Organizar minha semana");
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pp.setMargins(0, dp(12), 0, 0);
            card.addView(plan, pp);
            plan.setOnClickListener(v -> {
                organizeTab = "planner";
                showPage("organize");
            });
        } else {
            TextView hint = text("Marque tarefas como flexíveis para o Ritmo poder distribuí-las automaticamente.", 10, MUTED, false);
            hint.setPadding(0, dp(10), 0, 0);
            card.addView(hint);
        }
        return card;
    }

    private View weeklyChartCard() {
        LinearLayout card = cardBox(); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(14), dp(14), dp(8));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(store.totalCompletedLast7() + " concluídas", 14, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(text(humanMinutes(store.totalCompletedMinutesLast7()), 12, BRAND, true));
        card.addView(top);
        WeeklyBarChart chart = new WeeklyBarChart(this);
        chart.setColors(BRAND, MUTED, PANEL2);
        chart.setData(store.last7CompletionCounts(), store.last7Labels());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        cp.setMargins(0, dp(8), 0, 0);
        card.addView(chart, cp);
        return card;
    }

    private View buildTasksPage() {
        LinearLayout body = body();
        body.addView(sectionHeader("Tarefas", "Limpar concluídas", v -> confirmClearDone()));
        body.addView(taskSearchBar());
        body.addView(taskFilterBar());
        body.addView(taskCategoryBar());

        taskListContainer = new LinearLayout(this);
        taskListContainer.setOrientation(LinearLayout.VERTICAL);
        List<Store.Task> list = new ArrayList<>(store.tasks);
        Collections.sort(list, Comparator.comparing((Store.Task t) -> safe(t.date, "9999")).thenComparing(t -> safe(t.time, "99:99")));
        for (Store.Task t : list) {
            View card = taskCard(t);
            card.setTag(R.id.tag_task_id, t.id);
            taskListContainer.addView(card);
        }
        body.addView(taskListContainer);
        taskEmptyState = emptyState(R.drawable.ic_tasks, "Nada por aqui", "Nenhuma tarefa corresponde aos filtros atuais.", "Limpar filtros", v -> { taskFilter="all"; taskCategoryFilter="Todas"; taskQuery=""; showPage("tasks"); });
        body.addView(taskEmptyState);
        applyTaskVisibility();
        return wrapScroll(body);
    }

    private View taskSearchBar() {
        LinearLayout box = cardBox();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(10), dp(3), dp(10), dp(3));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_search);
        icon.setColorFilter(MUTED);
        box.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        EditText search = new EditText(this);
        search.setHint("Buscar por tarefa, projeto ou descrição");
        search.setHintTextColor(MUTED);
        search.setTextColor(TEXT);
        search.setTextSize(13);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setPadding(dp(9), dp(7), dp(4), dp(7));
        search.setText(taskQuery);
        search.setSelection(search.length());
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
            public void onTextChanged(CharSequence s, int st, int before, int count) { taskQuery = s.toString(); applyTaskVisibility(); }
            public void afterTextChanged(Editable e) { }
        });
        box.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams bp = marginBottom(dp(9));
        box.setLayoutParams(bp);
        return box;
    }

    private View taskFilterBar() {
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        String[][] filters = {{"all","Todas"},{"today","Hoje"},{"todo","Pendentes"},{"done","Concluídas"}};
        for (String[] f : filters) {
            Button b = chip(f[1], taskFilter.equals(f[0]));
            b.setOnClickListener(v -> { taskFilter = f[0]; showPage("tasks"); });
            row.addView(b);
        }
        hs.addView(row); hs.setPadding(0,0,0,dp(4));
        return hs;
    }

    private View taskCategoryBar() {
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        String[] categories = {"Todas","Estudos","Trabalho","Pessoal","Projeto","Saúde","Financeiro"};
        for (String c : categories) {
            Button b = chip(c, taskCategoryFilter.equals(c));
            b.setAlpha(.92f);
            b.setOnClickListener(v -> { taskCategoryFilter = c; showPage("tasks"); });
            row.addView(b);
        }
        hs.addView(row); hs.setPadding(0,0,0,dp(9));
        return hs;
    }

    private void applyTaskVisibility() {
        if (taskListContainer == null) return;
        int visible = 0;
        String q = taskQuery == null ? "" : taskQuery.trim().toLowerCase(new Locale("pt","BR"));
        for (int i = 0; i < taskListContainer.getChildCount(); i++) {
            View v = taskListContainer.getChildAt(i);
            Object idObj = v.getTag(R.id.tag_task_id);
            if (!(idObj instanceof Long)) continue;
            Store.Task t = store.findTask((Long) idObj);
            if (t == null) { v.setVisibility(View.GONE); continue; }
            boolean statusOk = "all".equals(taskFilter)
                    || ("today".equals(taskFilter) && Store.today().equals(t.date))
                    || ("todo".equals(taskFilter) && !"done".equals(t.status))
                    || ("done".equals(taskFilter) && "done".equals(t.status));
            boolean categoryOk = "Todas".equals(taskCategoryFilter) || taskCategoryFilter.equals(t.category);
            String hay = (t.title + " " + t.description + " " + t.category + " " + store.projectTitle(t.projectId)).toLowerCase(new Locale("pt","BR"));
            boolean queryOk = q.isEmpty() || hay.contains(q);
            boolean show = statusOk && categoryOk && queryOk;
            v.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) visible++;
        }
        if (taskEmptyState != null) taskEmptyState.setVisibility(visible == 0 ? View.VISIBLE : View.GONE);
    }

    private View taskCard(Store.Task t) {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(11), dp(8), dp(11));
        card.setLayoutParams(marginBottom(dp(8)));
        makePressable(card);

        Button check = smallCheck("done".equals(t.status));
        card.addView(check, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(dp(11), 0, dp(5), 0);

        TextView title = text(t.title, 14, TEXT, true);
        if ("done".equals(t.status)) {
            title.setAlpha(.5f);
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        center.addView(title);

        String time = t.time == null || t.time.isEmpty() ? "Sem horário" : t.time;
        String meta = time + " · " + t.category + " · " + humanMinutes(t.minutes);
        center.addView(text(meta, 10, MUTED, false));

        String project = t.projectId == 0 ? "" : store.projectTitle(t.projectId);
        if (!project.isEmpty()) center.addView(text("▣ " + project, 10, BRAND, true));

        if (t.subtasks != null && !t.subtasks.isEmpty()) {
            center.addView(text("☑ " + t.completedSubtasks() + "/" + t.subtasks.size() + " subtarefas", 10, MUTED, false));
        }

        LinearLayout badges = new LinearLayout(this);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setPadding(0, dp(4), 0, 0);
        if ("doing".equals(t.status)) badges.addView(miniBadge("Em andamento", WARN));
        if (t.flexible && "none".equals(t.recurrence)) badges.addView(miniBadge("✦ Flexível", GOOD));
        if ("auto".equals(t.priority)) badges.addView(miniBadge("Prioridade auto", BRAND));
        if (badges.getChildCount() > 0) center.addView(badges);

        center.setOnClickListener(v -> showTaskDialog(t, false, null));
        card.addView(center, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View dot = new View(this);
        dot.setBackground(rounded(priorityColor(t.effectivePriority()), 99, false));
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(dp(8), dp(8));
        dpv.setMargins(dp(3),0,dp(3),0);
        card.addView(dot, dpv);

        ImageButton more = iconButton(R.drawable.ic_more, MUTED);
        more.setContentDescription("Opções da tarefa");
        more.setBackgroundColor(Color.TRANSPARENT);
        more.setPadding(dp(9),dp(9),dp(9),dp(9));
        more.setOnClickListener(v -> showTaskActions(t));
        card.addView(more, new LinearLayout.LayoutParams(dp(42), dp(42)));

        check.setOnClickListener(v -> animateTaskToggle(card, check, title, t));
        card.setOnLongClickListener(v -> { showTaskActions(t); return true; });
        return card;
    }

    private View buildAgendaPage() {
        LinearLayout body = body();
        body.addView(sectionHeader("Agenda", "Hoje", v -> {
            selectedAgendaDate = Store.today(); visibleMonth = Calendar.getInstance(); showPage("agenda");
        }));
        body.addView(agendaModeTabs());
        if ("week".equals(agendaMode)) body.addView(weeklyPlanner());
        else body.addView(monthCalendar());

        LinearLayout selected = new LinearLayout(this); selected.setGravity(Gravity.CENTER_VERTICAL);
        selected.setPadding(dp(3), dp(18), dp(3), dp(9));
        selected.addView(text(fullDate(selectedAgendaDate), 17, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView add = text("+ Adicionar",12,BRAND,true); add.setPadding(dp(8),dp(6),0,dp(6));
        add.setOnClickListener(v -> showTaskDialog(null, true, selectedAgendaDate));
        selected.addView(add);
        body.addView(selected);

        List<Store.Task> list = tasksForDate(selectedAgendaDate);
        Collections.sort(list, Comparator.comparing(t -> safe(t.time, "99:99")));
        if (list.isEmpty()) body.addView(emptyCard("Sem compromissos neste dia."));
        for (Store.Task t : list) body.addView(agendaItem(t));
        return wrapScroll(body);
    }

    private View agendaModeTabs() {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        Button month = chip("Mês", "month".equals(agendaMode));
        Button week = chip("Semana", "week".equals(agendaMode));
        month.setOnClickListener(v -> { agendaMode="month"; showPage("agenda"); });
        week.setOnClickListener(v -> { agendaMode="week"; showPage("agenda"); });
        row.addView(month); row.addView(week);
        return row;
    }

    private View monthCalendar() {
        LinearLayout card = cardBox(); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(10),dp(10),dp(10),dp(12));
        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = flatButton("‹"); Button next = flatButton("›");
        TextView month = text(new SimpleDateFormat("MMMM yyyy", new Locale("pt","BR")).format(visibleMonth.getTime()), 15, TEXT, true);
        month.setGravity(Gravity.CENTER); month.setText(month.getText().toString().substring(0,1).toUpperCase(new Locale("pt","BR")) + month.getText().toString().substring(1));
        head.addView(prev, new LinearLayout.LayoutParams(dp(44),dp(40)));
        head.addView(month, new LinearLayout.LayoutParams(0,dp(40),1f));
        head.addView(next, new LinearLayout.LayoutParams(dp(44),dp(40)));
        prev.setOnClickListener(v -> { visibleMonth.add(Calendar.MONTH,-1); showPage("agenda"); });
        next.setOnClickListener(v -> { visibleMonth.add(Calendar.MONTH,1); showPage("agenda"); });
        card.addView(head);

        GridLayout grid = new GridLayout(this); grid.setColumnCount(7); grid.setRowCount(7); grid.setUseDefaultMargins(false);
        String[] wd={"SEG","TER","QUA","QUI","SEX","SÁB","DOM"};
        for (int i=0;i<7;i++) {
            TextView d=text(wd[i],9,MUTED,true); d.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams(GridLayout.spec(0),GridLayout.spec(i,1f)); gp.width=0; gp.height=dp(30);
            grid.addView(d,gp);
        }
        Calendar first=(Calendar)visibleMonth.clone(); first.set(Calendar.DAY_OF_MONTH,1);
        int dow=first.get(Calendar.DAY_OF_WEEK); int offset=dow==Calendar.SUNDAY?6:dow-Calendar.MONDAY;
        int days=first.getActualMaximum(Calendar.DAY_OF_MONTH);
        for(int cell=0;cell<42;cell++){
            int day=cell-offset+1;
            LinearLayout dayBox=new LinearLayout(this); dayBox.setOrientation(LinearLayout.VERTICAL); dayBox.setGravity(Gravity.CENTER);
            int row=1+cell/7,col=cell%7;
            GridLayout.LayoutParams gp=new GridLayout.LayoutParams(GridLayout.spec(row),GridLayout.spec(col,1f)); gp.width=0; gp.height=dp(54); gp.setMargins(dp(2),dp(2),dp(2),dp(2));
            if(day>=1 && day<=days){
                Calendar dc=(Calendar)visibleMonth.clone(); dc.set(Calendar.DAY_OF_MONTH,day);
                String iso=Store.format(dc.getTime()); boolean selected=iso.equals(selectedAgendaDate); boolean today=iso.equals(Store.today());
                dayBox.setBackground(rounded(selected?BRAND:(today?PANEL2:Color.TRANSPARENT),14,false));
                TextView n=text(String.valueOf(day),12,selected?Color.WHITE:TEXT,today||selected); n.setGravity(Gravity.CENTER); dayBox.addView(n);
                int count=store.taskCountOn(iso);
                if(count>0){ TextView badge=text(String.valueOf(count),9,selected?Color.WHITE:BRAND,true); badge.setGravity(Gravity.CENTER); dayBox.addView(badge); }
                dayBox.setOnClickListener(v->{ selectedAgendaDate=iso; showPage("agenda"); });
            }
            grid.addView(dayBox,gp);
        }
        card.addView(grid);
        return card;
    }

    private View weeklyPlanner() {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(10), dp(11), dp(12));
        String weekStart = Store.startOfWeek(selectedAgendaDate);
        String weekEnd = Store.addDays(weekStart, 6);

        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = flatButton("‹"); Button next = flatButton("›");
        TextView title = text("Semana · " + compactDate(weekStart) + " — " + compactDate(weekEnd), 14, TEXT, true); title.setGravity(Gravity.CENTER);
        head.addView(prev, new LinearLayout.LayoutParams(dp(42),dp(38)));
        head.addView(title, new LinearLayout.LayoutParams(0,dp(38),1f));
        head.addView(next, new LinearLayout.LayoutParams(dp(42),dp(38)));
        prev.setOnClickListener(v -> { selectedAgendaDate = Store.addDays(selectedAgendaDate,-7); syncMonthToSelected(); showPage("agenda"); });
        next.setOnClickListener(v -> { selectedAgendaDate = Store.addDays(selectedAgendaDate,7); syncMonthToSelected(); showPage("agenda"); });
        card.addView(head);

        for (int i = 0; i < 7; i++) {
            String iso = Store.addDays(weekStart, i);
            int count = store.taskCountOn(iso), mins = store.plannedMinutesOn(iso), pct = store.completionPercentOn(iso);
            boolean selected = iso.equals(selectedAgendaDate);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10),dp(9),dp(10),dp(9));
            row.setBackground(rounded(selected ? BRAND : Color.TRANSPARENT, 15, false));

            LinearLayout day = new LinearLayout(this); day.setOrientation(LinearLayout.VERTICAL);
            int primary = selected ? Color.WHITE : TEXT, secondary = selected ? Color.rgb(210,239,229) : MUTED;
            day.addView(text(weekdayShort(iso),10,secondary,true));
            day.addView(text(dayMonth(iso),15,primary,true));
            row.addView(day, new LinearLayout.LayoutParams(dp(70),ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout load = new LinearLayout(this); load.setOrientation(LinearLayout.VERTICAL);
            load.addView(text(count + " tarefa" + (count == 1 ? "" : "s") + " · " + humanMinutes(mins),11,primary,true));
            ProgressBar pb = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100); pb.setProgress(pct);
            pb.setProgressTintList(ColorStateList.valueOf(selected ? ACCENT : BRAND));
            pb.setProgressBackgroundTintList(ColorStateList.valueOf(selected ? Color.argb(50,255,255,255) : PANEL2));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(6)); pp.setMargins(0,dp(6),0,0); load.addView(pb,pp);
            row.addView(load, new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));

            TextView percent = text(pct + "%",11,selected ? Color.WHITE : BRAND,true); percent.setGravity(Gravity.RIGHT);
            row.addView(percent, new LinearLayout.LayoutParams(dp(46),ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOnClickListener(v -> { selectedAgendaDate = iso; syncMonthToSelected(); showPage("agenda"); });
            row.setOnDragListener((v,event)->{
                if(event.getAction()==DragEvent.ACTION_DRAG_ENTERED){v.setAlpha(.72f);return true;}
                if(event.getAction()==DragEvent.ACTION_DRAG_EXITED){v.setAlpha(1f);return true;}
                if(event.getAction()==DragEvent.ACTION_DROP){
                    v.setAlpha(1f);Object state=event.getLocalState();
                    if(state instanceof Store.Task){Store.Task task=(Store.Task)state;task.date=iso;store.save();ReminderScheduler.schedule(this,task);selectedAgendaDate=iso;syncMonthToSelected();showPage("agenda");}
                    return true;
                }
                if(event.getAction()==DragEvent.ACTION_DRAG_ENDED){v.setAlpha(1f);return true;}
                return true;
            });
            card.addView(row, marginBottom(i == 6 ? 0 : dp(3)));
        }
        return card;
    }

    private View agendaItem(Store.Task t) {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.TOP); row.setPadding(0,0,0,dp(9));
        TextView time=text(t.time==null||t.time.isEmpty()?"—":t.time,12,MUTED,true); time.setGravity(Gravity.RIGHT); time.setPadding(0,dp(14),dp(11),0);
        row.addView(time,new LinearLayout.LayoutParams(dp(61),ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout event=cardBox(); event.setOrientation(LinearLayout.VERTICAL); event.setPadding(dp(13),dp(10),dp(8),dp(10));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(t.title,14,TEXT,true);top.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        ImageButton drag=iconButton(R.drawable.ic_drag,MUTED);drag.setBackgroundColor(Color.TRANSPARENT);drag.setContentDescription("Arrastar para outro dia");drag.setPadding(dp(8),dp(8),dp(8),dp(8));
        drag.setOnLongClickListener(v->{ClipData clip=ClipData.newPlainText("task",String.valueOf(t.id));v.startDragAndDrop(clip,new View.DragShadowBuilder(event),t,0);v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);return true;});
        top.addView(drag,new LinearLayout.LayoutParams(dp(36),dp(36)));event.addView(top);
        String status="done".equals(t.status)?"Concluído":"doing".equals(t.status)?"Em andamento":"Planejado";
        String project=t.projectId==0?"":" · "+store.projectTitle(t.projectId);
        event.addView(text(t.category+project+" · "+humanMinutes(t.minutes)+" · "+status,10,MUTED,false));
        if(t.flexible && "none".equals(t.recurrence)){
            String due=t.deadline==null||t.deadline.length()!=10?t.date:t.deadline;
            event.addView(text("✦ Flexível · prazo "+compactDate(due),9,GOOD,true));
        }
        if("week".equals(agendaMode)){TextView hint=text("Segure o ícone ⠿ e solte em outro dia da semana",9,BRAND,false);hint.setPadding(0,dp(4),0,0);event.addView(hint);}
        event.setOnClickListener(v->showTaskDialog(t,false,null)); event.setOnLongClickListener(v->{showTaskActions(t);return true;});
        row.addView(event,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); return row;
    }

    private View buildSettingsPage() {
        LinearLayout body = body();

        body.addView(sectionHeader("Perfil", null, null));
        LinearLayout profile = cardBox();
        profile.setOrientation(LinearLayout.VERTICAL);
        profile.setPadding(dp(15), dp(14), dp(15), dp(14));
        EditText name = input("Como você quer ser chamado?");
        name.setText(userName());
        profile.addView(text("Nome de exibição", 12, TEXT, true));
        TextView help = text("Usado apenas nos cumprimentos do app e salvo localmente.", 10, MUTED, false);
        help.setPadding(0, dp(2), 0, dp(8));
        profile.addView(help);
        profile.addView(name);
        Button saveName = primaryButton("Salvar nome");
        saveName.setOnClickListener(v -> {
            getSharedPreferences("ritmo_ui", MODE_PRIVATE).edit().putString("user_name", name.getText().toString().trim()).apply();
            Toast.makeText(this, "Perfil atualizado.", Toast.LENGTH_SHORT).show();
            updateTopBar();
        });
        profile.addView(saveName);
        body.addView(profile);

        body.addView(sectionHeader("Aparência", null, null));
        LinearLayout appearance = cardBox();
        appearance.setOrientation(LinearLayout.VERTICAL);
        appearance.setPadding(dp(15), dp(14), dp(15), dp(14));
        appearance.addView(text("Tema do aplicativo", 12, TEXT, true));
        TextView themeHelp = text("O modo Sistema acompanha automaticamente o tema do Android.", 10, MUTED, false);
        themeHelp.setPadding(0, dp(2), 0, dp(10));
        appearance.addView(themeHelp);
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] values = {"system","light","dark"};
        String[] labels = {"Sistema","Claro","Escuro"};
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            Button b = chip(labels[i], value.equals(themeMode));
            b.setOnClickListener(v -> {
                getSharedPreferences("ritmo_ui", MODE_PRIVATE).edit().putString("theme_mode", value).apply();
                recreate();
            });
            themeRow.addView(b);
        }
        appearance.addView(themeRow);
        body.addView(appearance);

        body.addView(sectionHeader("Acessibilidade", null, null));
        LinearLayout accessibility = cardBox();
        accessibility.setOrientation(LinearLayout.VERTICAL);
        accessibility.setPadding(dp(15), dp(12), dp(15), dp(12));

        CheckBox reduced = new CheckBox(this);
        reduced.setText("Reduzir animações");
        reduced.setTextColor(TEXT);
        reduced.setTextSize(13);
        reduced.setChecked(reduceMotion);
        reduced.setButtonTintList(ColorStateList.valueOf(BRAND));
        reduced.setPadding(0, dp(4), 0, dp(4));
        reduced.setOnCheckedChangeListener((button, checked) -> {
            reduceMotion = checked;
            getSharedPreferences("ritmo_ui", MODE_PRIVATE).edit().putBoolean("reduce_motion", checked).apply();
        });
        accessibility.addView(reduced);

        CheckBox haptics = new CheckBox(this);
        haptics.setText("Feedback tátil");
        haptics.setTextColor(TEXT);
        haptics.setTextSize(13);
        haptics.setChecked(getSharedPreferences("ritmo_ui", MODE_PRIVATE).getBoolean("haptics", true));
        haptics.setButtonTintList(ColorStateList.valueOf(BRAND));
        haptics.setPadding(0, dp(4), 0, dp(4));
        haptics.setOnCheckedChangeListener((button, checked) ->
                getSharedPreferences("ritmo_ui", MODE_PRIVATE).edit().putBoolean("haptics", checked).apply());
        accessibility.addView(haptics);
        body.addView(accessibility);

        body.addView(sectionHeader("Planejamento", null, null));
        LinearLayout planningPrefs = cardBox();
        planningPrefs.setOrientation(LinearLayout.VERTICAL);
        planningPrefs.setPadding(dp(15), dp(12), dp(15), dp(12));
        CheckBox autoReplan = new CheckBox(this);
        autoReplan.setText("Replanejar pendências flexíveis automaticamente");
        autoReplan.setTextColor(TEXT);
        autoReplan.setTextSize(13);
        autoReplan.setButtonTintList(ColorStateList.valueOf(BRAND));
        autoReplan.setChecked(getSharedPreferences("ritmo_planner_settings", MODE_PRIVATE).getBoolean("autoReplanOverdue", false));
        autoReplan.setOnCheckedChangeListener((button, checked) ->
                getSharedPreferences("ritmo_planner_settings", MODE_PRIVATE).edit().putBoolean("autoReplanOverdue", checked).apply());
        planningPrefs.addView(autoReplan);
        TextView autoHelp = text("Quando ativado, tarefas flexíveis vencidas voltam para a semana atual e passam novamente pelo Planejador.", 10, MUTED, false);
        autoHelp.setPadding(dp(4), 0, dp(4), dp(4));
        planningPrefs.addView(autoHelp);
        body.addView(planningPrefs);

        body.addView(sectionHeader("Lembretes", null, null));
        LinearLayout reminders = cardBox();
        reminders.setOrientation(LinearLayout.VERTICAL);
        reminders.setPadding(dp(15), dp(14), dp(15), dp(14));
        reminders.addView(text("Tarefas e hábitos", 12, TEXT, true));
        reminders.addView(text("Restaure os alarmes locais caso o Android tenha restringido notificações ou após alterações importantes.", 10, MUTED, false));
        Button reschedule = secondaryButton("Reagendar todos os lembretes");
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(10), 0, 0);
        reminders.addView(reschedule, rp);
        reschedule.setOnClickListener(v -> {
            try { ReminderScheduler.rescheduleAll(this, store); } catch (Throwable ignored) { }
            try { RoutineReminderScheduler.rescheduleAll(this, store); } catch (Throwable ignored) { }
            Toast.makeText(this, "Lembretes reagendados.", Toast.LENGTH_SHORT).show();
        });
        body.addView(reminders);

        body.addView(sectionHeader("Sobre", null, null));
        LinearLayout about = cardBox();
        about.setOrientation(LinearLayout.VERTICAL);
        about.setPadding(dp(15), dp(14), dp(15), dp(14));
        about.addView(text("Ritmo 2.3.0", 14, TEXT, true));
        about.addView(text("Gestão de rotina, foco, hábitos e planejamento. Seus dados continuam locais no aparelho.", 10, MUTED, false));
        TextView storage = text(store.tasks.size() + " tarefas · " + store.routines.size() + " hábitos · " + store.focusSessions.size() + " sessões de foco", 10, BRAND, true);
        storage.setPadding(0, dp(8), 0, 0);
        about.addView(storage);
        body.addView(about);

        return wrapScroll(body);
    }

    private View buildOrganizePage() {
        LinearLayout body=body();
        body.addView(sectionHeader("Progresso & organização",null,null));
        body.addView(organizeTabs());
        if("planner".equals(organizeTab)) body.addView(plannerSection());
        else if("projects".equals(organizeTab)) body.addView(projectsSection());
        else if("goals".equals(organizeTab)) body.addView(goalsSection());
        else if("habits".equals(organizeTab)) body.addView(habitsSection());
        else if("stats".equals(organizeTab)) body.addView(statsSection());
        else body.addView(kanbanBoard());
        return wrapScroll(body);
    }

    private View organizeTabs(){
        HorizontalScrollView hs=new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        String[][] tabs={{"stats","Estatísticas"},{"habits","Hábitos"},{"planner","Planejador"},{"kanban","Kanban"},{"projects","Projetos"},{"goals","Metas"}};
        for(String[] t:tabs){ Button b=chip(t[1],organizeTab.equals(t[0])); b.setOnClickListener(v->{organizeTab=t[0];showPage("organize");}); row.addView(b); }
        hs.addView(row); hs.setPadding(0,0,0,dp(10)); return hs;
    }

    private View kanbanBoard(){
        LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout hint = new LinearLayout(this); hint.setGravity(Gravity.CENTER_VERTICAL); hint.setPadding(dp(3),0,dp(3),dp(8));
        ImageView dragIcon = new ImageView(this); dragIcon.setImageResource(R.drawable.ic_drag); dragIcon.setColorFilter(BRAND);
        hint.addView(dragIcon,new LinearLayout.LayoutParams(dp(18),dp(18)));
        TextView ht = text("Segure um card e arraste para outra coluna",10,MUTED,false); ht.setPadding(dp(7),0,0,0); hint.addView(ht);
        wrap.addView(hint);

        HorizontalScrollView hs=new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout board=new LinearLayout(this); board.setOrientation(LinearLayout.HORIZONTAL);
        String[][] columns={{"todo","A FAZER"},{"doing","EM ANDAMENTO"},{"done","CONCLUÍDO"}};
        for(String[] col:columns){
            final String targetStatus = col[0];
            LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(11),dp(11),dp(11),dp(11)); box.setBackground(rounded(PANEL2,20,false));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(isTablet()?dp(310):dp(276),ViewGroup.LayoutParams.WRAP_CONTENT); bp.setMargins(0,0,dp(12),0); board.addView(box,bp);
            int count=0; for(Store.Task t:store.tasks) if(targetStatus.equals(t.status)) count++;
            box.addView(text(col[1]+"  ·  "+count,11,MUTED,true));
            box.setOnDragListener((v,event)->{
                if(event.getAction()==DragEvent.ACTION_DRAG_ENTERED){v.setAlpha(.78f);return true;}
                if(event.getAction()==DragEvent.ACTION_DRAG_EXITED){v.setAlpha(1f);return true;}
                if(event.getAction()==DragEvent.ACTION_DROP){
                    v.setAlpha(1f);
                    Object state=event.getLocalState();
                    if(state instanceof Store.Task){
                        Store.Task task=(Store.Task)state;
                        store.setTaskStatus(task,targetStatus);
                        if("done".equals(targetStatus)) ReminderScheduler.cancel(this,task.id); else ReminderScheduler.schedule(this,task);
                        showPage("organize");
                    }
                    return true;
                }
                if(event.getAction()==DragEvent.ACTION_DRAG_ENDED){v.setAlpha(1f);return true;}
                return true;
            });
            for(Store.Task t:store.tasks){
                if(!targetStatus.equals(t.status)) continue;
                LinearLayout k=cardBox(); k.setOrientation(LinearLayout.VERTICAL); k.setPadding(dp(11),dp(10),dp(9),dp(9));
                LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);
                txt.addView(text(t.title,13,TEXT,true));
                String meta=t.category+" · "+compactDate(t.date)+(t.projectId!=0?" · "+store.projectTitle(t.projectId):"");
                txt.addView(text(meta,10,MUTED,false));
                top.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
                ImageButton more=iconButton(R.drawable.ic_more,MUTED);more.setBackgroundColor(Color.TRANSPARENT);more.setPadding(dp(8),dp(8),dp(8),dp(8));more.setOnClickListener(v->showTaskActions(t));
                top.addView(more,new LinearLayout.LayoutParams(dp(36),dp(36)));k.addView(top);
                if(t.subtasks!=null&&!t.subtasks.isEmpty()){
                    int done=t.completedSubtasks();
                    ProgressBar sp=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);sp.setMax(t.subtasks.size());sp.setProgress(done);sp.setProgressTintList(ColorStateList.valueOf(BRAND));sp.setProgressBackgroundTintList(ColorStateList.valueOf(PANEL2));
                    LinearLayout.LayoutParams spp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(5));spp.setMargins(0,dp(7),0,0);k.addView(sp,spp);
                    TextView st=text(done+"/"+t.subtasks.size()+" subtarefas",9,MUTED,false);st.setPadding(0,dp(4),0,0);k.addView(st);
                }
                TextView drag=text("Segure para mover",9,BRAND,false);drag.setPadding(0,dp(6),0,0);k.addView(drag);
                LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);kp.setMargins(0,dp(8),0,0);box.addView(k,kp);
                k.setOnClickListener(v->showTaskDialog(t,false,null));
                k.setOnLongClickListener(v->{
                    ClipData clip=ClipData.newPlainText("task",String.valueOf(t.id));
                    v.startDragAndDrop(clip,new View.DragShadowBuilder(v),t,0);
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    return true;
                });
            }
        }
        hs.addView(board); wrap.addView(hs); return wrap;
    }

    private View projectsSection(){
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);
        Button add=primaryButton("+ Novo projeto");add.setOnClickListener(v->showProjectDialog(null));wrap.addView(add,marginBottom(dp(10)));
        if(store.projects.isEmpty()){wrap.addView(emptyCard("Nenhum projeto criado."));return wrap;}
        for(Store.Project p:store.projects){
            LinearLayout card=cardBox();card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(13),dp(12),dp(12));
            LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text(p.title,14,TEXT,true));
            if(p.description!=null&&!p.description.isEmpty()) info.addView(text(p.description,10,MUTED,false));
            String meta=store.projectTaskCount(p.id)+" tarefas · "+store.projectDoneCount(p.id)+" concluídas"+(p.targetDate==null||p.targetDate.isEmpty()?"":" · prazo "+compactDate(p.targetDate));
            info.addView(text(meta,10,MUTED,false));
            top.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            ImageButton more=iconButton(R.drawable.ic_more,MUTED);more.setBackgroundColor(Color.TRANSPARENT);more.setOnClickListener(v->showProjectActions(p));top.addView(more,new LinearLayout.LayoutParams(dp(38),dp(38)));card.addView(top);
            ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(100);pb.setProgress(store.projectProgress(p.id));pb.setProgressTintList(ColorStateList.valueOf(BRAND));pb.setProgressBackgroundTintList(ColorStateList.valueOf(PANEL2));
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(7));pp.setMargins(0,dp(10),0,0);card.addView(pb,pp);
            TextView pct=text(store.projectProgress(p.id)+"%",10,BRAND,true);pct.setGravity(Gravity.RIGHT);pct.setPadding(0,dp(4),0,0);card.addView(pct);
            card.setOnClickListener(v->showProjectTasks(p));wrap.addView(card,marginBottom(dp(9)));
        }
        return wrap;
    }

    private View goalsSection(){
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);
        Button add=primaryButton("+ Nova meta"); add.setOnClickListener(v->showGoalDialog(null)); wrap.addView(add,marginBottom(dp(10)));
        wrap.addView(goalsCard(store.goals.size())); return wrap;
    }

    private View goalsCard(int limit){
        LinearLayout card=cardBox();card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(15),dp(13),dp(15),dp(13));
        if(store.goals.isEmpty()){card.addView(text("Nenhuma meta cadastrada.",13,MUTED,false));return card;}
        int max=Math.min(limit,store.goals.size());
        for(int i=0;i<max;i++){
            Store.Goal g=store.goals.get(i); LinearLayout goal=new LinearLayout(this); goal.setOrientation(LinearLayout.VERTICAL); goal.setPadding(0,i==0?dp(3):dp(14),0,dp(12));
            LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout name=new LinearLayout(this); name.setOrientation(LinearLayout.VERTICAL); name.addView(text(g.title,13,TEXT,true));
            if(g.targetDate!=null&&!g.targetDate.isEmpty()) name.addView(text("Prazo · "+compactDate(g.targetDate),10,MUTED,false));
            top.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); top.addView(text(g.progress+"%",13,BRAND,true)); goal.addView(top);
            ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(100);pb.setProgress(clamp(g.progress,0,100));pb.setProgressTintList(ColorStateList.valueOf(BRAND));pb.setProgressBackgroundTintList(ColorStateList.valueOf(PANEL2));
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(8));pp.setMargins(0,dp(9),0,0);goal.addView(pb,pp);
            goal.setOnClickListener(v->showGoalDialog(g)); goal.setOnLongClickListener(v->{confirmDeleteGoal(g);return true;}); card.addView(goal);
            if(i<max-1) card.addView(divider());
        }
        return card;
    }

    private View habitsSection(){
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);
        Button add=primaryButton("+ Novo hábito");
        add.setOnClickListener(v->showRoutineDialog(null));
        wrap.addView(add,marginBottom(dp(10)));

        if(store.routines.isEmpty()){
            wrap.addView(emptyState(R.drawable.ic_routine, "Nenhum hábito cadastrado", "Crie uma rotina simples e repita até ela ficar automática.", "Criar hábito", v -> showRoutineDialog(null)));
            return wrap;
        }

        LinearLayout card=cardBox();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(6),dp(14),dp(6));
        for(int i=0;i<store.routines.size();i++){
            Store.Routine r=store.routines.get(i);
            card.addView(routineRow(r, false));
            if(i<store.routines.size()-1)card.addView(divider());
        }
        wrap.addView(card);
        return wrap;
    }

    private View statsSection(){
        LinearLayout wrap=new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(metric(store.completionRateLast7()+"%","Conclusão"),weightedMargin(0,4));
        row1.addView(metric(store.executionEfficiencyLast7()+"%","Execução real"),weightedMargin(4,0));
        wrap.addView(row1,marginBottom(dp(8)));

        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(metric(store.averageScoreLast30()+"%","Consistência 30d"),weightedMargin(0,4));
        row2.addView(metric(humanMinutes(store.focusMinutesLast7()),"Foco 7d"),weightedMargin(4,0));
        wrap.addView(row2,marginBottom(dp(8)));

        LinearLayout row3=new LinearLayout(this);row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(metric(String.valueOf(store.overdueOpenCount()),"Atrasadas"),weightedMargin(0,4));
        row3.addView(metric(store.bestCompletionDayLast7(),"Melhor dia"),weightedMargin(4,0));
        wrap.addView(row3);

        TextView h0=text("Consistência mensal",16,TEXT,true);
        h0.setPadding(dp(3),dp(20),0,dp(8));
        wrap.addView(h0);
        wrap.addView(monthlyConsistencyCard());

        TextView hf=text("Sessões de foco",16,TEXT,true);
        hf.setPadding(dp(3),dp(20),0,dp(8));
        wrap.addView(hf);
        wrap.addView(focusStatsCard());

        TextView h1=text("Produtividade semanal",16,TEXT,true);
        h1.setPadding(dp(3),dp(20),0,dp(8));
        wrap.addView(h1);
        wrap.addView(weeklyChartCard());

        TextView h2=text("Distribuição por categoria",16,TEXT,true);
        h2.setPadding(dp(3),dp(20),0,dp(8));
        wrap.addView(h2);
        wrap.addView(categoryDistributionCard());

        TextView h3=text("Leitura da semana",16,TEXT,true);
        h3.setPadding(dp(3),dp(20),0,dp(8));
        wrap.addView(h3);
        wrap.addView(insightsCard());
        return wrap;
    }

    private View monthlyConsistencyCard() {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(12));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("Últimos 30 dias", 13, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(text(store.averageScoreLast30() + "% média", 11, BRAND, true));
        card.addView(top);
        MonthlyHeatmapView heat = new MonthlyHeatmapView(this);
        heat.setData(store.last30Scores());
        heat.setColors(BRAND, PANEL2, MUTED);
        heat.setContentDescription("Mapa de consistência dos últimos 30 dias");
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
        hp.setMargins(0, dp(10), 0, 0);
        card.addView(heat, hp);
        card.addView(text("A intensidade representa a combinação entre tarefas e hábitos concluídos em cada dia.", 9, MUTED, false));
        return card;
    }

    private View focusStatsCard() {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(text(store.focusSessionCountLast7() + " sessões nesta semana", 13, TEXT, true));
        String adherence = store.focusPlannedMinutesLast7() > 0 ? " · " + store.focusAdherenceLast7() + "% do planejado" : "";
        info.addView(text(humanMinutes(store.focusMinutesLast7()) + " de foco registrado" + adherence, 10, MUTED, false));
        top.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button free = secondaryButton("Iniciar foco");
        free.setOnClickListener(v -> startFocus(null));
        top.addView(free, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        card.addView(top);

        WeeklyBarChart chart = new WeeklyBarChart(this);
        chart.setColors(WARN, MUTED, PANEL2);
        chart.setData(store.last7FocusMinutes(), store.last7Labels());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(145));
        cp.setMargins(0, dp(10), 0, 0);
        card.addView(chart, cp);
        return card;
    }

    private View categoryDistributionCard(){
        LinearLayout card=cardBox();card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(15),dp(14),dp(15),dp(14));
        Map<String,Integer> map=store.categoryMinutesLast7();
        if(map.isEmpty()){card.addView(text("Conclua tarefas para ver sua distribuição de tempo.",12,MUTED,false));return card;}
        int max=1,total=0;for(int v:map.values()){max=Math.max(max,v);total+=v;}
        for(Map.Entry<String,Integer> e:map.entrySet()){
            LinearLayout top=new LinearLayout(this);top.addView(text(e.getKey(),12,TEXT,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));top.addView(text(humanMinutes(e.getValue()),11,MUTED,true));card.addView(top);
            ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(max);pb.setProgress(e.getValue());pb.setProgressTintList(ColorStateList.valueOf(BRAND));pb.setProgressBackgroundTintList(ColorStateList.valueOf(PANEL2));
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(7));pp.setMargins(0,dp(6),0,dp(12));card.addView(pb,pp);
        }
        card.addView(text("Total concluído · "+humanMinutes(total),11,MUTED,false));return card;
    }


    private SmartPlanner.Settings plannerSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("ritmo_planner_settings", MODE_PRIVATE);
        return new SmartPlanner.Settings(
                prefs.getInt("startHour", 8),
                prefs.getInt("endHour", 22),
                prefs.getInt("capacityMinutes", 360),
                prefs.getBoolean("includeWeekend", true)
        );
    }

    private int plannerLoadMinutes(String date) {
        int total = 0;
        for (Store.Task t : store.tasks) {
            if (!"done".equals(t.status) && date.equals(t.date)) total += Math.max(0, t.minutes);
        }
        for (Store.Routine r : store.routines) {
            if (r.dueOn(date) && !r.doneOn(date)) total += Math.max(0, r.minutes);
        }
        return total;
    }

    private int flexibleOpenCount() {
        int total = 0;
        for (Store.Task t : store.tasks) {
            if (!"done".equals(t.status) && t.flexible && "none".equals(t.recurrence)) total++;
        }
        return total;
    }

    private View plannerSection() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        SmartPlanner.Settings settings = plannerSettings();
        int flexible = flexibleOpenCount();
        int overloaded = 0;
        for (int i = 0; i < 7; i++) {
            if (plannerLoadMinutes(Store.addDays(Store.today(), i)) > settings.capacityMinutes) overloaded++;
        }

        LinearLayout hero = cardBox();
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16), dp(15), dp(16), dp(15));
        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.addView(text("Planejamento inteligente", 17, TEXT, true));
        heroText.addView(text("Distribui tarefas flexíveis sem mexer em compromissos fixos.", 11, MUTED, false));
        heroTop.addView(heroText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView magic = new ImageView(this);
        magic.setImageResource(R.drawable.ic_auto);
        magic.setColorFilter(BRAND);
        heroTop.addView(magic, new LinearLayout.LayoutParams(dp(28), dp(28)));
        hero.addView(heroTop);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(0, dp(13), 0, 0);
        metrics.addView(metric(String.valueOf(flexible), "Flexíveis"), weightedMargin(0, 4));
        metrics.addView(metric(humanMinutes(settings.capacityMinutes), "Capacidade/dia"), weightedMargin(4, 4));
        metrics.addView(metric(String.valueOf(overloaded), "Sobrecargas"), weightedMargin(4, 0));
        hero.addView(metrics);

        Button plan = primaryButton("Distribuir semana automaticamente");
        plan.setEnabled(flexible > 0);
        plan.setAlpha(flexible > 0 ? 1f : .55f);
        LinearLayout.LayoutParams planParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        planParams.setMargins(0, dp(13), 0, 0);
        hero.addView(plan, planParams);
        plan.setOnClickListener(v -> runSmartPlanner());

        Button settingsButton = secondaryButton("Configurar disponibilidade");
        LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        setParams.setMargins(0, dp(8), 0, 0);
        hero.addView(settingsButton, setParams);
        settingsButton.setOnClickListener(v -> showPlannerSettingsDialog());

        if (SmartPlanner.canUndo(this)) {
            Button undo = secondaryButton("↶ Desfazer último planejamento");
            LinearLayout.LayoutParams undoParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            undoParams.setMargins(0, dp(7), 0, 0);
            hero.addView(undo, undoParams);
            undo.setOnClickListener(v -> undoLastPlan());
        }
        wrap.addView(hero, marginBottom(dp(12)));

        TextView weekTitle = text("CARGA DOS PRÓXIMOS 7 DIAS", 11, MUTED, true);
        weekTitle.setPadding(dp(3), dp(8), 0, dp(8));
        wrap.addView(weekTitle);

        LinearLayout weekCard = cardBox();
        weekCard.setOrientation(LinearLayout.VERTICAL);
        weekCard.setPadding(dp(12), dp(8), dp(12), dp(8));
        for (int i = 0; i < 7; i++) {
            String date = Store.addDays(Store.today(), i);
            weekCard.addView(plannerDayRow(date, settings.capacityMinutes));
            if (i < 6) weekCard.addView(divider());
        }
        wrap.addView(weekCard, marginBottom(dp(12)));

        TextView flexTitle = text("TAREFAS FLEXÍVEIS", 11, MUTED, true);
        flexTitle.setPadding(dp(3), dp(8), 0, dp(8));
        wrap.addView(flexTitle);

        LinearLayout flexCard = cardBox();
        flexCard.setOrientation(LinearLayout.VERTICAL);
        flexCard.setPadding(dp(13), dp(8), dp(13), dp(8));
        List<Store.Task> flex = new ArrayList<>();
        for (Store.Task t : store.tasks) {
            if (!"done".equals(t.status) && t.flexible && "none".equals(t.recurrence)) flex.add(t);
        }
        Collections.sort(flex, Comparator.comparing(t -> safe(t.deadline, t.date)));
        if (flex.isEmpty()) {
            flexCard.addView(text("Nenhuma tarefa flexível. Ao criar ou editar uma tarefa, escolha “Flexível” em Planejamento.", 12, MUTED, false));
        } else {
            int shown = 0;
            for (Store.Task t : flex) {
                if (shown++ >= 8) break;
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(8), 0, dp(8));
                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.addView(text(t.title, 13, TEXT, true));
                String deadline = t.deadline == null || t.deadline.length() != 10 ? t.date : t.deadline;
                info.addView(text("Prazo " + compactDate(deadline) + " · " + humanMinutes(t.minutes) + " · " + priorityLabel(t.effectivePriority()), 10, MUTED, false));
                row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView day = text(compactDate(t.date), 10, BRAND, true);
                row.addView(day);
                row.setOnClickListener(v -> showTaskDialog(t, false, null));
                flexCard.addView(row);
                if (shown < Math.min(8, flex.size())) flexCard.addView(divider());
            }
        }
        wrap.addView(flexCard, marginBottom(dp(12)));

        LinearLayout note = cardBox();
        note.setOrientation(LinearLayout.VERTICAL);
        note.setPadding(dp(14), dp(13), dp(14), dp(13));
        note.addView(text("Como o Ritmo distribui", 13, TEXT, true));
        note.addView(text("Prioridade e prazo vêm primeiro. Depois, o app procura o dia menos carregado e o primeiro horário livre dentro da sua disponibilidade. Hábitos também contam na carga diária. Tarefas recorrentes e compromissos fixos nunca são movidos automaticamente.", 11, MUTED, false));
        wrap.addView(note);
        return wrap;
    }

    private View plannerDayRow(String date, int capacity) {
        int load = plannerLoadMinutes(date);
        int percent = capacity <= 0 ? 0 : Math.round(load * 100f / capacity);
        int color = percent > 100 ? BAD : percent >= 80 ? WARN : GOOD;
        String status = percent > 100 ? "Sobrecarga" : percent >= 80 ? "Cheio" : percent >= 50 ? "Equilibrado" : "Leve";

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));

        LinearLayout day = new LinearLayout(this);
        day.setOrientation(LinearLayout.VERTICAL);
        day.addView(text(weekdayShort(date), 10, MUTED, true));
        day.addView(text(dayMonth(date), 14, TEXT, true));
        row.addView(day, new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        LinearLayout labels = new LinearLayout(this);
        labels.addView(text(humanMinutes(load) + " / " + humanMinutes(capacity), 11, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        labels.addView(text(status, 10, color, true));
        middle.addView(labels);
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(Math.min(100, percent));
        pb.setProgressTintList(ColorStateList.valueOf(color));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(PANEL2));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7));
        pp.setMargins(0, dp(6), 0, 0);
        middle.addView(pb, pp);
        row.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(v -> {
            selectedAgendaDate = date;
            syncMonthToSelected();
            agendaMode = "week";
            showPage("agenda");
        });
        return row;
    }

    private void showPlannerSettingsDialog() {
        SmartPlanner.Settings current = plannerSettings();
        LinearLayout form = dialogForm();

        String[] capacities = {"2 h", "3 h", "4 h", "5 h", "6 h", "7 h", "8 h", "10 h"};
        int[] capacityValues = {120, 180, 240, 300, 360, 420, 480, 600};
        Spinner capacity = spinner(capacities);
        int capIndex = 4;
        for (int i = 0; i < capacityValues.length; i++) if (capacityValues[i] == current.capacityMinutes) capIndex = i;
        capacity.setSelection(capIndex);

        String[] startHours = {"05:00","06:00","07:00","08:00","09:00","10:00","11:00","12:00"};
        String[] endHours = {"16:00","17:00","18:00","19:00","20:00","21:00","22:00","23:00","24:00"};
        Spinner start = spinner(startHours);
        Spinner end = spinner(endHours);
        setSpinner(start, String.format(Locale.US, "%02d:00", current.startHour));
        setSpinner(end, String.format(Locale.US, "%02d:00", current.endHour));

        Spinner weekend = spinner(new String[]{"Seg a Dom", "Somente Seg a Sex"});
        setSpinner(weekend, current.includeWeekend ? "Seg a Dom" : "Somente Seg a Sex");

        form.addView(fieldLabel("CAPACIDADE PRODUTIVA POR DIA"));
        form.addView(capacity);
        form.addView(formRow(fieldBox("INÍCIO", start), fieldBox("FIM", end)));
        form.addView(fieldLabel("DIAS DISPONÍVEIS"));
        form.addView(weekend);

        TextView tip = text("Capacidade produtiva não precisa ser igual ao tempo acordado. Use apenas as horas que você realmente quer reservar para tarefas, estudos e projetos.", 10, MUTED, false);
        tip.setPadding(0, dp(10), 0, 0);
        form.addView(tip);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Disponibilidade do planejador")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int startHour = Integer.parseInt(String.valueOf(start.getSelectedItem()).substring(0, 2));
            String endValue = String.valueOf(end.getSelectedItem());
            int endHour = Integer.parseInt(endValue.substring(0, 2));
            if (endHour == 0) endHour = 24;
            if (endHour <= startHour) {
                Toast.makeText(this, "O horário final precisa ser depois do inicial.", Toast.LENGTH_LONG).show();
                return;
            }
            int capacityMinutes = capacityValues[capacity.getSelectedItemPosition()];
            boolean includeWeekend = weekend.getSelectedItemPosition() == 0;
            getSharedPreferences("ritmo_planner_settings", MODE_PRIVATE).edit()
                    .putInt("startHour", startHour)
                    .putInt("endHour", endHour)
                    .putInt("capacityMinutes", capacityMinutes)
                    .putBoolean("includeWeekend", includeWeekend)
                    .apply();
            dlg.dismiss();
            organizeTab = "planner";
            showPage("organize");
        });
    }

    private void runSmartPlanner() {
        SmartPlanner.Settings settings = plannerSettings();
        SmartPlanner.Result result = SmartPlanner.plan(store, settings);
        if (result.isEmpty()) {
            Toast.makeText(this, "Não há tarefas flexíveis para distribuir.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder preview = new StringBuilder();
        preview.append(result.eligibleTasks).append(" tarefa(s) flexível(is) serão analisadas");
        preview.append("\n").append(result.movedTasks).append(" terão dia ou horário ajustado.");
        if (result.overloadedDays > 0) preview.append("\n\nAinda restarão ").append(result.overloadedDays).append(" dia(s) acima da capacidade.");
        preview.append("\n\n");
        int shown = 0;
        for (SmartPlanner.Assignment a : result.assignments) {
            if (shown++ >= 5) break;
            preview.append("• ").append(a.task.title).append("\n  ")
                    .append(compactDate(a.newDate))
                    .append(a.newTime.isEmpty() ? " · sem horário livre" : " · " + a.newTime)
                    .append(a.overCapacity ? " · atenção" : "")
                    .append("\n");
        }
        if (result.assignments.size() > 5) preview.append("… e mais ").append(result.assignments.size() - 5).append(" tarefa(s).");

        new AlertDialog.Builder(this)
                .setTitle("Aplicar planejamento?")
                .setMessage(preview.toString())
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Aplicar", (d, w) -> {
                    SmartPlanner.apply(this, store, result);
                    for (SmartPlanner.Assignment a : result.assignments) ReminderScheduler.schedule(this, a.task);
                    Toast.makeText(this, "Semana reorganizada. Você pode desfazer enquanto não fizer outro planejamento.", Toast.LENGTH_LONG).show();
                    organizeTab = "planner";
                    showPage("organize");
                })
                .show();
    }

    private void undoLastPlan() {
        new AlertDialog.Builder(this)
                .setTitle("Desfazer planejamento?")
                .setMessage("As tarefas voltarão aos dias e horários em que estavam antes da última distribuição automática.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Desfazer", (d, w) -> {
                    int restored = SmartPlanner.undo(this, store);
                    try { ReminderScheduler.rescheduleAll(this, store); } catch (Throwable ignored) { }
                    try { RoutineReminderScheduler.rescheduleAll(this, store); } catch (Throwable ignored) { }
                    Toast.makeText(this, restored + " tarefa(s) restaurada(s).", Toast.LENGTH_SHORT).show();
                    organizeTab = "planner";
                    showPage("organize");
                })
                .show();
    }

    private View insightsCard(){
        LinearLayout card=cardBox();card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(15),dp(14),dp(15),dp(14));
        int pending=0,high=0,todayMin=0;for(Store.Task t:store.tasks){if(!"done".equals(t.status)){pending++;if("high".equals(t.effectivePriority()))high++;}if(Store.today().equals(t.date))todayMin+=t.minutes;}
        card.addView(insight("Prioridades",high>0?high+" tarefa(s) de prioridade alta ainda abertas.":"Nenhuma tarefa crítica aberta agora."));card.addView(divider());
        card.addView(insight("Carga de hoje",todayMin>480?"Você planejou "+humanMinutes(todayMin)+". Vale redistribuir parte da carga.":"Sua carga planejada é de "+humanMinutes(todayMin)+" e está administrável."));card.addView(divider());
        card.addView(insight("Fila total",pending+" tarefa(s) continuam abertas. Evite adicionar novas demandas sem revisar essa fila."));return card;
    }

    private View insight(String title,String msg){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,dp(10),0,dp(10));box.addView(text(title,12,TEXT,true));box.addView(text(msg,11,MUTED,false));return box;}

    private void showAddMenu(){
        String[] items={"Nova tarefa","Novo compromisso","Novo projeto","Nova meta","Novo hábito"};
        new AlertDialog.Builder(this).setTitle("Adicionar").setItems(items,(d,which)->{
            if(which==0)showTaskDialog(null,false,null);
            else if(which==1)showTaskDialog(null,true,null);
            else if(which==2)showProjectDialog(null);
            else if(which==3)showGoalDialog(null);
            else showRoutineDialog(null);
        }).show();
    }

    private void showTaskDialog(Store.Task existing, boolean eventMode, String forcedDate){
        boolean editing=existing!=null;
        ScrollView scroll=new ScrollView(this); LinearLayout form=dialogForm(); scroll.addView(form);
        EditText title=input("Título");
        EditText description=inputMulti("Descrição / observações");
        EditText date=input("Data planejada"); date.setFocusable(false);
        EditText time=input("Horário"); time.setFocusable(false);
        EditText deadline=input("Prazo"); deadline.setFocusable(false);
        EditText minutes=input("Minutos"); minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        Spinner category=spinner(new String[]{"Estudos","Trabalho","Pessoal","Projeto","Saúde","Financeiro"});
        Spinner priority=spinner(new String[]{"Automática","Baixa","Média","Alta"});
        Spinner planning=spinner(new String[]{"Flexível · Ritmo pode mover","Fixo · não mover"});
        Spinner recurrence=spinner(new String[]{"Sem repetição","Todos os dias","Seg a Sex","Semanal","Mensal"});
        Spinner reminder=spinner(new String[]{"Sem lembrete","Na hora","10 min antes","30 min antes","1 h antes","1 dia antes"});
        Spinner project=spinner(projectLabels());

        form.addView(fieldLabel("TÍTULO")); form.addView(title);
        form.addView(fieldLabel("DESCRIÇÃO")); form.addView(description);
        form.addView(formRow(fieldBox("DATA",date),fieldBox("HORÁRIO",time)));
        form.addView(formRow(fieldBox("PRAZO",deadline),fieldBox("DURAÇÃO",minutes)));
        form.addView(formRow(fieldBox("CATEGORIA",category),fieldBox("PROJETO",project)));
        form.addView(formRow(fieldBox("PRIORIDADE",priority),fieldBox("PLANEJAMENTO",planning)));
        form.addView(formRow(fieldBox("REPETIÇÃO",recurrence),fieldBox("LEMBRETE",reminder)));

        TextView plannerHint=text("Flexível: o Planejador pode mudar o dia e horário sem ultrapassar o prazo. Tarefas recorrentes são mantidas como fixas.",9,MUTED,false);
        plannerHint.setPadding(dp(2),dp(5),dp(2),dp(8));
        form.addView(plannerHint);

        EditText newSubtasks = inputMulti("Uma subtarefa por linha");
        if(editing){
            Button manage=secondaryButton("Subtarefas · "+existing.completedSubtasks()+"/"+existing.subtasks.size());
            manage.setOnClickListener(v->showSubtaskDialog(existing));
            form.addView(fieldLabel("SUBTAREFAS")); form.addView(manage,marginBottom(dp(8)));
        }else{
            form.addView(fieldLabel("SUBTAREFAS (OPCIONAL)"));form.addView(newSubtasks);
        }

        String initialDate=forcedDate!=null?forcedDate:Store.today();
        date.setText(initialDate);
        deadline.setText(initialDate);
        minutes.setText(eventMode?"60":"30");
        setSpinner(priority,"Automática");
        setSpinner(planning,eventMode?"Fixo · não mover":"Flexível · Ritmo pode mover");
        if(editing){
            title.setText(existing.title);description.setText(existing.description);date.setText(existing.date);time.setText(existing.time);minutes.setText(String.valueOf(existing.minutes));
            deadline.setText(existing.deadline==null||existing.deadline.length()!=10?existing.date:existing.deadline);
            setSpinner(category,existing.category);setSpinner(priority,priorityLabel(existing.priority));setSpinner(planning,existing.flexible?"Flexível · Ritmo pode mover":"Fixo · não mover");
            setSpinner(recurrence,recurrenceLabel(existing.recurrence));setSpinner(reminder,reminderLabel(existing.reminderMinutes));setSpinner(project,projectLabel(existing.projectId));
        }
        date.setOnClickListener(v->pickDate(date));
        time.setOnClickListener(v->pickTime(time));
        deadline.setOnClickListener(v->pickDate(deadline));

        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(editing?"Editar tarefa":(eventMode?"Novo compromisso":"Nova tarefa")).setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null);
        if(editing) builder.setNeutralButton("Excluir",null);
        AlertDialog dlg=builder.create();
        dlg.show();
        if(editing){Button del=dlg.getButton(AlertDialog.BUTTON_NEUTRAL);del.setTextColor(BAD);del.setOnClickListener(v->{dlg.dismiss();confirmDeleteTask(existing);});}
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String ttl=title.getText().toString().trim();if(ttl.isEmpty()){title.setError("Informe um título");return;}
            String plannedDate=date.getText().toString();
            String dueDate=deadline.getText().toString();
            if(dueDate==null||dueDate.length()!=10)dueDate=plannedDate;
            if(dueDate.compareTo(plannedDate)<0){deadline.setError("O prazo não pode ser anterior à data planejada");Toast.makeText(this,"Ajuste o prazo da tarefa.",Toast.LENGTH_SHORT).show();return;}
            int mins=Math.max(0,parseInt(minutes.getText().toString(),30));long projectId=projectIdFromLabel(String.valueOf(project.getSelectedItem()));
            String recurrenceValue=recurrenceValue(String.valueOf(recurrence.getSelectedItem()));
            boolean flexible=planning.getSelectedItemPosition()==0 && "none".equals(recurrenceValue);
            if(planning.getSelectedItemPosition()==0 && !"none".equals(recurrenceValue))Toast.makeText(this,"Tarefas recorrentes ficam fixas para preservar a repetição.",Toast.LENGTH_LONG).show();
            if(editing){
                existing.title=ttl;existing.description=description.getText().toString().trim();existing.date=plannedDate;existing.time=time.getText().toString();existing.deadline=dueDate;existing.flexible=flexible;existing.minutes=mins;existing.category=String.valueOf(category.getSelectedItem());existing.projectId=projectId;existing.priority=priorityValueFromLabel(String.valueOf(priority.getSelectedItem()));existing.recurrence=recurrenceValue;existing.reminderMinutes=reminderValue(String.valueOf(reminder.getSelectedItem()));store.save();ReminderScheduler.schedule(this,existing);
            }else{
                Store.Task t=new Store.Task(System.currentTimeMillis(),ttl,description.getText().toString().trim(),plannedDate,time.getText().toString(),priorityValueFromLabel(String.valueOf(priority.getSelectedItem())),mins,String.valueOf(category.getSelectedItem()),"todo",recurrenceValue,reminderValue(String.valueOf(reminder.getSelectedItem())),projectId,dueDate,flexible);
                addSubtasksFromLines(t,newSubtasks.getText().toString());store.tasks.add(t);store.save();ReminderScheduler.schedule(this,t);
            }
            dlg.dismiss();showPage(currentPage);
        });
    }

    private void addSubtasksFromLines(Store.Task task,String raw){
        if(raw==null)return;for(String line:raw.split("\\n")){String t=line.trim();if(!t.isEmpty())task.subtasks.add(new Store.Subtask(System.nanoTime(),t,false));}
    }

    private void showSubtaskDialog(Store.Task task){
        ScrollView scroll=new ScrollView(this);LinearLayout form=dialogForm();scroll.addView(form);
        final AlertDialog[] holder=new AlertDialog[1];
        if(task.subtasks.isEmpty()) form.addView(text("Nenhuma subtarefa ainda.",12,MUTED,false),marginBottom(dp(8)));
        for(Store.Subtask sub:new ArrayList<>(task.subtasks)){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(5),0,dp(5));
            Button check=smallCheck(sub.done);
            TextView name=text(sub.title,13,TEXT,true);if(sub.done)name.setAlpha(.5f);name.setPadding(dp(10),0,dp(8),0);
            check.setOnClickListener(v->{sub.done=!sub.done;store.save();check.setText(sub.done?"✓":"");check.setBackground(rounded(sub.done?GOOD:PANEL2,10,!sub.done));name.setAlpha(sub.done?.5f:1f);});
            row.addView(check,new LinearLayout.LayoutParams(dp(34),dp(34)));
            row.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            ImageButton del=iconButton(R.drawable.ic_delete,BAD);del.setBackgroundColor(Color.TRANSPARENT);del.setPadding(dp(8),dp(8),dp(8),dp(8));
            del.setOnClickListener(v->{task.subtasks.remove(sub);store.save();if(holder[0]!=null)holder[0].dismiss();showSubtaskDialog(task);});
            row.addView(del,new LinearLayout.LayoutParams(dp(38),dp(38)));form.addView(row);
        }
        Button add=secondaryButton("+ Adicionar subtarefa");add.setOnClickListener(v->{if(holder[0]!=null)holder[0].dismiss();showNewSubtaskDialog(task);});form.addView(add,marginBottom(dp(4)));
        holder[0]=new AlertDialog.Builder(this).setTitle("Subtarefas · "+task.completedSubtasks()+"/"+task.subtasks.size()).setView(scroll).setPositiveButton("Concluir",null).create();
        holder[0].show();
    }

    private void showNewSubtaskDialog(Store.Task task){
        EditText name=input("Nome da subtarefa");
        LinearLayout form=dialogForm();form.addView(name);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Nova subtarefa").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Adicionar",null).create();
        dlg.show();dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String ttl=name.getText().toString().trim();if(ttl.isEmpty()){name.setError("Informe um nome");return;}task.subtasks.add(new Store.Subtask(System.nanoTime(),ttl,false));store.save();dlg.dismiss();showSubtaskDialog(task);});
    }

    private void showTaskActions(Store.Task t){
        String planningAction = !"none".equals(t.recurrence) ? "Recorrente · planejamento fixo" : (t.flexible ? "Marcar como fixa" : "Marcar como flexível");
        String[] actions={"Iniciar foco","Editar","Subtarefas ("+t.completedSubtasks()+"/"+t.subtasks.size()+")",planningAction,"Mover para A fazer","Mover para Em andamento","Mover para Concluído","Excluir"};
        new AlertDialog.Builder(this).setTitle(t.title).setItems(actions,(d,w)->{
            if(w==0) startFocus(t);
            else if(w==1)showTaskDialog(t,false,null);
            else if(w==2)showSubtaskDialog(t);
            else if(w==3){
                if(!"none".equals(t.recurrence))Toast.makeText(this,"Tarefas recorrentes permanecem fixas.",Toast.LENGTH_SHORT).show();
                else{t.flexible=!t.flexible;if(t.deadline==null||t.deadline.length()!=10)t.deadline=t.date;store.save();Toast.makeText(this,t.flexible?"Tarefa marcada como flexível.":"Tarefa marcada como fixa.",Toast.LENGTH_SHORT).show();showPage(currentPage);}
            }
            else if(w==4){store.setTaskStatus(t,"todo");ReminderScheduler.schedule(this,t);showPage(currentPage);}
            else if(w==5){store.setTaskStatus(t,"doing");ReminderScheduler.schedule(this,t);showPage(currentPage);}
            else if(w==6){store.setTaskStatus(t,"done");ReminderScheduler.cancel(this,t.id);showPage(currentPage);}
            else confirmDeleteTask(t);
        }).show();
    }

    private void showProjectDialog(Store.Project existing){
        LinearLayout form=dialogForm();EditText title=input("Nome do projeto");EditText description=inputMulti("Descrição");EditText target=input("Prazo");target.setFocusable(false);target.setOnClickListener(v->pickDate(target));
        form.addView(fieldLabel("PROJETO"));form.addView(title);form.addView(fieldLabel("DESCRIÇÃO"));form.addView(description);form.addView(fieldLabel("PRAZO"));form.addView(target);
        target.setText(Store.addDays(Store.today(),30));if(existing!=null){title.setText(existing.title);description.setText(existing.description);target.setText(existing.targetDate);}
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(existing==null?"Novo projeto":"Editar projeto").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null);if(existing!=null)b.setNeutralButton("Excluir",null);AlertDialog dlg=b.create();dlg.show();
        if(existing!=null){Button del=dlg.getButton(AlertDialog.BUTTON_NEUTRAL);del.setTextColor(BAD);del.setOnClickListener(v->{dlg.dismiss();confirmDeleteProject(existing);});}
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String ttl=title.getText().toString().trim();if(ttl.isEmpty()){title.setError("Informe o projeto");return;}if(existing==null)store.projects.add(new Store.Project(System.currentTimeMillis(),ttl,description.getText().toString().trim(),target.getText().toString()));else{existing.title=ttl;existing.description=description.getText().toString().trim();existing.targetDate=target.getText().toString();}store.save();dlg.dismiss();organizeTab="projects";showPage("organize");});
    }

    private void showProjectActions(Store.Project p){String[] items={"Abrir tarefas","Editar","Excluir"};new AlertDialog.Builder(this).setTitle(p.title).setItems(items,(d,w)->{if(w==0)showProjectTasks(p);else if(w==1)showProjectDialog(p);else confirmDeleteProject(p);}).show();}
    private void showProjectTasks(Store.Project p){taskQuery=p.title;taskCategoryFilter="Todas";taskFilter="all";showPage("tasks");}
    private void confirmDeleteProject(Store.Project p){new AlertDialog.Builder(this).setTitle("Excluir projeto?").setMessage("As tarefas serão mantidas, mas ficarão sem projeto.").setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{for(Store.Task t:store.tasks)if(t.projectId==p.id)t.projectId=0;store.projects.remove(p);store.save();organizeTab="projects";showPage("organize");}).show();}

    private void showGoalDialog(Store.Goal existing){
        LinearLayout form=dialogForm();EditText title=input("Nome da meta");EditText progress=input("Progresso de 0 a 100");progress.setInputType(InputType.TYPE_CLASS_NUMBER);EditText target=input("Prazo");target.setFocusable(false);
        form.addView(fieldLabel("META"));form.addView(title);form.addView(formRow(fieldBox("PROGRESSO (%)",progress),fieldBox("PRAZO",target)));
        target.setText(Store.addDays(Store.today(),30));target.setOnClickListener(v->pickDate(target));
        if(existing!=null){title.setText(existing.title);progress.setText(String.valueOf(existing.progress));target.setText(existing.targetDate);}
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(existing==null?"Nova meta":"Editar meta").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null);if(existing!=null)b.setNeutralButton("Excluir",null);AlertDialog dlg=b.create();dlg.show();
        if(existing!=null){Button del=dlg.getButton(AlertDialog.BUTTON_NEUTRAL);del.setTextColor(BAD);del.setOnClickListener(v->{dlg.dismiss();confirmDeleteGoal(existing);});}
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String ttl=title.getText().toString().trim();if(ttl.isEmpty()){title.setError("Informe a meta");return;}int p=clamp(parseInt(progress.getText().toString(),0),0,100);if(existing==null)store.goals.add(new Store.Goal(System.currentTimeMillis(),ttl,p,target.getText().toString()));else{existing.title=ttl;existing.progress=p;existing.targetDate=target.getText().toString();}store.save();dlg.dismiss();organizeTab="goals";showPage("organize");});
    }

    private void showRoutineDialog(Store.Routine existing){
        ScrollView scroll = new ScrollView(this);
        LinearLayout form=dialogForm();
        scroll.addView(form);

        EditText title=input("Nome do hábito");
        EditText detail=input("Descrição curta");
        EditText time=input("Horário");
        time.setFocusable(false);
        time.setOnClickListener(v -> pickTime(time));
        EditText minutes=input("Minutos");
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        Spinner frequency=spinner(new String[]{"Todos os dias","Seg a Sex","Semanal","Dias específicos"});
        Spinner category=spinner(new String[]{"Pessoal","Saúde","Estudos","Trabalho","Projeto","Financeiro"});
        Spinner accent=spinner(new String[]{"Violeta","Menta","Âmbar","Rosa","Azul"});
        Spinner reminder=spinner(new String[]{"Sem lembrete","Na hora","10 min antes","30 min antes","1 h antes"});
        final int[] daysMask = { existing == null ? 0 : existing.daysMask };
        Button days = secondaryButton(daysMask[0] == 0 ? "Selecionar dias" : daysMaskLabel(daysMask[0]));
        days.setOnClickListener(v -> showDaysPicker(daysMask, days));

        form.addView(fieldLabel("HÁBITO"));form.addView(title);
        form.addView(fieldLabel("DESCRIÇÃO"));form.addView(detail);
        form.addView(formRow(fieldBox("HORÁRIO",time),fieldBox("DURAÇÃO",minutes)));
        form.addView(formRow(fieldBox("FREQUÊNCIA",frequency),fieldBox("DIAS",days)));
        form.addView(formRow(fieldBox("CATEGORIA",category),fieldBox("COR",accent)));
        form.addView(fieldLabel("LEMBRETE")); form.addView(reminder);

        TextView hint = text("O lembrete é local e funciona mesmo sem internet. A cor ajuda a reconhecer a rotina rapidamente.", 9, MUTED, false);
        hint.setPadding(dp(2), dp(5), dp(2), dp(8));
        form.addView(hint);

        minutes.setText("15");
        setSpinner(accent,"Violeta");
        if(existing!=null){
            title.setText(existing.title);
            detail.setText(existing.detail);
            time.setText(existing.time);
            minutes.setText(String.valueOf(existing.minutes));
            setSpinner(frequency,frequencyLabel(existing.frequency));
            setSpinner(category,existing.category);
            setSpinner(accent,routineAccentLabel(existing.accent));
            setSpinner(reminder,reminderLabel(existing.reminderMinutes));
        }

        AlertDialog.Builder b=new AlertDialog.Builder(this)
                .setTitle(existing==null?"Novo hábito":"Editar hábito")
                .setView(scroll)
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Salvar",null);
        if(existing!=null)b.setNeutralButton("Excluir",null);
        AlertDialog dlg=b.create();
        dlg.show();

        if(existing!=null){
            Button del=dlg.getButton(AlertDialog.BUTTON_NEUTRAL);
            del.setTextColor(BAD);
            del.setOnClickListener(v->{dlg.dismiss();confirmDeleteRoutine(existing);});
        }

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String ttl=title.getText().toString().trim();
            if(ttl.isEmpty()){title.setError("Informe o hábito");return;}
            String f=frequencyValue(String.valueOf(frequency.getSelectedItem()));
            if ("custom".equals(f) && daysMask[0] == 0) {
                Toast.makeText(this,"Selecione pelo menos um dia da semana.",Toast.LENGTH_SHORT).show();
                return;
            }
            int mins=Math.max(0,parseInt(minutes.getText().toString(),15));
            int reminderMinutes=reminderValue(String.valueOf(reminder.getSelectedItem()));
            if(reminderMinutes>=0 && time.getText().toString().trim().isEmpty()){
                Toast.makeText(this,"Defina um horário para usar lembrete.",Toast.LENGTH_SHORT).show();
                return;
            }
            if(existing==null){
                Store.Routine r = new Store.Routine(
                        System.currentTimeMillis(), ttl, detail.getText().toString().trim(), f, mins, Store.today(),
                        time.getText().toString(), String.valueOf(category.getSelectedItem()),
                        routineAccentKey(String.valueOf(accent.getSelectedItem())), reminderMinutes, daysMask[0]
                );
                store.routines.add(r);
                store.save();
                RoutineReminderScheduler.schedule(this, r);
            }else{
                existing.title=ttl;
                existing.detail=detail.getText().toString().trim();
                existing.frequency=f;
                existing.minutes=mins;
                existing.time=time.getText().toString();
                existing.category=String.valueOf(category.getSelectedItem());
                existing.accent=routineAccentKey(String.valueOf(accent.getSelectedItem()));
                existing.reminderMinutes=reminderMinutes;
                existing.daysMask=daysMask[0];
                store.save();
                RoutineReminderScheduler.schedule(this, existing);
            }
            dlg.dismiss();
            organizeTab="habits";
            showPage("organize");
        });
    }

    private void cycleTask(Store.Task t){
        String next="todo".equals(t.status)?"doing":"doing".equals(t.status)?"done":"todo";store.setTaskStatus(t,next);if("done".equals(next))ReminderScheduler.cancel(this,t.id);else ReminderScheduler.schedule(this,t);showPage("organize");
    }

    private void confirmDeleteTask(Store.Task t){new AlertDialog.Builder(this).setTitle("Excluir tarefa?").setMessage(t.title).setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{ReminderScheduler.cancel(this,t.id);store.tasks.remove(t);store.save();showPage(currentPage);}).show();}
    private void confirmDeleteGoal(Store.Goal g){new AlertDialog.Builder(this).setTitle("Excluir meta?").setMessage(g.title).setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{store.goals.remove(g);store.save();organizeTab="goals";showPage("organize");}).show();}
    private void confirmDeleteRoutine(Store.Routine r){new AlertDialog.Builder(this).setTitle("Excluir hábito?").setMessage(r.title).setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{RoutineReminderScheduler.cancel(this,r.id);store.routines.remove(r);store.save();organizeTab="habits";showPage("organize");}).show();}
    private void confirmClearDone(){new AlertDialog.Builder(this).setTitle("Limpar concluídas?").setMessage("O histórico de produtividade será preservado, mas as tarefas concluídas sairão da lista.").setNegativeButton("Cancelar",null).setPositiveButton("Limpar",(d,w)->{List<Store.Task> keep=new ArrayList<>();for(Store.Task t:store.tasks)if(!"done".equals(t.status)||!"none".equals(t.recurrence))keep.add(t);store.tasks.clear();store.tasks.addAll(keep);store.save();showPage("tasks");}).show();}

    private void pickDate(EditText target){
        Calendar c=Calendar.getInstance();c.setTime(Store.parse(target.getText().toString()));
        new DatePickerDialog(this,(view,y,m,d)->target.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)),c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void pickTime(EditText target){Calendar c=Calendar.getInstance();new TimePickerDialog(this,(view,h,m)->target.setText(String.format(Locale.US,"%02d:%02d",h,m)),c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show();}
    private void syncMonthToSelected(){visibleMonth.setTime(Store.parse(selectedAgendaDate));}

    private List<Store.Task> tasksForDate(String date){List<Store.Task> out=new ArrayList<>();for(Store.Task t:store.tasks)if(date.equals(t.date))out.add(t);return out;}

    private View sectionHeader(String title,String action,View.OnClickListener listener){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(3),dp(21),dp(3),dp(9));
        row.addView(text(title,17,TEXT,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        if(action!=null){TextView a=text(action,12,BRAND,true);a.setPadding(dp(8),dp(6),0,dp(6));if(listener!=null)a.setOnClickListener(listener);row.addView(a);}return row;
    }

    private Button chip(String label,boolean active){
        Button b=new Button(this);
        b.setText(label);b.setAllCaps(false);b.setTextSize(11);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(active?Color.WHITE:MUTED);
        b.setPadding(dp(13),0,dp(13),0);b.setMinHeight(dp(40));b.setMinWidth(dp(48));
        b.setBackground(rounded(active?BRAND:PANEL,99,!active));
        makePressable(b);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(40));p.setMargins(0,0,dp(7),0);b.setLayoutParams(p);return b;
    }

    private Button flatButton(String label){
        Button b=new Button(this);b.setText(label);b.setTextSize(24);b.setTextColor(TEXT);b.setAllCaps(false);b.setMinHeight(dp(48));b.setMinWidth(dp(48));b.setPadding(0,0,0,dp(3));b.setBackground(rounded(PANEL2,12,false));makePressable(b);return b;
    }

    private Button primaryButton(String label){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(Color.WHITE);b.setMinHeight(dp(48));b.setBackground(rounded(BRAND,12,false));b.setPadding(dp(14),dp(10),dp(14),dp(10));b.setElevation(dp(2));makePressable(b);return b;
    }

    private Button smallCheck(boolean done){
        Button b=new Button(this);b.setText(done?"✓":"");b.setTextSize(16);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setMinWidth(dp(38));b.setMinHeight(dp(38));b.setPadding(0,0,0,0);
        b.setBackground(rounded(done?GOOD:PANEL2,12,!done));makePressable(b);return b;
    }

    private ImageButton iconButton(int res,int tint){
        ImageButton b=new ImageButton(this);b.setImageResource(res);b.setColorFilter(tint);b.setScaleType(ImageView.ScaleType.CENTER);
        b.setBackground(rounded(PANEL,12,true));b.setPadding(dp(10),dp(10),dp(10),dp(10));b.setMinimumWidth(dp(44));b.setMinimumHeight(dp(44));makePressable(b);return b;
    }

    private LinearLayout cardBox(){
        LinearLayout l=new LinearLayout(this);l.setBackground(rounded(PANEL,16,true));l.setElevation(darkMode?dp(1):dp(3));return l;
    }

    private View emptyCard(String message){
        LinearLayout box=cardBox();box.setGravity(Gravity.CENTER);box.setPadding(dp(18),dp(22),dp(18),dp(22));
        TextView t=text(message,12,MUTED,false);t.setGravity(Gravity.CENTER);box.addView(t);return box;
    }
    private View divider(){View v=new View(this);v.setBackgroundColor(LINE);v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(1)));return v;}

    private ScrollView wrapScroll(LinearLayout body){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        if(isTablet()){
            FrameLayout holder=new FrameLayout(this);holder.setPadding(0,0,0,0);
            FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(760),ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.CENTER_HORIZONTAL);holder.addView(body,bp);scroll.addView(holder,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        }else scroll.addView(body,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private LinearLayout body(){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(16),dp(3),dp(16),dp(24));b.setBackgroundColor(BG);return b;}
    private LinearLayout dialogForm(){LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(16),dp(4),dp(16),dp(6));return form;}
    private TextView fieldLabel(String label){TextView t=text(label,10,MUTED,true);t.setPadding(dp(3),dp(4),0,dp(5));return t;}

    private LinearLayout fieldBox(String label, View field){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.addView(fieldLabel(label));
        ViewGroup.LayoutParams current=field.getLayoutParams();
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,current==null?dp(46):current.height);fp.setMargins(0,0,0,0);field.setLayoutParams(fp);box.addView(field);
        return box;
    }

    private LinearLayout formRow(View left,View right){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);lp.setMargins(0,0,dp(5),dp(7));row.addView(left,lp);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);rp.setMargins(dp(5),0,0,dp(7));row.addView(right,rp);return row;
    }

    private Button secondaryButton(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(BRAND);b.setMinHeight(dp(44));b.setBackground(rounded(PANEL2,12,true));b.setPadding(dp(12),dp(9),dp(12),dp(9));makePressable(b);return b;}

    private TextView miniBadge(String label, int color) {
        TextView badge = text(label, 9, color, true);
        badge.setPadding(dp(7), dp(3), dp(7), dp(3));
        badge.setBackground(rounded(Color.argb(darkMode ? 50 : 24, Color.red(color), Color.green(color), Color.blue(color)), 99, false));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(5), 0);
        badge.setLayoutParams(p);
        return badge;
    }

    private View emptyInline(String message) {
        TextView t = text(message, 12, MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(18), dp(10), dp(18));
        return t;
    }

    private View emptyState(int iconRes, String title, String message, String action, View.OnClickListener listener) {
        LinearLayout box = cardBox();
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(22), dp(26), dp(22), dp(24));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(BRAND);
        icon.setPadding(dp(11),dp(11),dp(11),dp(11));
        icon.setBackground(rounded(BRAND_SOFT, 16, false));
        box.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView h = text(title, 14, TEXT, true);
        h.setGravity(Gravity.CENTER);
        h.setPadding(0, dp(12), 0, dp(4));
        box.addView(h);

        TextView m = text(message, 11, MUTED, false);
        m.setGravity(Gravity.CENTER);
        box.addView(m);

        if (action != null) {
            Button b = secondaryButton(action);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
            p.setMargins(0, dp(14), 0, 0);
            box.addView(b, p);
            if (listener != null) b.setOnClickListener(listener);
        }
        return box;
    }

    private void animateTaskToggle(View card, Button check, TextView title, Store.Task task) {
        boolean willDone = !"done".equals(task.status);
        check.setText(willDone ? "✓" : "");
        check.setBackground(rounded(willDone ? GOOD : PANEL2, 12, !willDone));
        title.setAlpha(willDone ? .5f : 1f);
        if (willDone) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        else title.setPaintFlags(title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        haptic(check, willDone ? (Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.CLOCK_TICK) : HapticFeedbackConstants.CLOCK_TICK);

        if (!reduceMotion) {
            check.setScaleX(.72f); check.setScaleY(.72f);
            check.animate().scaleX(1f).scaleY(1f).setDuration(220L).start();
            if (willDone) {
                card.animate().alpha(.72f).setDuration(150L).withEndAction(() -> card.animate().alpha(1f).setDuration(90L).start()).start();
            }
        }

        store.toggleTask(task);
        if ("done".equals(task.status)) ReminderScheduler.cancel(this, task.id); else ReminderScheduler.schedule(this, task);
        card.postDelayed(() -> showPage(currentPage), reduceMotion ? 0L : 210L);
    }

    private void animateRoutineToggle(View row, Button check, TextView title, Store.Routine routine) {
        boolean willDone = !routine.doneOn(Store.today());
        check.setText(willDone ? "✓" : "");
        check.setBackground(rounded(willDone ? GOOD : PANEL2, 12, !willDone));
        title.setAlpha(willDone ? .55f : 1f);
        if (willDone) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        else title.setPaintFlags(title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        haptic(check, willDone ? (Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.CLOCK_TICK) : HapticFeedbackConstants.CLOCK_TICK);
        if (!reduceMotion) {
            check.setScaleX(.72f); check.setScaleY(.72f);
            check.animate().scaleX(1f).scaleY(1f).setDuration(220L).start();
        }
        routine.toggle(Store.today());
        store.save();
        RoutineReminderScheduler.schedule(this, routine);
        row.postDelayed(() -> showPage(currentPage), reduceMotion ? 0L : 180L);
    }

    private void makePressable(View view) {
        if (view == null || reduceMotion) return;
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(.985f).scaleY(.985f).setDuration(70L).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start();
            }
            return false;
        });
    }

    private void haptic(View view, int feedback) {
        if (!getSharedPreferences("ritmo_ui", MODE_PRIVATE).getBoolean("haptics", true)) return;
        try { view.performHapticFeedback(feedback); } catch (Throwable ignored) { }
    }

    private int routineAccentColor(String key) {
        if ("mint".equals(key)) return MINT;
        if ("amber".equals(key)) return WARN;
        if ("rose".equals(key)) return BAD;
        if ("blue".equals(key)) return darkMode ? Color.rgb(96,165,250) : Color.rgb(37,99,235);
        return BRAND;
    }

    private String routineAccentKey(String label) {
        if ("Menta".equals(label)) return "mint";
        if ("Âmbar".equals(label)) return "amber";
        if ("Rosa".equals(label)) return "rose";
        if ("Azul".equals(label)) return "blue";
        return "violet";
    }

    private String routineAccentLabel(String key) {
        if ("mint".equals(key)) return "Menta";
        if ("amber".equals(key)) return "Âmbar";
        if ("rose".equals(key)) return "Rosa";
        if ("blue".equals(key)) return "Azul";
        return "Violeta";
    }

    private String[] projectLabels(){String[] values=new String[store.projects.size()+1];values[0]="Sem projeto";for(int i=0;i<store.projects.size();i++)values[i+1]=store.projects.get(i).title;return values;}
    private String projectLabel(long id){Store.Project p=store.findProject(id);return p==null?"Sem projeto":p.title;}
    private long projectIdFromLabel(String label){if(label==null||"Sem projeto".equals(label))return 0L;for(Store.Project p:store.projects)if(p.title.equals(label))return p.id;return 0L;}

    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(14);e.setSingleLine(true);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setPadding(dp(12),dp(9),dp(12),dp(9));e.setBackground(rounded(PANEL2,12,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46));p.setMargins(0,0,0,dp(9));e.setLayoutParams(p);return e;}
    private EditText inputMulti(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(14);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setGravity(Gravity.TOP);e.setMinLines(2);e.setMaxLines(4);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(rounded(PANEL2,12,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(70));p.setMargins(0,0,0,dp(9));e.setLayoutParams(p);return e;}
    private Spinner spinner(String[] items){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items);s.setAdapter(a);s.setBackground(rounded(PANEL2,12,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46));p.setMargins(0,0,0,dp(9));s.setLayoutParams(p);return s;}
    private void setSpinner(Spinner s,String value){for(int i=0;i<s.getCount();i++)if(String.valueOf(s.getItemAtPosition(i)).equals(value)){s.setSelection(i);return;}}

    private TextView text(String value,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setLineSpacing(0f,1.10f);return t;}

    private GradientDrawable rounded(int color,int radiusDp,boolean stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radiusDp));if(stroke)g.setStroke(dp(1),LINE);return g;}
    private LinearLayout.LayoutParams marginBottom(int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(0,0,0,bottom);return p;}
    private LinearLayout.LayoutParams weighted(){return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f);}
    private LinearLayout.LayoutParams weightedMargin(int left,int right){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(82),1f);p.setMargins(dp(left),0,dp(right),0);return p;}

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private boolean isTablet(){return getResources().getConfiguration().smallestScreenWidthDp>=600;}
    private int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private int parseInt(String s,int fallback){try{return Integer.parseInt(s.trim());}catch(Exception e){return fallback;}}
    private String safe(String s,String fallback){return s==null||s.isEmpty()?fallback:s;}
    private int priorityValue(String p){if("high".equals(p))return 3;if("medium".equals(p))return 2;return 1;}
    private int priorityColor(String p){if("high".equals(p))return BAD;if("medium".equals(p))return WARN;return BRAND;}
    private String priorityLabel(String p){if("auto".equals(p))return "Automática";if("high".equals(p))return "Alta";if("medium".equals(p))return "Média";return "Baixa";}
    private String priorityValueFromLabel(String p){if("Automática".equals(p))return "auto";if("Alta".equals(p))return "high";if("Média".equals(p))return "medium";return "low";}

    private String recurrenceLabel(String r){if("daily".equals(r))return "Todos os dias";if("weekdays".equals(r))return "Seg a Sex";if("weekly".equals(r))return "Semanal";if("monthly".equals(r))return "Mensal";return "Sem repetição";}
    private String recurrenceValue(String r){if("Todos os dias".equals(r))return "daily";if("Seg a Sex".equals(r))return "weekdays";if("Semanal".equals(r))return "weekly";if("Mensal".equals(r))return "monthly";return "none";}
    private String frequencyLabel(String f){if("weekdays".equals(f))return "Seg a Sex";if("weekly".equals(f))return "Semanal";if("custom".equals(f))return "Dias específicos";return "Todos os dias";}
    private String frequencyValue(String f){if("Seg a Sex".equals(f))return "weekdays";if("Semanal".equals(f))return "weekly";if("Dias específicos".equals(f))return "custom";return "daily";}
    private void showDaysPicker(final int[] maskHolder, Button target) {
        String[] labels = {"Dom","Seg","Ter","Qua","Qui","Sex","Sáb"};
        boolean[] checked = new boolean[7];
        for (int i = 0; i < 7; i++) checked[i] = (maskHolder[0] & (1 << i)) != 0;
        new AlertDialog.Builder(this)
                .setTitle("Dias da semana")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    if (isChecked) maskHolder[0] |= (1 << which);
                    else maskHolder[0] &= ~(1 << which);
                })
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Aplicar", (dialog, which) -> target.setText(maskHolder[0] == 0 ? "Selecionar dias" : daysMaskLabel(maskHolder[0])))
                .show();
    }

    private String daysMaskLabel(int mask) {
        String[] labels = {"D","S","T","Q","Q","S","S"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) == 0) continue;
            if (out.length() > 0) out.append(" · ");
            out.append(labels[i]);
        }
        return out.length() == 0 ? "Selecionar dias" : out.toString();
    }

    private int reminderValue(String l){if("Na hora".equals(l))return 0;if("10 min antes".equals(l))return 10;if("30 min antes".equals(l))return 30;if("1 h antes".equals(l))return 60;if("1 dia antes".equals(l))return 1440;return -1;}
    private String reminderLabel(int m){if(m==0)return "Na hora";if(m==10)return "10 min antes";if(m==30)return "30 min antes";if(m==60)return "1 h antes";if(m==1440)return "1 dia antes";return "Sem lembrete";}

    private String humanMinutes(int minutes){if(minutes<60)return minutes+"min";int h=minutes/60,m=minutes%60;return m==0?h+"h":h+"h "+m+"min";}
    private String shortDate(String iso){try{Date d=Store.parse(iso);String s=new SimpleDateFormat("EEEE, dd MMM",new Locale("pt","BR")).format(d);return s.substring(0,1).toUpperCase(new Locale("pt","BR"))+s.substring(1);}catch(Exception e){return iso;}}
    private String fullDate(String iso){try{Date d=Store.parse(iso);String s=new SimpleDateFormat("EEEE, dd 'de' MMMM",new Locale("pt","BR")).format(d);return s.substring(0,1).toUpperCase(new Locale("pt","BR"))+s.substring(1);}catch(Exception e){return iso;}}
    private String compactDate(String iso){return new SimpleDateFormat("dd/MM",new Locale("pt","BR")).format(Store.parse(iso));}
    private String dayMonth(String iso){return new SimpleDateFormat("dd/MM",Locale.US).format(Store.parse(iso));}
    private String weekdayShort(String iso){String s=new SimpleDateFormat("EEE",new Locale("pt","BR")).format(Store.parse(iso)).replace(".","");return s.toUpperCase(new Locale("pt","BR"));}
}
