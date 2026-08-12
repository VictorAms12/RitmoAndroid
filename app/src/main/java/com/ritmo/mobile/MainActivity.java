package com.ritmo.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.content.ClipData;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
    private int BG, PANEL, PANEL2, TEXT, MUTED, LINE, BRAND, BRAND_DARK, ACCENT, GOOD, WARN, BAD, NAV;
    private Store store;
    private FrameLayout content;
    private LinearLayout bottomNav;
    private String currentPage = "home";
    private String taskFilter = "all";
    private String organizeTab = "kanban";
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
        darkMode = getSharedPreferences("ritmo_ui", MODE_PRIVATE).getBoolean("dark", false);
        setTheme(darkMode ? com.ritmo.mobile.R.style.Theme_Ritmo_Dark : com.ritmo.mobile.R.style.Theme_Ritmo);
        super.onCreate(savedInstanceState);
        try {
            applyPalette();
            configureSystemBars();
            store = new Store(this);
            buildShell();
            showPage("home");

            // Permissões e alarmes ficam para depois da primeira tela renderizar.
            // Isso evita que integrações do sistema impeçam a abertura do app.
            if (content != null) {
                content.postDelayed(() -> {
                    try { requestNotificationPermissionIfNeeded(); } catch (Throwable ignored) { }
                    try { ReminderScheduler.rescheduleAll(this, store); } catch (Throwable ignored) { }
                }, 700);
            }
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

    private void applyPalette() {
        if (darkMode) {
            BG = Color.rgb(7, 22, 18);
            PANEL = Color.rgb(13, 33, 27);
            PANEL2 = Color.rgb(16, 43, 35);
            TEXT = Color.rgb(239, 248, 244);
            MUTED = Color.rgb(148, 170, 162);
            LINE = Color.rgb(29, 58, 49);
            BRAND = Color.rgb(87, 216, 170);
            BRAND_DARK = Color.rgb(9, 47, 38);
            ACCENT = Color.rgb(87, 216, 170);
            GOOD = Color.rgb(74, 201, 156);
            WARN = Color.rgb(230, 165, 73);
            BAD = Color.rgb(230, 108, 108);
            NAV = Color.rgb(6, 17, 14);
        } else {
            BG = Color.rgb(244, 247, 245);
            PANEL = Color.WHITE;
            PANEL2 = Color.rgb(237, 243, 240);
            TEXT = Color.rgb(23, 35, 31);
            MUTED = Color.rgb(107, 123, 117);
            LINE = Color.rgb(220, 230, 225);
            BRAND = Color.rgb(15, 95, 77);
            BRAND_DARK = Color.rgb(11, 43, 36);
            ACCENT = Color.rgb(87, 216, 170);
            GOOD = Color.rgb(29, 138, 100);
            WARN = Color.rgb(201, 130, 34);
            BAD = Color.rgb(201, 79, 79);
            NAV = Color.rgb(9, 28, 23);
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

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAV);

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

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        bottomNav = buildBottomNav();
        root.addView(bottomNav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(7), dp(12), dp(7));
        bar.setBackgroundColor(BG);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        topTitleView = text("Ritmo", 21, TEXT, true);
        topSubtitleView = text(shortDate(Store.today()), 11, MUTED, false);
        titleBox.addView(topTitleView);
        titleBox.addView(topSubtitleView);
        bar.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView version = text("v2.1", 10, BRAND, true);
        version.setPadding(dp(9), dp(5), dp(9), dp(5));
        version.setGravity(Gravity.CENTER);
        version.setBackground(rounded(darkMode ? Color.rgb(18, 58, 46) : Color.rgb(223, 240, 234), 99, false));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.setMargins(0, 0, dp(6), 0);
        bar.addView(version, vp);

        ImageButton theme = iconButton(darkMode ? R.drawable.ic_sun : R.drawable.ic_moon, darkMode ? ACCENT : BRAND);
        theme.setContentDescription("Alternar tema");
        theme.setOnClickListener(v -> {
            getSharedPreferences("ritmo_ui", MODE_PRIVATE).edit().putBoolean("dark", !darkMode).apply();
            recreate();
        });
        bar.addView(theme, new LinearLayout.LayoutParams(dp(42), dp(42)));
        return bar;
    }

    private void updateTopBar() {
        if (topTitleView == null || topSubtitleView == null) return;
        if ("tasks".equals(currentPage)) {
            topTitleView.setText("Tarefas");
            topSubtitleView.setText("Capture, filtre e conclua");
        } else if ("agenda".equals(currentPage)) {
            topTitleView.setText("Agenda");
            topSubtitleView.setText(fullDate(selectedAgendaDate));
        } else if ("organize".equals(currentPage)) {
            topTitleView.setText("Organizar");
            String label = "kanban".equals(organizeTab) ? "Kanban" : "projects".equals(organizeTab) ? "Projetos" : "goals".equals(organizeTab) ? "Metas" : "habits".equals(organizeTab) ? "Hábitos" : "Estatísticas";
            topSubtitleView.setText(label + " · visão geral");
        } else {
            topTitleView.setText("Ritmo");
            topSubtitleView.setText(shortDate(Store.today()));
        }
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(6), dp(8), dp(6));
        nav.setBackgroundColor(NAV);
        nav.addView(navButton(com.ritmo.mobile.R.drawable.ic_home, "Hoje", "home"), weighted());
        nav.addView(navButton(com.ritmo.mobile.R.drawable.ic_tasks, "Tarefas", "tasks"), weighted());

        ImageButton plus = new ImageButton(this);
        plus.setImageResource(com.ritmo.mobile.R.drawable.ic_add);
        plus.setScaleType(ImageView.ScaleType.CENTER);
        plus.setBackground(rounded(ACCENT, 18, false));
        plus.setContentDescription("Adicionar");
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(58), dp(54));
        pp.setMargins(dp(4), 0, dp(4), 0);
        nav.addView(plus, pp);
        plus.setOnClickListener(v -> showAddMenu());

        nav.addView(navButton(com.ritmo.mobile.R.drawable.ic_calendar, "Agenda", "agenda"), weighted());
        nav.addView(navButton(com.ritmo.mobile.R.drawable.ic_grid, "Organizar", "organize"), weighted());
        return nav;
    }

    private View navButton(int iconRes, String label, String page) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(3), dp(4), dp(3), dp(4));
        box.setTag(page);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.rgb(154, 177, 168));
        box.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView txt = text(label, 10, Color.rgb(154, 177, 168), true);
        txt.setGravity(Gravity.CENTER);
        box.addView(txt);
        box.setOnClickListener(v -> showPage(page));
        return box;
    }

    private void highlightNav() {
        for (int i = 0; i < bottomNav.getChildCount(); i++) {
            View child = bottomNav.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            boolean active = currentPage.equals(String.valueOf(child.getTag()));
            child.setBackground(active ? rounded(Color.argb(30, 255, 255, 255), 14, false) : null);
            LinearLayout box = (LinearLayout) child;
            for (int j = 0; j < box.getChildCount(); j++) {
                View inner = box.getChildAt(j);
                if (inner instanceof TextView) ((TextView) inner).setTextColor(active ? Color.WHITE : Color.rgb(154, 177, 168));
                if (inner instanceof ImageView) ((ImageView) inner).setColorFilter(active ? Color.WHITE : Color.rgb(154, 177, 168));
            }
        }
    }

    private void showPage(String page) {
        try {
            currentPage = page;
            if (content == null) return;
            content.removeAllViews();
            if ("tasks".equals(page)) content.addView(buildTasksPage());
            else if ("agenda".equals(page)) content.addView(buildAgendaPage());
            else if ("organize".equals(page)) content.addView(buildOrganizePage());
            else content.addView(buildHomePage());
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

        body.addView(sectionHeader("Prioridades de hoje", "Ver todas", v -> showPage("tasks")));
        List<Store.Task> today = tasksForDate(Store.today());
        Collections.sort(today, (a, b) -> Integer.compare(priorityValue(b.effectivePriority()), priorityValue(a.effectivePriority())));
        int shown = 0;
        for (Store.Task t : today) {
            if (shown++ >= 4) break;
            body.addView(taskCard(t));
        }
        if (today.isEmpty()) body.addView(emptyCard("Nenhuma tarefa para hoje."));

        body.addView(sectionHeader("Rotina de hoje", "Ver hábitos", v -> {
            organizeTab = "habits";
            showPage("organize");
        }));
        body.addView(homeRoutineCard());

        body.addView(sectionHeader("Resumo", null, null));
        body.addView(metricsRow(today));

        body.addView(sectionHeader("Metas em andamento", "Abrir metas", v -> {
            organizeTab = "goals";
            showPage("organize");
        }));
        body.addView(goalsCard(Math.min(3, store.goals.size())));

        body.addView(sectionHeader("Últimos 7 dias", "Estatísticas", v -> {
            organizeTab = "stats";
            showPage("organize");
        }));
        body.addView(weeklyChartCard());
        return wrapScroll(body);
    }

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{darkMode ? Color.rgb(8, 42, 34) : BRAND_DARK, darkMode ? Color.rgb(14, 89, 69) : Color.rgb(14, 107, 85)});
        bg.setCornerRadius(dp(28));
        hero.setBackground(bg);

        hero.addView(text("SEU DIA EM MOVIMENTO", 11, Color.rgb(168, 214, 196), true));
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = h < 12 ? "Bom dia. Escolha o que realmente precisa avançar."
                : h < 18 ? "Boa tarde. Proteja o foco e mantenha o ritmo."
                : "Boa noite. Feche o dia com clareza, sem carregar tudo para amanhã.";
        TextView title = text(greeting, isTablet() ? 30 : 26, Color.WHITE, true);
        title.setPadding(0, dp(7), 0, dp(16));
        hero.addView(title);

        List<Store.Task> today = tasksForDate(Store.today());
        int done = 0, planned = 0;
        for (Store.Task t : today) { if ("done".equals(t.status)) done++; planned += t.minutes; }
        int pct = today.isEmpty() ? 0 : Math.round(done * 100f / today.size());

        LinearLayout score = new LinearLayout(this);
        score.setOrientation(LinearLayout.HORIZONTAL);
        score.setGravity(Gravity.CENTER_VERTICAL);
        score.setPadding(dp(14), dp(13), dp(14), dp(13));
        score.setBackground(rounded(Color.argb(35, 255, 255, 255), 18, false));
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("Eficiência de hoje", 11, Color.rgb(185, 224, 210), false));
        left.addView(text(pct + "%", 30, Color.WHITE, true));
        left.addView(text(done + " de " + today.size() + " tarefas · " + humanMinutes(planned), 11, Color.rgb(185, 224, 210), false));
        score.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100); pb.setProgress(pct);
        pb.setProgressTintList(ColorStateList.valueOf(ACCENT));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(Color.argb(45,255,255,255)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(86), dp(9));
        score.addView(pb, p);
        hero.addView(score);
        return hero;
    }

    private View homeRoutineCard() {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(8), dp(15), dp(8));
        int due = 0;
        for (Store.Routine r : store.routines) {
            if (!r.dueOn(Store.today())) continue;
            due++;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            Button check = smallCheck(r.doneOn(Store.today()));
            check.setOnClickListener(v -> { r.toggle(Store.today()); store.save(); showPage("home"); });
            row.addView(check, new LinearLayout.LayoutParams(dp(34), dp(34)));
            LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); txt.setPadding(dp(10),0,0,0);
            txt.addView(text(r.title, 13, TEXT, true));
            txt.addView(text(r.minutes + " min · sequência " + r.streak(Store.today()) + " dia(s)", 11, MUTED, false));
            row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(row);
        }
        if (due == 0) card.addView(text("Nenhuma rotina prevista para hoje.", 13, MUTED, false));
        return card;
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
        taskEmptyState = emptyCard("Nenhuma tarefa corresponde aos filtros.");
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
        card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(11), dp(10), dp(8), dp(10));
        card.setLayoutParams(marginBottom(dp(8)));

        Button check = smallCheck("done".equals(t.status));
        check.setOnClickListener(v -> {
            store.toggleTask(t);
            if ("done".equals(t.status)) ReminderScheduler.cancel(this, t.id); else ReminderScheduler.schedule(this, t);
            showPage(currentPage);
        });
        card.addView(check, new LinearLayout.LayoutParams(dp(35), dp(35)));

        LinearLayout center = new LinearLayout(this); center.setOrientation(LinearLayout.VERTICAL); center.setPadding(dp(10),0,dp(5),0);
        TextView title = text(t.title, 14, TEXT, true); if ("done".equals(t.status)) title.setAlpha(.5f);
        center.addView(title);
        String meta = (t.time == null || t.time.isEmpty() ? "Sem horário" : t.time) + " · " + t.category + " · " + humanMinutes(t.minutes);
        center.addView(text(meta, 10, MUTED, false));
        String project = t.projectId == 0 ? "" : store.projectTitle(t.projectId);
        if (!project.isEmpty()) center.addView(text("▣ " + project, 10, BRAND, true));
        if (t.subtasks != null && !t.subtasks.isEmpty()) {
            center.addView(text("☑ " + t.completedSubtasks() + "/" + t.subtasks.size() + " subtarefas", 10, MUTED, false));
        }
        String extra = ("auto".equals(t.priority) ? "Prioridade automática · " : "") + recurrenceLabel(t.recurrence) + (t.reminderMinutes >= 0 ? " · " + reminderLabel(t.reminderMinutes) : "");
        if ("auto".equals(t.priority) || !"Sem repetição".equals(recurrenceLabel(t.recurrence)) || t.reminderMinutes >= 0) center.addView(text(extra, 9, BRAND, false));
        center.setOnClickListener(v -> showTaskDialog(t, false, null));
        card.addView(center, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View dot = new View(this); dot.setBackground(rounded(priorityColor(t.effectivePriority()), 99, false));
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(dp(8), dp(8)); dpv.setMargins(dp(3),0,dp(3),0); card.addView(dot, dpv);
        ImageButton more = iconButton(R.drawable.ic_more, MUTED);
        more.setContentDescription("Opções da tarefa");
        more.setBackgroundColor(Color.TRANSPARENT);
        more.setPadding(dp(9),dp(9),dp(9),dp(9));
        more.setOnClickListener(v -> showTaskActions(t));
        card.addView(more, new LinearLayout.LayoutParams(dp(38), dp(38)));
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
        if("week".equals(agendaMode)){TextView hint=text("Segure o ícone ⠿ e solte em outro dia da semana",9,BRAND,false);hint.setPadding(0,dp(4),0,0);event.addView(hint);}
        event.setOnClickListener(v->showTaskDialog(t,false,null)); event.setOnLongClickListener(v->{showTaskActions(t);return true;});
        row.addView(event,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); return row;
    }

    private View buildOrganizePage() {
        LinearLayout body=body();
        body.addView(sectionHeader("Organização",null,null));
        body.addView(organizeTabs());
        if("projects".equals(organizeTab)) body.addView(projectsSection());
        else if("goals".equals(organizeTab)) body.addView(goalsSection());
        else if("habits".equals(organizeTab)) body.addView(habitsSection());
        else if("stats".equals(organizeTab)) body.addView(statsSection());
        else body.addView(kanbanBoard());
        return wrapScroll(body);
    }

    private View organizeTabs(){
        HorizontalScrollView hs=new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        String[][] tabs={{"kanban","Kanban"},{"projects","Projetos"},{"goals","Metas"},{"habits","Hábitos"},{"stats","Estatísticas"}};
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
        Button add=primaryButton("+ Novo hábito"); add.setOnClickListener(v->showRoutineDialog(null)); wrap.addView(add,marginBottom(dp(10)));
        LinearLayout card=cardBox();card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(8),dp(14),dp(8));
        if(store.routines.isEmpty()) card.addView(text("Nenhum hábito cadastrado.",13,MUTED,false));
        for(int i=0;i<store.routines.size();i++){
            Store.Routine r=store.routines.get(i); LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(9),0,dp(9));
            Button check=smallCheck(r.doneOn(Store.today()));check.setEnabled(r.dueOn(Store.today()));check.setAlpha(r.dueOn(Store.today())?1f:.35f);
            check.setOnClickListener(v->{r.toggle(Store.today());store.save();showPage("organize");});row.addView(check,new LinearLayout.LayoutParams(dp(36),dp(36)));
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(10),0,dp(8),0);
            info.addView(text(r.title,13,TEXT,true)); info.addView(text(frequencyLabel(r.frequency)+" · "+r.minutes+" min",10,MUTED,false));
            if(r.detail!=null&&!r.detail.isEmpty())info.addView(text(r.detail,10,MUTED,false));
            info.setOnClickListener(v->showRoutineDialog(r)); row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            LinearLayout streak=new LinearLayout(this);streak.setOrientation(LinearLayout.VERTICAL);streak.setGravity(Gravity.CENTER);
            streak.addView(text("🔥 "+r.streak(Store.today()),14,WARN,true));streak.addView(text("sequência",9,MUTED,false));row.addView(streak);
            row.setOnLongClickListener(v->{confirmDeleteRoutine(r);return true;});card.addView(row);if(i<store.routines.size()-1)card.addView(divider());
        }
        wrap.addView(card);return wrap;
    }

    private View statsSection(){
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(metric(store.completionRateLast7()+"%","Taxa de conclusão"),weightedMargin(0,4));
        row1.addView(metric(humanMinutes(store.totalCompletedMinutesLast7()),"Tempo concluído"),weightedMargin(4,0));
        wrap.addView(row1,marginBottom(dp(8)));
        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(metric(String.valueOf(store.overdueOpenCount()),"Atrasadas"),weightedMargin(0,4));
        row2.addView(metric(store.bestCompletionDayLast7(),"Melhor dia"),weightedMargin(4,0));
        wrap.addView(row2);

        TextView h1=text("Produtividade semanal",16,TEXT,true);h1.setPadding(dp(3),dp(20),0,dp(8));wrap.addView(h1);wrap.addView(weeklyChartCard());
        TextView h2=text("Distribuição por categoria",16,TEXT,true);h2.setPadding(dp(3),dp(20),0,dp(8));wrap.addView(h2);wrap.addView(categoryDistributionCard());
        TextView h3=text("Leitura da semana",16,TEXT,true);h3.setPadding(dp(3),dp(20),0,dp(8));wrap.addView(h3);wrap.addView(insightsCard());
        return wrap;
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
        EditText date=input("Data"); date.setFocusable(false);
        EditText time=input("Horário"); time.setFocusable(false);
        EditText minutes=input("Minutos"); minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        Spinner category=spinner(new String[]{"Estudos","Trabalho","Pessoal","Projeto","Saúde","Financeiro"});
        Spinner priority=spinner(new String[]{"Automática","Baixa","Média","Alta"});
        Spinner recurrence=spinner(new String[]{"Sem repetição","Todos os dias","Seg a Sex","Semanal","Mensal"});
        Spinner reminder=spinner(new String[]{"Sem lembrete","Na hora","10 min antes","30 min antes","1 h antes","1 dia antes"});
        Spinner project=spinner(projectLabels());

        form.addView(fieldLabel("TÍTULO")); form.addView(title);
        form.addView(fieldLabel("DESCRIÇÃO")); form.addView(description);
        form.addView(formRow(fieldBox("DATA",date),fieldBox("HORÁRIO",time)));
        form.addView(formRow(fieldBox("DURAÇÃO",minutes),fieldBox("PRIORIDADE",priority)));
        form.addView(formRow(fieldBox("CATEGORIA",category),fieldBox("PROJETO",project)));
        form.addView(formRow(fieldBox("REPETIÇÃO",recurrence),fieldBox("LEMBRETE",reminder)));

        EditText newSubtasks = inputMulti("Uma subtarefa por linha");
        if(editing){
            Button manage=secondaryButton("Subtarefas · "+existing.completedSubtasks()+"/"+existing.subtasks.size());
            manage.setOnClickListener(v->showSubtaskDialog(existing));
            form.addView(fieldLabel("SUBTAREFAS")); form.addView(manage,marginBottom(dp(8)));
        }else{
            form.addView(fieldLabel("SUBTAREFAS (OPCIONAL)"));form.addView(newSubtasks);
        }

        String initialDate=forcedDate!=null?forcedDate:Store.today(); date.setText(initialDate); minutes.setText(eventMode?"60":"30");
        setSpinner(priority,"Automática");
        if(editing){
            title.setText(existing.title);description.setText(existing.description);date.setText(existing.date);time.setText(existing.time);minutes.setText(String.valueOf(existing.minutes));
            setSpinner(category,existing.category);setSpinner(priority,priorityLabel(existing.priority));setSpinner(recurrence,recurrenceLabel(existing.recurrence));setSpinner(reminder,reminderLabel(existing.reminderMinutes));setSpinner(project,projectLabel(existing.projectId));
        }
        date.setOnClickListener(v->pickDate(date));time.setOnClickListener(v->pickTime(time));

        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(editing?"Editar tarefa":(eventMode?"Novo compromisso":"Nova tarefa")).setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null);
        if(editing) builder.setNeutralButton("Excluir",null);
        AlertDialog dlg=builder.create();
        dlg.show();
        if(editing){Button del=dlg.getButton(AlertDialog.BUTTON_NEUTRAL);del.setTextColor(BAD);del.setOnClickListener(v->{dlg.dismiss();confirmDeleteTask(existing);});}
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String ttl=title.getText().toString().trim();if(ttl.isEmpty()){title.setError("Informe um título");return;}
            int mins=Math.max(0,parseInt(minutes.getText().toString(),30));long projectId=projectIdFromLabel(String.valueOf(project.getSelectedItem()));
            if(editing){existing.title=ttl;existing.description=description.getText().toString().trim();existing.date=date.getText().toString();existing.time=time.getText().toString();existing.minutes=mins;existing.category=String.valueOf(category.getSelectedItem());existing.projectId=projectId;existing.priority=priorityValueFromLabel(String.valueOf(priority.getSelectedItem()));existing.recurrence=recurrenceValue(String.valueOf(recurrence.getSelectedItem()));existing.reminderMinutes=reminderValue(String.valueOf(reminder.getSelectedItem()));store.save();ReminderScheduler.schedule(this,existing);}
            else{Store.Task t=new Store.Task(System.currentTimeMillis(),ttl,description.getText().toString().trim(),date.getText().toString(),time.getText().toString(),priorityValueFromLabel(String.valueOf(priority.getSelectedItem())),mins,String.valueOf(category.getSelectedItem()),"todo",recurrenceValue(String.valueOf(recurrence.getSelectedItem())),reminderValue(String.valueOf(reminder.getSelectedItem())),projectId);addSubtasksFromLines(t,newSubtasks.getText().toString());store.tasks.add(t);store.save();ReminderScheduler.schedule(this,t);}
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
        String[] actions={"Editar","Subtarefas ("+t.completedSubtasks()+"/"+t.subtasks.size()+")","Mover para A fazer","Mover para Em andamento","Mover para Concluído","Excluir"};
        new AlertDialog.Builder(this).setTitle(t.title).setItems(actions,(d,w)->{
            if(w==0)showTaskDialog(t,false,null);
            else if(w==1)showSubtaskDialog(t);
            else if(w==2){store.setTaskStatus(t,"todo");ReminderScheduler.schedule(this,t);showPage(currentPage);}
            else if(w==3){store.setTaskStatus(t,"doing");ReminderScheduler.schedule(this,t);showPage(currentPage);}
            else if(w==4){store.setTaskStatus(t,"done");ReminderScheduler.cancel(this,t.id);showPage(currentPage);}
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
        LinearLayout form=dialogForm();EditText title=input("Nome do hábito");EditText detail=input("Descrição curta");EditText minutes=input("Minutos");minutes.setInputType(InputType.TYPE_CLASS_NUMBER);Spinner frequency=spinner(new String[]{"Todos os dias","Seg a Sex","Semanal"});
        form.addView(fieldLabel("HÁBITO"));form.addView(title);form.addView(fieldLabel("DESCRIÇÃO"));form.addView(detail);form.addView(formRow(fieldBox("DURAÇÃO",minutes),fieldBox("FREQUÊNCIA",frequency)));
        minutes.setText("15");if(existing!=null){title.setText(existing.title);detail.setText(existing.detail);minutes.setText(String.valueOf(existing.minutes));setSpinner(frequency,frequencyLabel(existing.frequency));}
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(existing==null?"Novo hábito":"Editar hábito").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null);if(existing!=null)b.setNeutralButton("Excluir",null);AlertDialog dlg=b.create();dlg.show();
        if(existing!=null){Button del=dlg.getButton(AlertDialog.BUTTON_NEUTRAL);del.setTextColor(BAD);del.setOnClickListener(v->{dlg.dismiss();confirmDeleteRoutine(existing);});}
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String ttl=title.getText().toString().trim();if(ttl.isEmpty()){title.setError("Informe o hábito");return;}String f=frequencyValue(String.valueOf(frequency.getSelectedItem()));int mins=parseInt(minutes.getText().toString(),15);if(existing==null)store.routines.add(new Store.Routine(System.currentTimeMillis(),ttl,detail.getText().toString().trim(),f,mins,Store.today()));else{existing.title=ttl;existing.detail=detail.getText().toString().trim();existing.frequency=f;existing.minutes=mins;}store.save();dlg.dismiss();organizeTab="habits";showPage("organize");});
    }

    private void cycleTask(Store.Task t){
        String next="todo".equals(t.status)?"doing":"doing".equals(t.status)?"done":"todo";store.setTaskStatus(t,next);if("done".equals(next))ReminderScheduler.cancel(this,t.id);else ReminderScheduler.schedule(this,t);showPage("organize");
    }

    private void confirmDeleteTask(Store.Task t){new AlertDialog.Builder(this).setTitle("Excluir tarefa?").setMessage(t.title).setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{ReminderScheduler.cancel(this,t.id);store.tasks.remove(t);store.save();showPage(currentPage);}).show();}
    private void confirmDeleteGoal(Store.Goal g){new AlertDialog.Builder(this).setTitle("Excluir meta?").setMessage(g.title).setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{store.goals.remove(g);store.save();organizeTab="goals";showPage("organize");}).show();}
    private void confirmDeleteRoutine(Store.Routine r){new AlertDialog.Builder(this).setTitle("Excluir hábito?").setMessage(r.title).setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{store.routines.remove(r);store.save();organizeTab="habits";showPage("organize");}).show();}
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
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(active?Color.WHITE:MUTED);b.setPadding(dp(13),0,dp(13),0);b.setMinHeight(0);b.setMinWidth(0);b.setBackground(rounded(active?BRAND:PANEL,99,!active));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(40));p.setMargins(0,0,dp(7),0);b.setLayoutParams(p);return b;
    }

    private Button flatButton(String label){Button b=new Button(this);b.setText(label);b.setTextSize(25);b.setTextColor(TEXT);b.setAllCaps(false);b.setMinHeight(0);b.setMinWidth(0);b.setPadding(0,0,0,dp(3));b.setBackground(rounded(PANEL2,12,false));return b;}
    private Button primaryButton(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.WHITE);b.setMinHeight(0);b.setBackground(rounded(BRAND,15,false));b.setPadding(dp(14),dp(11),dp(14),dp(11));return b;}
    private Button smallCheck(boolean done){Button b=new Button(this);b.setText(done?"✓":"");b.setTextSize(15);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setMinWidth(0);b.setMinHeight(0);b.setPadding(0,0,0,0);b.setBackground(rounded(done?GOOD:PANEL2,10,!done));return b;}

    private ImageButton iconButton(int res,int tint){ImageButton b=new ImageButton(this);b.setImageResource(res);b.setColorFilter(tint);b.setScaleType(ImageView.ScaleType.CENTER);b.setBackground(rounded(PANEL,14,true));b.setPadding(dp(10),dp(10),dp(10),dp(10));return b;}

    private LinearLayout cardBox(){LinearLayout l=new LinearLayout(this);l.setBackground(rounded(PANEL,19,true));return l;}
    private View emptyCard(String message){TextView t=text(message,13,MUTED,false);t.setGravity(Gravity.CENTER);t.setPadding(dp(16),dp(22),dp(16),dp(22));t.setBackground(rounded(PANEL,19,true));return t;}
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

    private Button secondaryButton(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(BRAND);b.setMinHeight(0);b.setBackground(rounded(PANEL2,13,true));b.setPadding(dp(12),dp(10),dp(12),dp(10));return b;}

    private String[] projectLabels(){String[] values=new String[store.projects.size()+1];values[0]="Sem projeto";for(int i=0;i<store.projects.size();i++)values[i+1]=store.projects.get(i).title;return values;}
    private String projectLabel(long id){Store.Project p=store.findProject(id);return p==null?"Sem projeto":p.title;}
    private long projectIdFromLabel(String label){if(label==null||"Sem projeto".equals(label))return 0L;for(Store.Project p:store.projects)if(p.title.equals(label))return p.id;return 0L;}

    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(14);e.setSingleLine(true);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setPadding(dp(12),dp(9),dp(12),dp(9));e.setBackground(rounded(PANEL2,12,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46));p.setMargins(0,0,0,dp(9));e.setLayoutParams(p);return e;}
    private EditText inputMulti(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(14);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setGravity(Gravity.TOP);e.setMinLines(2);e.setMaxLines(4);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(rounded(PANEL2,12,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(70));p.setMargins(0,0,0,dp(9));e.setLayoutParams(p);return e;}
    private Spinner spinner(String[] items){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items);s.setAdapter(a);s.setBackground(rounded(PANEL2,12,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46));p.setMargins(0,0,0,dp(9));s.setLayoutParams(p);return s;}
    private void setSpinner(Spinner s,String value){for(int i=0;i<s.getCount();i++)if(String.valueOf(s.getItemAtPosition(i)).equals(value)){s.setSelection(i);return;}}

    private TextView text(String value,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);t.setLineSpacing(0f,1.08f);return t;}

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
    private String frequencyLabel(String f){if("weekdays".equals(f))return "Seg a Sex";if("weekly".equals(f))return "Semanal";return "Todos os dias";}
    private String frequencyValue(String f){if("Seg a Sex".equals(f))return "weekdays";if("Semanal".equals(f))return "weekly";return "daily";}
    private int reminderValue(String l){if("Na hora".equals(l))return 0;if("10 min antes".equals(l))return 10;if("30 min antes".equals(l))return 30;if("1 h antes".equals(l))return 60;if("1 dia antes".equals(l))return 1440;return -1;}
    private String reminderLabel(int m){if(m==0)return "Na hora";if(m==10)return "10 min antes";if(m==30)return "30 min antes";if(m==60)return "1 h antes";if(m==1440)return "1 dia antes";return "Sem lembrete";}

    private String humanMinutes(int minutes){if(minutes<60)return minutes+"min";int h=minutes/60,m=minutes%60;return m==0?h+"h":h+"h "+m+"min";}
    private String shortDate(String iso){try{Date d=Store.parse(iso);String s=new SimpleDateFormat("EEEE, dd MMM",new Locale("pt","BR")).format(d);return s.substring(0,1).toUpperCase(new Locale("pt","BR"))+s.substring(1);}catch(Exception e){return iso;}}
    private String fullDate(String iso){try{Date d=Store.parse(iso);String s=new SimpleDateFormat("EEEE, dd 'de' MMMM",new Locale("pt","BR")).format(d);return s.substring(0,1).toUpperCase(new Locale("pt","BR"))+s.substring(1);}catch(Exception e){return iso;}}
    private String compactDate(String iso){return new SimpleDateFormat("dd/MM",new Locale("pt","BR")).format(Store.parse(iso));}
    private String dayMonth(String iso){return new SimpleDateFormat("dd/MM",Locale.US).format(Store.parse(iso));}
    private String weekdayShort(String iso){String s=new SimpleDateFormat("EEE",new Locale("pt","BR")).format(Store.parse(iso)).replace(".","");return s.toUpperCase(new Locale("pt","BR"));}
}
