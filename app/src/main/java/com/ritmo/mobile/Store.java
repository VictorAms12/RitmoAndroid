package com.ritmo.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Store {
    private static final String PREFS = "ritmo_prefs";
    private static final String KEY_DATA = "ritmo_data";

    public final List<Task> tasks = new ArrayList<>();
    public final List<Goal> goals = new ArrayList<>();
    public final List<Routine> routines = new ArrayList<>();

    private final SharedPreferences prefs;

    public Store(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", new Locale("pt", "BR")).format(new Date());
    }

    private void load() {
        String raw = prefs.getString(KEY_DATA, null);
        if (raw == null || raw.trim().isEmpty()) {
            seed();
            save();
            return;
        }

        try {
            JSONObject root = new JSONObject(raw);
            JSONArray t = root.optJSONArray("tasks");
            JSONArray g = root.optJSONArray("goals");
            JSONArray r = root.optJSONArray("routines");

            tasks.clear();
            goals.clear();
            routines.clear();

            if (t != null) {
                for (int i = 0; i < t.length(); i++) tasks.add(Task.fromJson(t.getJSONObject(i)));
            }
            if (g != null) {
                for (int i = 0; i < g.length(); i++) goals.add(Goal.fromJson(g.getJSONObject(i)));
            }
            if (r != null) {
                for (int i = 0; i < r.length(); i++) routines.add(Routine.fromJson(r.getJSONObject(i)));
            }
        } catch (Exception e) {
            tasks.clear();
            goals.clear();
            routines.clear();
            seed();
            save();
        }
    }

    public void save() {
        try {
            JSONObject root = new JSONObject();
            JSONArray t = new JSONArray();
            JSONArray g = new JSONArray();
            JSONArray r = new JSONArray();
            for (Task task : tasks) t.put(task.toJson());
            for (Goal goal : goals) g.put(goal.toJson());
            for (Routine routine : routines) r.put(routine.toJson());
            root.put("tasks", t);
            root.put("goals", g);
            root.put("routines", r);
            prefs.edit().putString(KEY_DATA, root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void seed() {
        String d = today();
        tasks.add(new Task(System.currentTimeMillis() + 1, "Revisar conteúdo de Redes", d, "09:00", "high", 60, "Estudos", "todo"));
        tasks.add(new Task(System.currentTimeMillis() + 2, "Organizar projeto pessoal", d, "14:30", "medium", 90, "Projeto", "doing"));
        tasks.add(new Task(System.currentTimeMillis() + 3, "Revisão do dia", d, "21:40", "low", 20, "Pessoal", "done"));

        goals.add(new Goal(System.currentTimeMillis() + 11, "Fortalecer conhecimentos em Redes", 68));
        goals.add(new Goal(System.currentTimeMillis() + 12, "Concluir projeto pessoal", 42));
        goals.add(new Goal(System.currentTimeMillis() + 13, "Manter rotina semanal", 76));

        routines.add(new Routine(System.currentTimeMillis() + 21, "Planejar o dia", "Todos os dias · 10 min"));
        routines.add(new Routine(System.currentTimeMillis() + 22, "Bloco de foco", "Seg a Sex · 60 min"));
        routines.add(new Routine(System.currentTimeMillis() + 23, "Revisão noturna", "Todos os dias · 20 min"));
    }

    public static class Task {
        public long id;
        public String title;
        public String date;
        public String time;
        public String priority;
        public int minutes;
        public String category;
        public String status;

        public Task(long id, String title, String date, String time, String priority, int minutes, String category, String status) {
            this.id = id;
            this.title = title;
            this.date = date;
            this.time = time;
            this.priority = priority;
            this.minutes = minutes;
            this.category = category;
            this.status = status;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("date", date);
            o.put("time", time);
            o.put("priority", priority);
            o.put("minutes", minutes);
            o.put("category", category);
            o.put("status", status);
            return o;
        }

        static Task fromJson(JSONObject o) {
            return new Task(
                    o.optLong("id", System.currentTimeMillis()),
                    o.optString("title", "Tarefa"),
                    o.optString("date", today()),
                    o.optString("time", ""),
                    o.optString("priority", "low"),
                    o.optInt("minutes", 30),
                    o.optString("category", "Pessoal"),
                    o.optString("status", "todo")
            );
        }
    }

    public static class Goal {
        public long id;
        public String title;
        public int progress;

        public Goal(long id, String title, int progress) {
            this.id = id;
            this.title = title;
            this.progress = progress;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("progress", progress);
            return o;
        }

        static Goal fromJson(JSONObject o) {
            return new Goal(o.optLong("id", System.currentTimeMillis()), o.optString("title", "Meta"), o.optInt("progress", 0));
        }
    }

    public static class Routine {
        public long id;
        public String title;
        public String detail;

        public Routine(long id, String title, String detail) {
            this.id = id;
            this.title = title;
            this.detail = detail;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("detail", detail);
            return o;
        }

        static Routine fromJson(JSONObject o) {
            return new Routine(o.optLong("id", System.currentTimeMillis()), o.optString("title", "Rotina"), o.optString("detail", "Recorrente"));
        }
    }
}
