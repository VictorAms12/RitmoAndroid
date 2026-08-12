package com.ritmo.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private final int BG = Color.rgb(244, 247, 245);
    private final int PANEL = Color.WHITE;
    private final int PANEL2 = Color.rgb(237, 243, 240);
    private final int TEXT = Color.rgb(23, 35, 31);
    private final int MUTED = Color.rgb(107, 123, 117);
    private final int LINE = Color.rgb(220, 230, 225);
    private final int BRAND = Color.rgb(15, 95, 77);
    private final int BRAND_DARK = Color.rgb(11, 43, 36);
    private final int BRAND_ACCENT = Color.rgb(87, 216, 170);
    private final int GOOD = Color.rgb(29, 138, 100);
    private final int WARN = Color.rgb(201, 130, 34);
    private final int BAD = Color.rgb(201, 79, 79);

    private Store store;
    private FrameLayout content;
    private LinearLayout bottomNav;
    private String currentPage = "home";
    private String taskFilter = "all";
    private String organizeTab = "kanban";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BRAND_DARK);
        getWindow().setNavigationBarColor(Color.rgb(8, 27, 22));
        store = new Store(this);
        buildShell();
        showPage("home");
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        content = new FrameLayout(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(content, cp);

        bottomNav = buildBottomNav();
        root.addView(bottomNav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(8), dp(18), dp(8));
        bar.setBackgroundColor(BG);

        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView brand = text("Ritmo", 21, TEXT, true);
        TextView date = text(fullDate(Store.today()), 12, MUTED, false);
        brandBox.addView(brand);
        brandBox.addView(date);
        bar.addView(brandBox, bp);

        TextView badge = text("OFFLINE", 10, BRAND, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(Color.rgb(223, 240, 234), 999));
        badge.setPadding(dp(11), dp(7), dp(11), dp(7));
        bar.addView(badge);
        return bar;
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(7), dp(8), dp(7));
        nav.setBackgroundColor(Color.rgb(9, 28, 23));

        nav.addView(navButton("⌂", "Hoje", "home"), weighted());
        nav.addView(navButton("✓", "Tarefas", "tasks"), weighted());

        Button plus = new Button(this);
        plus.setText("+");
        plus.setTextSize(28);
        plus.setTextColor(Color.rgb(7, 33, 25));
        plus.setAllCaps(false);
        plus.setPadding(0, 0, 0, dp(2));
        plus.setBackground(rounded(BRAND_ACCENT, 18));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(58), dp(54));
        pp.setMargins(dp(4), 0, dp(4), 0);
        nav.addView(plus, pp);
        plus.setOnClickListener(v -> showAddMenu());

        nav.addView(navButton("▦", "Agenda", "agenda"), weighted());
        nav.addView(navButton("◇", "Mais", "organize"), weighted());
        return nav;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private View navButton(String icon, String label, String page) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(3), dp(4), dp(3), dp(4));
        TextView i = text(icon, 20, Color.rgb(154, 177, 168), false);
        TextView l = text(label, 10, Color.rgb(154, 177, 168), true);
        i.setGravity(Gravity.CENTER);
        l.setGravity(Gravity.CENTER);
        box.addView(i);
        box.addView(l);
        box.setTag(page);
        box.setOnClickListener(v -> showPage(page));
        return box;
    }

    private void highlightNav() {
        for (int i = 0; i < bottomNav.getChildCount(); i++) {
            View child = bottomNav.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            String tag = String.valueOf(child.getTag());
            boolean active = currentPage.equals(tag);
            child.setBackground(active ? rounded(Color.argb(28, 255, 255, 255), 14) : null);
            LinearLayout ll = (LinearLayout) child;
            for (int j = 0; j < ll.getChildCount(); j++) {
                if (ll.getChildAt(j) instanceof TextView) {
                    ((TextView) ll.getChildAt(j)).setTextColor(active ? Color.WHITE : Color.rgb(154, 177, 168));
                }
            }
        }
    }

    private void showPage(String page) {
        currentPage = page;
        content.removeAllViews();
        if (page.equals("tasks")) content.addView(buildTasksPage());
        else if (page.equals("agenda")) content.addView(buildAgendaPage());
        else if (page.equals("organize")) content.addView(buildOrganizePage());
        else content.addView(buildHomePage());
        highlightNav();
    }

    private View buildHomePage() {
        ScrollView scroll = baseScroll();
        LinearLayout body = body();
        scroll.addView(body);

        body.addView(buildHero());
        body.addView(sectionHeader("Prioridades de hoje", "Ver todas", v -> showPage("tasks")));

        List<Store.Task> today = tasksForDate(Store.today());
        Collections.sort(today, (a, b) -> Integer.compare(priorityValue(b.priority), priorityValue(a.priority)));
        int count = 0;
        for (Store.Task task : today) {
            if (count >= 4) break;
            body.addView(taskCard(task));
            count++;
        }
        if (today.isEmpty()) body.addView(emptyCard("Nenhuma tarefa para hoje."));

        body.addView(sectionHeader("Resumo", null, null));
        body.addView(metricsRow(today));

        body.addView(sectionHeader("Metas em andamento", "Abrir metas", v -> {
            organizeTab = "goals";
            showPage("organize");
        }));
        body.addView(goalsCard(Math.min(3, store.goals.size())));
        return scroll;
    }

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(rounded(BRAND_DARK, 28));

        TextView eyebrow = text("SEU DIA EM MOVIMENTO", 11, Color.rgb(168, 214, 196), true);
        hero.addView(eyebrow);

        Calendar cal = Calendar.getInstance();
        int h = cal.get(Calendar.HOUR_OF_DAY);
        String greeting = h < 12 ? "Bom dia. Defina o ritmo antes que o dia defina por você."
                : h < 18 ? "Boa tarde. Priorize o essencial e proteja seu tempo."
                : "Boa noite. Feche o dia com clareza e sem sobrecarga.";
        TextView title = text(greeting, 27, Color.WHITE, true);
        title.setPadding(0, dp(7), 0, dp(16));
        hero.addView(title);

        List<Store.Task> today = tasksForDate(Store.today());
        int done = 0;
        for (Store.Task t : today) if ("done".equals(t.status)) done++;
        int pct = today.isEmpty() ? 0 : Math.round(done * 100f / today.size());

        LinearLayout score = new LinearLayout(this);
        score.setOrientation(LinearLayout.HORIZONTAL);
        score.setGravity(Gravity.CENTER_VERTICAL);
        score.setPadding(dp(14), dp(14), dp(14), dp(14));
        score.setBackground(rounded(Color.argb(35, 255, 255, 255), 18));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("Eficiência de hoje", 12, Color.rgb(189, 222, 210), false));
        left.addView(text(pct + "%", 31, Color.WHITE, true));
        left.addView(text(done + " de " + today.size() + " tarefas concluídas", 11, Color.rgb(189, 222, 210), false));
        score.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView circle = text(pct + "%", 15, Color.WHITE, true);
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(rounded(Color.rgb(14, 107, 85), 999));
        score.addView(circle, new LinearLayout.LayoutParams(dp(70), dp(70)));
        hero.addView(score);
        return hero;
    }

    private View metricsRow(List<Store.Task> today) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int done = 0;
        int minutes = 0;
        for (Store.Task t : today) {
            if ("done".equals(t.status)) done++;
            minutes += t.minutes;
        }
        row.addView(metric(String.valueOf(done), "Concluídas"), weightedMargin(0, 4));
        row.addView(metric(String.valueOf(today.size() - done), "Pendentes"), weightedMargin(4, 4));
        String hours = String.format(new Locale("pt", "BR"), "%.1fh", minutes / 60f).replace(",0", "");
        row.addView(metric(hours, "Planejado"), weightedMargin(4, 0));
        return row;
    }

    private LinearLayout.LayoutParams weightedMargin(int left, int right) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(84), 1f);
        p.setMargins(dp(left), 0, dp(right), 0);
        return p;
    }

    private View metric(String value, String label) {
        LinearLayout box = cardBox();
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(13), dp(11), dp(13), dp(11));
        box.addView(text(value, 20, TEXT, true));
        box.addView(text(label, 11, MUTED, false));
        return box;
    }

    private View buildTasksPage() {
        ScrollView scroll = baseScroll();
        LinearLayout body = body();
        scroll.addView(body);
        body.addView(sectionHeader("Tarefas", "Limpar concluídas", v -> confirmClearDone()));
        body.addView(taskFilterBar());

        List<Store.Task> list = new ArrayList<>();
        for (Store.Task t : store.tasks) {
            boolean add = taskFilter.equals("all")
                    || (taskFilter.equals("today") && Store.today().equals(t.date))
                    || (taskFilter.equals("todo") && !"done".equals(t.status))
                    || (taskFilter.equals("done") && "done".equals(t.status));
            if (add) list.add(t);
        }
        Collections.sort(list, Comparator.comparing((Store.Task t) -> t.date == null ? "9999" : t.date)
                .thenComparing(t -> t.time == null ? "99:99" : t.time));

        if (list.isEmpty()) body.addView(emptyCard("Nada por aqui."));
        for (Store.Task task : list) body.addView(taskCard(task));
        return scroll;
    }

    private View taskFilterBar() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[][] filters = {{"all", "Todas"}, {"today", "Hoje"}, {"todo", "Pendentes"}, {"done", "Concluídas"}};
        for (String[] f : filters) {
            Button b = chip(f[1], taskFilter.equals(f[0]));
            b.setOnClickListener(v -> {
                taskFilter = f[0];
                showPage("tasks");
            });
            row.addView(b);
        }
        hs.addView(row);
        hs.setPadding(0, 0, 0, dp(10));
        return hs;
    }

    private View buildAgendaPage() {
        ScrollView scroll = baseScroll();
        LinearLayout body = body();
        scroll.addView(body);
        body.addView(sectionHeader("Agenda", "Hoje", null));

        LinearLayout dateCard = cardBox();
        dateCard.setOrientation(LinearLayout.VERTICAL);
        dateCard.setPadding(dp(16), dp(15), dp(16), dp(15));
        dateCard.addView(text("Planejamento do dia", 12, MUTED, false));
        dateCard.addView(text(fullDate(Store.today()), 18, TEXT, true));
        body.addView(dateCard, marginBottom(dp(14)));

        List<Store.Task> list = tasksForDate(Store.today());
        Collections.sort(list, Comparator.comparing(t -> t.time == null || t.time.isEmpty() ? "99:99" : t.time));
        if (list.isEmpty()) body.addView(emptyCard("Sem compromissos hoje."));
        for (Store.Task t : list) body.addView(agendaItem(t));
        return scroll;
    }

    private View agendaItem(Store.Task t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, 0, 0, dp(10));

        TextView time = text(t.time == null || t.time.isEmpty() ? "—" : t.time, 12, MUTED, false);
        time.setGravity(Gravity.RIGHT);
        time.setPadding(0, dp(15), dp(12), 0);
        row.addView(time, new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout event = cardBox();
        event.setOrientation(LinearLayout.VERTICAL);
        event.setPadding(dp(14), dp(13), dp(14), dp(13));
        event.addView(text(t.title, 14, TEXT, true));
        String status = "done".equals(t.status) ? "Concluído" : "doing".equals(t.status) ? "Em andamento" : "Planejado";
        event.addView(text(t.category + " · " + t.minutes + " min · " + status, 11, MUTED, false));
        row.addView(event, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View buildOrganizePage() {
        ScrollView scroll = baseScroll();
        LinearLayout body = body();
        scroll.addView(body);
        body.addView(sectionHeader("Organização", null, null));
        body.addView(organizeTabs());
        if (organizeTab.equals("goals")) body.addView(goalsCard(store.goals.size()));
        else if (organizeTab.equals("routine")) body.addView(routinesCard());
        else if (organizeTab.equals("insights")) body.addView(insightsCard());
        else body.addView(kanbanBoard());
        return scroll;
    }

    private View organizeTabs() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[][] tabs = {{"kanban", "Kanban"}, {"goals", "Metas"}, {"routine", "Rotina"}, {"insights", "Insights"}};
        for (String[] tab : tabs) {
            Button b = chip(tab[1], organizeTab.equals(tab[0]));
            b.setOnClickListener(v -> {
                organizeTab = tab[0];
                showPage("organize");
            });
            row.addView(b);
        }
        hs.addView(row);
        hs.setPadding(0, 0, 0, dp(12));
        return hs;
    }

    private View kanbanBoard() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout board = new LinearLayout(this);
        board.setOrientation(LinearLayout.HORIZONTAL);
        String[][] columns = {{"todo", "A FAZER"}, {"doing", "EM ANDAMENTO"}, {"done", "CONCLUÍDO"}};

        for (String[] column : columns) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(11), dp(11), dp(11), dp(11));
            col.setBackground(rounded(PANEL2, 20));
            LinearLayout.LayoutParams colP = new LinearLayout.LayoutParams(dp(275), ViewGroup.LayoutParams.WRAP_CONTENT);
            colP.setMargins(0, 0, dp(12), 0);
            board.addView(col, colP);

            int count = 0;
            for (Store.Task t : store.tasks) if (column[0].equals(t.status)) count++;
            col.addView(text(column[1] + "  ·  " + count, 12, MUTED, true));

            for (Store.Task t : store.tasks) {
                if (!column[0].equals(t.status)) continue;
                LinearLayout k = cardBox();
                k.setOrientation(LinearLayout.VERTICAL);
                k.setPadding(dp(12), dp(12), dp(12), dp(12));
                k.addView(text(t.title, 13, TEXT, true));
                k.addView(text(t.category + " · toque para avançar", 11, MUTED, false));
                LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                kp.setMargins(0, dp(8), 0, 0);
                col.addView(k, kp);
                k.setOnClickListener(v -> cycleTask(t));
                k.setOnLongClickListener(v -> {
                    confirmDeleteTask(t);
                    return true;
                });
            }
        }
        hs.addView(board);
        return hs;
    }

    private View goalsCard(int limit) {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        if (store.goals.isEmpty()) {
            card.addView(text("Nenhuma meta cadastrada.", 13, MUTED, false));
            return card;
        }
        int max = Math.min(limit, store.goals.size());
        for (int i = 0; i < max; i++) {
            Store.Goal g = store.goals.get(i);
            LinearLayout goal = new LinearLayout(this);
            goal.setOrientation(LinearLayout.VERTICAL);
            if (i > 0) goal.setPadding(0, dp(15), 0, 0);
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.addView(text(g.title, 13, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(text(g.progress + "%", 13, BRAND, true));
            goal.addView(top);
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgress(Math.max(0, Math.min(100, g.progress)));
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(BRAND));
            pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL2));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(9));
            pp.setMargins(0, dp(9), 0, 0);
            goal.addView(pb, pp);
            goal.setOnLongClickListener(v -> {
                confirmDeleteGoal(g);
                return true;
            });
            card.addView(goal);
        }
        return card;
    }

    private View routinesCard() {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(7), dp(16), dp(7));
        if (store.routines.isEmpty()) card.addView(text("Nenhuma rotina cadastrada.", 13, MUTED, false));
        for (int i = 0; i < store.routines.size(); i++) {
            Store.Routine r = store.routines.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(11), 0, dp(11));
            TextView icon = text(i % 2 == 0 ? "↻" : "◎", 20, BRAND, true);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(rounded(Color.rgb(223, 240, 234), 12));
            row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.setPadding(dp(11), 0, 0, 0);
            txt.addView(text(r.title, 13, TEXT, true));
            txt.addView(text(r.detail, 11, MUTED, false));
            row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnLongClickListener(v -> {
                confirmDeleteRoutine(r);
                return true;
            });
            card.addView(row);
            if (i < store.routines.size() - 1) card.addView(divider());
        }
        return card;
    }

    private View insightsCard() {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        int pending = 0, high = 0, minutes = 0;
        for (Store.Task t : store.tasks) {
            if (!"done".equals(t.status)) pending++;
            if (!"done".equals(t.status) && "high".equals(t.priority)) high++;
            if (Store.today().equals(t.date)) minutes += t.minutes;
        }
        card.addView(insight("◎", pending + " tarefa(s) ainda abertas",
                high > 0 ? high + " delas têm prioridade alta. Resolva essas antes de adicionar novas demandas." : "Sua fila crítica está controlada."));
        card.addView(divider());
        card.addView(insight("◷", String.format(new Locale("pt", "BR"), "%.1fh planejadas hoje", minutes / 60f),
                minutes > 480 ? "Seu dia está carregado. Considere redistribuir pelo menos uma atividade." : "A carga planejada está em uma faixa administrável."));
        card.addView(divider());
        card.addView(insight("↗", "Organize por energia, não só por horário",
                "Use seus melhores períodos para tarefas de concentração e deixe tarefas leves nos intervalos."));
        return card;
    }

    private View insight(String iconText, String title, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, dp(12));
        TextView icon = text(iconText, 20, BRAND, true);
        row.addView(icon, new LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(text(title, 13, TEXT, true));
        txt.addView(text(detail, 12, MUTED, false));
        row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View taskCard(Store.Task t) {
        LinearLayout card = cardBox();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams margin = marginBottom(dp(9));

        Button check = new Button(this);
        check.setText("done".equals(t.status) ? "✓" : "");
        check.setTextColor(Color.WHITE);
        check.setTextSize(14);
        check.setPadding(0, 0, 0, 0);
        check.setBackground(rounded("done".equals(t.status) ? GOOD : PANEL2, 9));
        card.addView(check, new LinearLayout.LayoutParams(dp(31), dp(31)));
        check.setOnClickListener(v -> {
            t.status = "done".equals(t.status) ? "todo" : "done";
            store.save();
            showPage(currentPage);
        });

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(11), 0, dp(8), 0);
        TextView title = text(t.title, 14, TEXT, true);
        if ("done".equals(t.status)) title.setAlpha(0.5f);
        info.addView(title);
        String timing = t.time == null || t.time.isEmpty() ? "Sem horário" : t.time;
        info.addView(text(timing + " · " + t.category + " · " + t.minutes + " min", 11, MUTED, false));
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView dot = text("●", 13, priorityColor(t.priority), true);
        card.addView(dot);
        card.setOnLongClickListener(v -> {
            confirmDeleteTask(t);
            return true;
        });
        card.setLayoutParams(margin);
        return card;
    }

    private void showAddMenu() {
        String[] items = {"Nova tarefa", "Novo compromisso", "Nova meta", "Nova rotina"};
        new AlertDialog.Builder(this)
                .setTitle("Adicionar")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) showTaskDialog(false);
                    else if (which == 1) showTaskDialog(true);
                    else if (which == 2) showGoalDialog();
                    else showRoutineDialog();
                })
                .show();
    }

    private void showTaskDialog(boolean commitment) {
        LinearLayout form = dialogForm();

        EditText title = input(commitment ? "Compromisso" : "Título da tarefa");
        form.addView(title);

        EditText date = input("Data");
        date.setFocusable(false);
        date.setText(Store.today());
        date.setOnClickListener(v -> pickDate(date));
        form.addView(date);

        EditText time = input("Horário");
        time.setFocusable(false);
        time.setText(commitment ? "09:00" : "");
        time.setOnClickListener(v -> pickTime(time));
        form.addView(time);

        Spinner category = spinner(new String[]{"Estudos", "Trabalho", "Pessoal", "Projeto", "Saúde", "Financeiro", "Compromisso"});
        if (commitment) category.setSelection(6);
        form.addView(category);

        Spinner priority = spinner(new String[]{"Baixa", "Média", "Alta"});
        priority.setSelection(commitment ? 1 : 0);
        form.addView(priority);

        EditText minutes = input("Duração em minutos");
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutes.setText(commitment ? "60" : "30");
        form.addView(minutes);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(commitment ? "Novo compromisso" : "Nova tarefa")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String ttl = title.getText().toString().trim();
            if (ttl.isEmpty()) {
                title.setError("Informe um título");
                return;
            }
            int mins = parseInt(minutes.getText().toString(), 30);
            String pr = priority.getSelectedItemPosition() == 2 ? "high" : priority.getSelectedItemPosition() == 1 ? "medium" : "low";
            store.tasks.add(new Store.Task(System.currentTimeMillis(), ttl, date.getText().toString(), time.getText().toString(), pr, mins, String.valueOf(category.getSelectedItem()), "todo"));
            store.save();
            dlg.dismiss();
            Toast.makeText(this, commitment ? "Compromisso salvo" : "Tarefa salva", Toast.LENGTH_SHORT).show();
            showPage(commitment ? "agenda" : "tasks");
        }));
        dlg.show();
    }

    private void showGoalDialog() {
        LinearLayout form = dialogForm();
        EditText title = input("Nome da meta");
        EditText progress = input("Progresso inicial (0 a 100)");
        progress.setInputType(InputType.TYPE_CLASS_NUMBER);
        progress.setText("0");
        form.addView(title);
        form.addView(progress);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Nova meta")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String ttl = title.getText().toString().trim();
            if (ttl.isEmpty()) {
                title.setError("Informe a meta");
                return;
            }
            int p = Math.max(0, Math.min(100, parseInt(progress.getText().toString(), 0)));
            store.goals.add(new Store.Goal(System.currentTimeMillis(), ttl, p));
            store.save();
            dlg.dismiss();
            organizeTab = "goals";
            showPage("organize");
        }));
        dlg.show();
    }

    private void showRoutineDialog() {
        LinearLayout form = dialogForm();
        EditText title = input("Nome da rotina");
        EditText detail = input("Frequência / duração");
        detail.setText("Todos os dias · 15 min");
        form.addView(title);
        form.addView(detail);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Nova rotina")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String ttl = title.getText().toString().trim();
            if (ttl.isEmpty()) {
                title.setError("Informe a rotina");
                return;
            }
            store.routines.add(new Store.Routine(System.currentTimeMillis(), ttl, detail.getText().toString().trim()));
            store.save();
            dlg.dismiss();
            organizeTab = "routine";
            showPage("organize");
        }));
        dlg.show();
    }

    private void pickDate(EditText target) {
        Calendar c = Calendar.getInstance();
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(target.getText().toString());
            if (d != null) c.setTime(d);
        } catch (ParseException ignored) {
        }
        new DatePickerDialog(this, (view, year, month, day) -> target.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime(EditText target) {
        Calendar c = Calendar.getInstance();
        new TimePickerDialog(this, (view, hour, minute) -> target.setText(String.format(Locale.US, "%02d:%02d", hour, minute)),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
    }

    private void cycleTask(Store.Task t) {
        t.status = "todo".equals(t.status) ? "doing" : "doing".equals(t.status) ? "done" : "todo";
        store.save();
        showPage("organize");
    }

    private void confirmDeleteTask(Store.Task t) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir tarefa?")
                .setMessage(t.title)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (d, w) -> {
                    store.tasks.remove(t);
                    store.save();
                    showPage(currentPage);
                }).show();
    }

    private void confirmDeleteGoal(Store.Goal g) {
        new AlertDialog.Builder(this).setTitle("Excluir meta?").setMessage(g.title)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (d, w) -> {
                    store.goals.remove(g);
                    store.save();
                    organizeTab = "goals";
                    showPage("organize");
                }).show();
    }

    private void confirmDeleteRoutine(Store.Routine r) {
        new AlertDialog.Builder(this).setTitle("Excluir rotina?").setMessage(r.title)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir", (d, w) -> {
                    store.routines.remove(r);
                    store.save();
                    organizeTab = "routine";
                    showPage("organize");
                }).show();
    }

    private void confirmClearDone() {
        new AlertDialog.Builder(this).setTitle("Limpar concluídas?")
                .setMessage("As tarefas concluídas serão removidas do aparelho.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Limpar", (d, w) -> {
                    List<Store.Task> keep = new ArrayList<>();
                    for (Store.Task t : store.tasks) if (!"done".equals(t.status)) keep.add(t);
                    store.tasks.clear();
                    store.tasks.addAll(keep);
                    store.save();
                    showPage("tasks");
                }).show();
    }

    private List<Store.Task> tasksForDate(String date) {
        List<Store.Task> out = new ArrayList<>();
        for (Store.Task t : store.tasks) if (date.equals(t.date)) out.add(t);
        return out;
    }

    private View sectionHeader(String title, String action, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(3), dp(22), dp(3), dp(10));
        row.addView(text(title, 17, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (action != null) {
            TextView a = text(action, 12, BRAND, true);
            a.setPadding(dp(8), dp(6), 0, dp(6));
            if (listener != null) a.setOnClickListener(listener);
            row.addView(a);
        }
        return row;
    }

    private Button chip(String label, boolean active) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(active ? Color.WHITE : MUTED);
        b.setPadding(dp(13), 0, dp(13), 0);
        b.setBackground(rounded(active ? BRAND : PANEL, 999));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        p.setMargins(0, 0, dp(7), 0);
        b.setLayoutParams(p);
        return b;
    }

    private LinearLayout cardBox() {
        LinearLayout l = new LinearLayout(this);
        l.setBackground(rounded(PANEL, 19));
        return l;
    }

    private View emptyCard(String message) {
        TextView t = text(message, 13, MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(22), dp(16), dp(22));
        t.setBackground(rounded(PANEL, 19));
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        form.setPadding(p, dp(6), p, 0);
        return form;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(9), dp(12), dp(9));
        e.setBackground(rounded(PANEL2, 12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        p.setMargins(0, 0, 0, dp(10));
        e.setLayoutParams(p);
        return e;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        s.setAdapter(adapter);
        s.setBackground(rounded(PANEL2, 12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        p.setMargins(0, 0, 0, dp(10));
        s.setLayoutParams(p);
        return s;
    }

    private ScrollView baseScroll() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setBackgroundColor(BG);
        return s;
    }

    private LinearLayout body() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(3), dp(16), dp(22));
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setLineSpacing(0f, 1.08f);
        return t;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (color == PANEL) g.setStroke(dp(1), LINE);
        return g;
    }

    private LinearLayout.LayoutParams marginBottom(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, bottom);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int priorityColor(String p) {
        if ("high".equals(p)) return BAD;
        if ("medium".equals(p)) return WARN;
        return BRAND;
    }

    private int priorityValue(String p) {
        if ("high".equals(p)) return 3;
        if ("medium".equals(p)) return 2;
        return 1;
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return fallback; }
    }

    private String fullDate(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            if (d == null) return iso;
            String f = new SimpleDateFormat("EEEE, dd 'de' MMMM", new Locale("pt", "BR")).format(d);
            return f.substring(0, 1).toUpperCase(new Locale("pt", "BR")) + f.substring(1);
        } catch (Exception e) {
            return iso;
        }
    }
}
