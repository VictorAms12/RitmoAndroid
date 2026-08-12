package com.ritmo.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Store {
    private static final String PREFS = "ritmo_prefs";
    private static final String KEY_DATA = "ritmo_data";

    public final List<Task> tasks = new ArrayList<>();
    public final List<Goal> goals = new ArrayList<>();
    public final List<Routine> routines = new ArrayList<>();
    public final List<Completion> completions = new ArrayList<>();
    public final List<Project> projects = new ArrayList<>();
    public final List<FocusSession> focusSessions = new ArrayList<>();

    private final SharedPreferences prefs;
    private final Context appContext;

    public Store(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        migrateLegacyCompletionHistory();
        normalizeRecurringTasks();
    }

    public static String today() { return format(new Date()); }

    public static String format(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d);
    }

    public static Date parse(String iso) {
        try { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso); }
        catch (Exception e) { return new Date(); }
    }

    public static String addDays(String iso, int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(parse(iso));
        c.add(Calendar.DAY_OF_MONTH, days);
        return format(c.getTime());
    }

    public static String startOfWeek(String iso) {
        Calendar c = Calendar.getInstance();
        c.setTime(parse(iso));
        int dow = c.get(Calendar.DAY_OF_WEEK);
        int delta = dow == Calendar.SUNDAY ? -6 : Calendar.MONDAY - dow;
        c.add(Calendar.DAY_OF_MONTH, delta);
        return format(c.getTime());
    }

    public static int daysBetween(String fromIso, String toIso) {
        try {
            long a = parse(fromIso).getTime();
            long b = parse(toIso).getTime();
            return (int) Math.round((b - a) / 86400000d);
        } catch (Exception e) { return 0; }
    }

    private void load() {
        String raw = prefs.getString(KEY_DATA, null);
        if (raw == null || raw.trim().isEmpty()) {
            seed(); save(); return;
        }
        try {
            JSONObject root = new JSONObject(raw);
            tasks.clear(); goals.clear(); routines.clear(); completions.clear(); projects.clear(); focusSessions.clear();
            JSONArray t = root.optJSONArray("tasks");
            JSONArray g = root.optJSONArray("goals");
            JSONArray r = root.optJSONArray("routines");
            JSONArray c = root.optJSONArray("completions");
            JSONArray p = root.optJSONArray("projects");
            JSONArray f = root.optJSONArray("focusSessions");
            if (t != null) for (int i = 0; i < t.length(); i++) tasks.add(Task.fromJson(t.getJSONObject(i)));
            if (g != null) for (int i = 0; i < g.length(); i++) goals.add(Goal.fromJson(g.getJSONObject(i)));
            if (r != null) for (int i = 0; i < r.length(); i++) routines.add(Routine.fromJson(r.getJSONObject(i)));
            if (c != null) for (int i = 0; i < c.length(); i++) completions.add(Completion.fromJson(c.getJSONObject(i)));
            if (p != null) for (int i = 0; i < p.length(); i++) projects.add(Project.fromJson(p.getJSONObject(i)));
            if (f != null) for (int i = 0; i < f.length(); i++) focusSessions.add(FocusSession.fromJson(f.getJSONObject(i)));
            sanitizeLoadedData();
        } catch (Exception e) {
            try { prefs.edit().putString("ritmo_data_corrupt_backup", raw).apply(); } catch (Throwable ignored) { }
            tasks.clear(); goals.clear(); routines.clear(); completions.clear(); projects.clear(); focusSessions.clear();
            seed(); save();
        }
    }

    private void sanitizeLoadedData() {
        for (Task t : tasks) {
            if (t.title == null || t.title.trim().isEmpty()) t.title = "Tarefa";
            if (t.description == null) t.description = "";
            if (t.date == null || t.date.length() != 10) t.date = today();
            if (t.time == null) t.time = "";
            if (t.priority == null) t.priority = "low";
            if (t.category == null || t.category.trim().isEmpty()) t.category = "Pessoal";
            if (t.status == null) t.status = "todo";
            if (t.recurrence == null) t.recurrence = "none";
            if (t.deadline == null || t.deadline.length() != 10) t.deadline = t.date;
            if (t.minutes < 0) t.minutes = 0;
            if (t.subtasks == null) t.subtasks = new ArrayList<>();
        }
        for (Goal g : goals) {
            if (g.title == null || g.title.trim().isEmpty()) g.title = "Meta";
            if (g.targetDate == null) g.targetDate = "";
            g.progress = Math.max(0, Math.min(100, g.progress));
        }
        for (Routine r : routines) {
            if (r.title == null || r.title.trim().isEmpty()) r.title = "Hábito";
            if (r.detail == null) r.detail = "";
            if (r.frequency == null) r.frequency = "daily";
            if (r.startDate == null || r.startDate.length() != 10) r.startDate = today();
            if (r.time == null) r.time = "";
            if (r.category == null || r.category.trim().isEmpty()) r.category = "Pessoal";
            if (r.accent == null || r.accent.trim().isEmpty()) r.accent = "violet";
            if (r.minutes < 0) r.minutes = 0;
        }
        for (Project p : projects) {
            if (p.title == null || p.title.trim().isEmpty()) p.title = "Projeto";
            if (p.description == null) p.description = "";
            if (p.targetDate == null) p.targetDate = "";
        }
    }

    public void save() {
        try {
            JSONObject root = new JSONObject();
            JSONArray t = new JSONArray(), g = new JSONArray(), r = new JSONArray(), c = new JSONArray(), p = new JSONArray(), f = new JSONArray();
            for (Task item : tasks) t.put(item.toJson());
            for (Goal item : goals) g.put(item.toJson());
            for (Routine item : routines) r.put(item.toJson());
            for (Completion item : completions) c.put(item.toJson());
            for (Project item : projects) p.put(item.toJson());
            for (FocusSession item : focusSessions) f.put(item.toJson());
            root.put("tasks", t); root.put("goals", g); root.put("routines", r); root.put("completions", c); root.put("projects", p); root.put("focusSessions", f);
            root.put("schemaVersion", 5);
            prefs.edit().putString(KEY_DATA, root.toString()).apply();
            try { RitmoWidgetProvider.updateAll(appContext); } catch (Throwable ignored) { }
        } catch (Exception ignored) { }
    }

    private void migrateLegacyCompletionHistory() {
        if (!completions.isEmpty()) return;
        boolean changed = false;
        for (Task t : tasks) {
            if ("done".equals(t.status)) {
                completions.add(new Completion(t.id, t.title, t.date, t.category, t.minutes)); changed = true;
            }
        }
        if (changed) save();
    }

    public Task findTask(long id) {
        for (Task t : tasks) if (t.id == id) return t;
        return null;
    }

    public Project findProject(long id) {
        for (Project p : projects) if (p.id == id) return p;
        return null;
    }

    public Routine findRoutine(long id) {
        for (Routine r : routines) if (r.id == id) return r;
        return null;
    }

    public String projectTitle(long id) {
        Project p = findProject(id);
        return p == null ? "Sem projeto" : p.title;
    }

    public void toggleTask(Task task) {
        if ("done".equals(task.status)) {
            task.status = "todo";
            for (int i = completions.size() - 1; i >= 0; i--) {
                Completion c = completions.get(i);
                if (c.taskId == task.id && task.date.equals(c.date)) completions.remove(i);
            }
        } else {
            task.status = "done";
            boolean exists = false;
            for (Completion c : completions) if (c.taskId == task.id && task.date.equals(c.date)) { exists = true; break; }
            if (!exists) completions.add(new Completion(task.id, task.title, task.date, task.category, task.minutes));
        }
        save();
    }

    public void setTaskStatus(Task task, String status) {
        if ("done".equals(status) && !"done".equals(task.status)) {
            task.status = "done";
            boolean exists = false;
            for (Completion c : completions) if (c.taskId == task.id && task.date.equals(c.date)) exists = true;
            if (!exists) completions.add(new Completion(task.id, task.title, task.date, task.category, task.minutes));
        } else if (!"done".equals(status) && "done".equals(task.status)) {
            task.status = status;
            for (int i = completions.size() - 1; i >= 0; i--) {
                Completion c = completions.get(i);
                if (c.taskId == task.id && task.date.equals(c.date)) completions.remove(i);
            }
        } else task.status = status;
        save();
    }

    public void normalizeRecurringTasks() {
        String today = today(); boolean changed = false;
        for (Task task : tasks) {
            if ("none".equals(task.recurrence) || !"done".equals(task.status)) continue;
            if (task.date.compareTo(today) >= 0) continue;
            String next = task.date; int guard = 0;
            do { next = nextOccurrence(next, task.recurrence); guard++; }
            while (next.compareTo(today) < 0 && guard < 370);
            task.date = next; task.status = "todo"; changed = true;
        }
        if (changed) save();
    }

    public static String nextOccurrence(String iso, String recurrence) {
        Calendar c = Calendar.getInstance(); c.setTime(parse(iso));
        if ("weekly".equals(recurrence)) c.add(Calendar.DAY_OF_MONTH, 7);
        else if ("monthly".equals(recurrence)) c.add(Calendar.MONTH, 1);
        else {
            c.add(Calendar.DAY_OF_MONTH, 1);
            if ("weekdays".equals(recurrence)) {
                while (c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) c.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
        return format(c.getTime());
    }

    public int completedOn(String iso) { int total = 0; for (Completion c : completions) if (iso.equals(c.date)) total++; return total; }
    public int completedMinutesOn(String iso) { int total = 0; for (Completion c : completions) if (iso.equals(c.date)) total += c.minutes; return total; }
    public int plannedMinutesOn(String iso) { int total = 0; for (Task t : tasks) if (iso.equals(t.date)) total += t.minutes; return total; }
    public int taskCountOn(String iso) { int total = 0; for (Task t : tasks) if (iso.equals(t.date)) total++; return total; }

    public int completionPercentOn(String iso) {
        int total = 0, done = 0;
        for (Task t : tasks) if (iso.equals(t.date)) { total++; if ("done".equals(t.status)) done++; }
        return total == 0 ? 0 : Math.round(done * 100f / total);
    }

    public int[] last7CompletionCounts() {
        int[] values = new int[7]; String start = addDays(today(), -6);
        for (int i = 0; i < 7; i++) values[i] = completedOn(addDays(start, i));
        return values;
    }

    public String[] last7Labels() {
        String[] labels = new String[7]; String start = addDays(today(), -6);
        SimpleDateFormat out = new SimpleDateFormat("EEE", new Locale("pt", "BR"));
        for (int i = 0; i < 7; i++) {
            String raw = out.format(parse(addDays(start, i))).replace(".", "");
            labels[i] = raw.substring(0, Math.min(3, raw.length())).toUpperCase(new Locale("pt", "BR"));
        }
        return labels;
    }

    public Map<String,Integer> categoryMinutesLast7() {
        LinkedHashMap<String,Integer> map = new LinkedHashMap<>(); String cutoff = addDays(today(), -6);
        for (Completion c : completions) {
            if (c.date.compareTo(cutoff) < 0 || c.date.compareTo(today()) > 0) continue;
            map.put(c.category, map.getOrDefault(c.category, 0) + c.minutes);
        }
        return map;
    }

    public int totalCompletedLast7() {
        int total = 0; String start = addDays(today(), -6);
        for (int i = 0; i < 7; i++) total += completedOn(addDays(start, i));
        return total;
    }

    public int totalCompletedMinutesLast7() {
        int total = 0; String start = addDays(today(), -6);
        for (int i = 0; i < 7; i++) total += completedMinutesOn(addDays(start, i));
        return total;
    }

    public int plannedMinutesLast7() {
        int total = 0; String start = addDays(today(), -6);
        for (int i = 0; i < 7; i++) total += plannedMinutesOn(addDays(start, i));
        return total;
    }

    public int completionRateLast7() {
        int total = 0, done = 0; String start = addDays(today(), -6);
        for (Task t : tasks) {
            if (t.date.compareTo(start) < 0 || t.date.compareTo(today()) > 0) continue;
            total++; if ("done".equals(t.status)) done++;
        }
        return total == 0 ? 0 : Math.round(done * 100f / total);
    }

    public int executionEfficiencyLast7() {
        int planned = plannedMinutesLast7();
        int completed = totalCompletedMinutesLast7();
        if (planned <= 0 && completed <= 0) return 0;
        planned = Math.max(planned, completed);
        return Math.min(100, Math.round(completed * 100f / Math.max(1, planned)));
    }

    public int overdueOpenCount() {
        int total = 0;
        for (Task t : tasks) if (!"done".equals(t.status) && t.date.compareTo(today()) < 0) total++;
        return total;
    }

    public String bestCompletionDayLast7() {
        int best = -1; String bestDate = today(); String start = addDays(today(), -6);
        for (int i = 0; i < 7; i++) {
            String d = addDays(start, i); int count = completedOn(d);
            if (count > best) { best = count; bestDate = d; }
        }
        return best <= 0 ? "—" : new SimpleDateFormat("EEE", new Locale("pt", "BR")).format(parse(bestDate)).replace(".", "");
    }

    public Routine bestRoutineByStreak() {
        Routine best = null; int max = -1;
        for (Routine r : routines) { int s = r.streak(today()); if (s > max) { max = s; best = r; } }
        return best;
    }

    public int projectTaskCount(long projectId) { int n = 0; for (Task t : tasks) if (t.projectId == projectId) n++; return n; }
    public int projectDoneCount(long projectId) { int n = 0; for (Task t : tasks) if (t.projectId == projectId && "done".equals(t.status)) n++; return n; }
    public int projectProgress(long projectId) { int total = projectTaskCount(projectId); return total == 0 ? 0 : Math.round(projectDoneCount(projectId) * 100f / total); }

    public void addFocusSession(FocusSession session) {
        if (session == null || session.actualMinutes <= 0) return;
        focusSessions.add(session);
        save();
    }

    public int focusMinutesOn(String iso) {
        int total = 0;
        for (FocusSession f : focusSessions) if (iso.equals(f.date)) total += Math.max(0, f.actualMinutes);
        return total;
    }

    public int focusMinutesLast7() {
        int total = 0; String start = addDays(today(), -6);
        for (FocusSession f : focusSessions) {
            if (f.date.compareTo(start) >= 0 && f.date.compareTo(today()) <= 0) total += Math.max(0, f.actualMinutes);
        }
        return total;
    }

    public int focusPlannedMinutesLast7() {
        int total = 0; String start = addDays(today(), -6);
        for (FocusSession f : focusSessions) {
            if (f.date.compareTo(start) >= 0 && f.date.compareTo(today()) <= 0) total += Math.max(0, f.plannedMinutes);
        }
        return total;
    }

    public int focusAdherenceLast7() {
        int planned = focusPlannedMinutesLast7();
        if (planned <= 0) return 0;
        return Math.min(150, Math.round(focusMinutesLast7() * 100f / planned));
    }

    public int focusSessionCountLast7() {
        int total = 0; String start = addDays(today(), -6);
        for (FocusSession f : focusSessions) if (f.date.compareTo(start) >= 0 && f.date.compareTo(today()) <= 0) total++;
        return total;
    }

    public int[] last7FocusMinutes() {
        int[] values = new int[7]; String start = addDays(today(), -6);
        for (int i = 0; i < 7; i++) values[i] = focusMinutesOn(addDays(start, i));
        return values;
    }

    public int replanOverdueFlexibleToToday() {
        int moved = 0;
        String today = today();
        for (Task t : tasks) {
            if ("done".equals(t.status) || !t.flexible || !"none".equals(t.recurrence)) continue;
            if (t.date.compareTo(today) >= 0) continue;
            String due = (t.deadline == null || t.deadline.length() != 10) ? today : t.deadline;
            if (due.compareTo(today) < 0) due = today;
            t.date = today;
            if (t.time == null) t.time = "";
            t.deadline = due;
            moved++;
        }
        if (moved > 0) save();
        return moved;
    }

    public int routineCompletionPercentOn(String iso) {
        int due = 0, done = 0;
        for (Routine r : routines) {
            if (!r.dueOn(iso)) continue;
            due++;
            if (r.doneOn(iso)) done++;
        }
        return due == 0 ? 0 : Math.round(done * 100f / due);
    }

    public int combinedDayScore(String iso) {
        int taskCount = taskCountOn(iso);
        int routineDue = 0;
        for (Routine r : routines) if (r.dueOn(iso)) routineDue++;
        if (taskCount == 0 && routineDue == 0) return 0;
        int taskScore = taskCount == 0 ? 100 : completionPercentOn(iso);
        int routineScore = routineDue == 0 ? 100 : routineCompletionPercentOn(iso);
        if (taskCount == 0) return routineScore;
        if (routineDue == 0) return taskScore;
        return Math.round(taskScore * .65f + routineScore * .35f);
    }

    public int averageScoreLast30() {
        int sum = 0, activeDays = 0;
        for (int i = 29; i >= 0; i--) {
            String d = addDays(today(), -i);
            boolean has = taskCountOn(d) > 0;
            if (!has) for (Routine r : routines) if (r.dueOn(d)) { has = true; break; }
            if (!has) continue;
            sum += combinedDayScore(d);
            activeDays++;
        }
        return activeDays == 0 ? 0 : Math.round(sum * 1f / activeDays);
    }

    public int[] last30Scores() {
        int[] values = new int[30];
        String start = addDays(today(), -29);
        for (int i = 0; i < 30; i++) values[i] = combinedDayScore(addDays(start, i));
        return values;
    }

    private void seed() {
        String d = today();
        Project project = new Project(System.currentTimeMillis() + 50, "Projeto pessoal", "Organizar e executar as próximas etapas.", addDays(d, 30));
        projects.add(project);

        Task a = new Task(System.currentTimeMillis() + 1, "Revisar conteúdo de Redes", "Revisar anotações e fazer 10 questões.", d, "09:00", "auto", 60, "Estudos", "todo", "weekdays", 10);
        a.subtasks.add(new Subtask(System.currentTimeMillis() + 101, "Revisar anotações", false));
        a.subtasks.add(new Subtask(System.currentTimeMillis() + 102, "Resolver 10 questões", false));
        tasks.add(a);
        tasks.add(new Task(System.currentTimeMillis() + 2, "Organizar projeto pessoal", "Separar prioridades e quebrar o projeto em pequenas etapas.", d, "14:30", "medium", 90, "Projeto", "doing", "none", 30, project.id));
        tasks.add(new Task(System.currentTimeMillis() + 3, "Revisão do dia", "", d, "21:40", "low", 20, "Pessoal", "done", "daily", -1));
        completions.add(new Completion(tasks.get(2).id, tasks.get(2).title, d, tasks.get(2).category, tasks.get(2).minutes));

        goals.add(new Goal(System.currentTimeMillis() + 11, "Fortalecer conhecimentos em Redes", 68, addDays(d, 45)));
        goals.add(new Goal(System.currentTimeMillis() + 12, "Concluir projeto pessoal", 42, addDays(d, 30)));
        goals.add(new Goal(System.currentTimeMillis() + 13, "Manter rotina semanal", 76, addDays(d, 14)));

        routines.add(new Routine(System.currentTimeMillis() + 21, "Planejar o dia", "Definir as 3 prioridades", "daily", 10, d));
        routines.add(new Routine(System.currentTimeMillis() + 22, "Bloco de foco", "Sem notificações e sem multitarefa", "weekdays", 60, d));
        routines.add(new Routine(System.currentTimeMillis() + 23, "Revisão noturna", "Fechar pendências e preparar amanhã", "daily", 20, d));
    }

    public static class Task {
        public long id, projectId;
        public String title, description, date, time, deadline, priority, category, status, recurrence;
        public int minutes, reminderMinutes;
        public boolean flexible;
        public List<Subtask> subtasks = new ArrayList<>();

        public Task(long id, String title, String description, String date, String time, String priority, int minutes,
                    String category, String status, String recurrence, int reminderMinutes) {
            this(id, title, description, date, time, priority, minutes, category, status, recurrence, reminderMinutes, 0L);
        }

        public Task(long id, String title, String description, String date, String time, String priority, int minutes,
                    String category, String status, String recurrence, int reminderMinutes, long projectId) {
            this(id, title, description, date, time, priority, minutes, category, status, recurrence, reminderMinutes,
                    projectId, date, false);
        }

        public Task(long id, String title, String description, String date, String time, String priority, int minutes,
                    String category, String status, String recurrence, int reminderMinutes, long projectId,
                    String deadline, boolean flexible) {
            this.id = id; this.title = title; this.description = description; this.date = date; this.time = time;
            this.priority = priority; this.minutes = minutes; this.category = category; this.status = status;
            this.recurrence = recurrence; this.reminderMinutes = reminderMinutes; this.projectId = projectId;
            this.deadline = (deadline == null || deadline.length() != 10) ? date : deadline;
            this.flexible = flexible;
        }

        public String effectivePriority() {
            if (!"auto".equals(priority)) return priority;
            if ("done".equals(status)) return "low";
            String reference = deadline == null || deadline.length() != 10 ? date : deadline;
            int days = daysBetween(today(), reference);
            if (days <= 1) return "high";
            if (days <= 3) return "medium";
            return "low";
        }

        public int completedSubtasks() { int n = 0; for (Subtask s : subtasks) if (s.done) n++; return n; }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("title", title); o.put("description", description); o.put("date", date); o.put("time", time);
            o.put("deadline", deadline); o.put("flexible", flexible);
            o.put("priority", priority); o.put("minutes", minutes); o.put("category", category); o.put("status", status);
            o.put("recurrence", recurrence); o.put("reminderMinutes", reminderMinutes); o.put("projectId", projectId);
            JSONArray a = new JSONArray(); for (Subtask s : subtasks) a.put(s.toJson()); o.put("subtasks", a);
            return o;
        }

        static Task fromJson(JSONObject o) {
            String date = o.optString("date", today());
            Task t = new Task(o.optLong("id", System.currentTimeMillis()), o.optString("title", "Tarefa"), o.optString("description", ""),
                    date, o.optString("time", ""), o.optString("priority", "low"), o.optInt("minutes", 30),
                    o.optString("category", "Pessoal"), o.optString("status", "todo"), o.optString("recurrence", "none"),
                    o.has("reminderMinutes") ? o.optInt("reminderMinutes", -1) : -1, o.optLong("projectId", 0L),
                    o.optString("deadline", date), o.optBoolean("flexible", false));
            JSONArray a = o.optJSONArray("subtasks");
            if (a != null) for (int i = 0; i < a.length(); i++) t.subtasks.add(Subtask.fromJson(a.optJSONObject(i)));
            return t;
        }
    }

    public static class Subtask {
        public long id; public String title; public boolean done;
        public Subtask(long id, String title, boolean done) { this.id = id; this.title = title; this.done = done; }
        JSONObject toJson() throws Exception { JSONObject o = new JSONObject(); o.put("id", id); o.put("title", title); o.put("done", done); return o; }
        static Subtask fromJson(JSONObject o) { if (o == null) return new Subtask(System.currentTimeMillis(), "Subtarefa", false); return new Subtask(o.optLong("id", System.currentTimeMillis()), o.optString("title", "Subtarefa"), o.optBoolean("done", false)); }
    }

    public static class Project {
        public long id; public String title, description, targetDate;
        public Project(long id, String title, String description, String targetDate) { this.id = id; this.title = title; this.description = description; this.targetDate = targetDate; }
        JSONObject toJson() throws Exception { JSONObject o = new JSONObject(); o.put("id", id); o.put("title", title); o.put("description", description); o.put("targetDate", targetDate); return o; }
        static Project fromJson(JSONObject o) { return new Project(o.optLong("id", System.currentTimeMillis()), o.optString("title", "Projeto"), o.optString("description", ""), o.optString("targetDate", "")); }
    }

    public static class Goal {
        public long id; public String title; public int progress; public String targetDate;
        public Goal(long id, String title, int progress, String targetDate) { this.id = id; this.title = title; this.progress = progress; this.targetDate = targetDate; }
        JSONObject toJson() throws Exception { JSONObject o = new JSONObject(); o.put("id", id); o.put("title", title); o.put("progress", progress); o.put("targetDate", targetDate); return o; }
        static Goal fromJson(JSONObject o) { return new Goal(o.optLong("id", System.currentTimeMillis()), o.optString("title", "Meta"), o.optInt("progress", 0), o.optString("targetDate", "")); }
    }

    public static class Routine {
        public long id;
        public String title, detail, frequency, startDate, time, category, accent;
        public int minutes, reminderMinutes, daysMask;
        public final List<String> doneDates = new ArrayList<>();

        public Routine(long id, String title, String detail, String frequency, int minutes, String startDate) {
            this(id, title, detail, frequency, minutes, startDate, "", "Pessoal", "violet", -1, 0);
        }

        public Routine(long id, String title, String detail, String frequency, int minutes, String startDate,
                       String time, String category, String accent, int reminderMinutes) {
            this(id, title, detail, frequency, minutes, startDate, time, category, accent, reminderMinutes, 0);
        }

        public Routine(long id, String title, String detail, String frequency, int minutes, String startDate,
                       String time, String category, String accent, int reminderMinutes, int daysMask) {
            this.id = id;
            this.title = title;
            this.detail = detail;
            this.frequency = frequency;
            this.minutes = minutes;
            this.startDate = startDate;
            this.time = time == null ? "" : time;
            this.category = category == null || category.trim().isEmpty() ? "Pessoal" : category;
            this.accent = accent == null || accent.trim().isEmpty() ? "violet" : accent;
            this.reminderMinutes = reminderMinutes;
            this.daysMask = daysMask;
        }

        public boolean doneOn(String date) { return doneDates.contains(date); }
        public void toggle(String date) { if (doneDates.contains(date)) doneDates.remove(date); else doneDates.add(date); }

        public boolean dueOn(String date) {
            Calendar c = Calendar.getInstance(); c.setTime(parse(date)); int dow = c.get(Calendar.DAY_OF_WEEK);
            if ("custom".equals(frequency)) {
                int bit = 1 << (dow - 1);
                return daysMask != 0 && (daysMask & bit) != 0;
            }
            if ("weekdays".equals(frequency)) return dow != Calendar.SATURDAY && dow != Calendar.SUNDAY;
            if ("weekly".equals(frequency)) { Calendar start = Calendar.getInstance(); start.setTime(parse(startDate)); return start.get(Calendar.DAY_OF_WEEK) == dow; }
            return true;
        }

        public int streak(String referenceDate) {
            String cursor = referenceDate; if (dueOn(cursor) && !doneOn(cursor)) cursor = addDays(cursor, -1);
            int streak = 0, guard = 0;
            while (guard < 370) {
                if (!dueOn(cursor)) { cursor = addDays(cursor, -1); guard++; continue; }
                if (!doneOn(cursor)) break;
                streak++; cursor = addDays(cursor, -1); guard++;
            }
            return streak;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("title", title); o.put("detail", detail); o.put("frequency", frequency);
            o.put("minutes", minutes); o.put("startDate", startDate); o.put("time", time); o.put("category", category);
            o.put("accent", accent); o.put("reminderMinutes", reminderMinutes); o.put("daysMask", daysMask);
            JSONArray a = new JSONArray(); for (String d : doneDates) a.put(d); o.put("doneDates", a);
            return o;
        }

        static Routine fromJson(JSONObject o) {
            String detail = o.optString("detail", "Recorrente");
            String frequency = o.optString("frequency", inferLegacyFrequency(detail));
            Routine r = new Routine(
                    o.optLong("id", System.currentTimeMillis()),
                    o.optString("title", "Rotina"),
                    detail,
                    frequency,
                    o.optInt("minutes", inferLegacyMinutes(detail)),
                    o.optString("startDate", today()),
                    o.optString("time", ""),
                    o.optString("category", "Pessoal"),
                    o.optString("accent", "violet"),
                    o.optInt("reminderMinutes", -1),
                    o.optInt("daysMask", 0)
            );
            JSONArray a = o.optJSONArray("doneDates");
            if (a != null) for (int i = 0; i < a.length(); i++) r.doneDates.add(a.optString(i));
            return r;
        }

        private static String inferLegacyFrequency(String detail) {
            String l = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
            if (l.contains("seg a sex")) return "weekdays";
            if (l.contains("semana")) return "weekly";
            return "daily";
        }

        private static int inferLegacyMinutes(String detail) {
            if (detail == null) return 15;
            String digits = detail.replaceAll("[^0-9]", " ").trim();
            if (digits.isEmpty()) return 15;
            try { return Integer.parseInt(digits.split("\\s+")[0]); } catch (Exception e) { return 15; }
        }
    }

    public static class FocusSession {
        public long id, taskId, startedAt;
        public String title, date, mode;
        public int plannedMinutes, actualMinutes;

        public FocusSession(long id, long taskId, String title, String date, String mode,
                            int plannedMinutes, int actualMinutes, long startedAt) {
            this.id = id; this.taskId = taskId; this.title = title; this.date = date; this.mode = mode;
            this.plannedMinutes = plannedMinutes; this.actualMinutes = actualMinutes; this.startedAt = startedAt;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("taskId", taskId); o.put("title", title); o.put("date", date);
            o.put("mode", mode); o.put("plannedMinutes", plannedMinutes); o.put("actualMinutes", actualMinutes);
            o.put("startedAt", startedAt);
            return o;
        }

        static FocusSession fromJson(JSONObject o) {
            return new FocusSession(
                    o.optLong("id", System.currentTimeMillis()),
                    o.optLong("taskId", 0L),
                    o.optString("title", "Sessão de foco"),
                    o.optString("date", today()),
                    o.optString("mode", "Pomodoro"),
                    o.optInt("plannedMinutes", 25),
                    o.optInt("actualMinutes", 0),
                    o.optLong("startedAt", System.currentTimeMillis())
            );
        }
    }

    public static class Completion {
        public long taskId; public String title, date, category; public int minutes;
        public Completion(long taskId, String title, String date, String category, int minutes) { this.taskId = taskId; this.title = title; this.date = date; this.category = category; this.minutes = minutes; }
        JSONObject toJson() throws Exception { JSONObject o = new JSONObject(); o.put("taskId", taskId); o.put("title", title); o.put("date", date); o.put("category", category); o.put("minutes", minutes); return o; }
        static Completion fromJson(JSONObject o) { return new Completion(o.optLong("taskId"), o.optString("title"), o.optString("date"), o.optString("category", "Pessoal"), o.optInt("minutes", 0)); }
    }
}
