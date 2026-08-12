package com.ritmo.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planejador local e determinístico do Ritmo.
 * Não usa rede nem IA externa: distribui apenas tarefas marcadas como flexíveis.
 */
public final class SmartPlanner {
    private static final String PREFS = "ritmo_planner";
    private static final String KEY_ROLLBACK = "last_plan_rollback";

    private SmartPlanner() { }

    public static final class Settings {
        public final int startHour;
        public final int endHour;
        public final int capacityMinutes;
        public final boolean includeWeekend;

        public Settings(int startHour, int endHour, int capacityMinutes, boolean includeWeekend) {
            this.startHour = Math.max(0, Math.min(23, startHour));
            this.endHour = Math.max(this.startHour + 1, Math.min(24, endHour));
            this.capacityMinutes = Math.max(60, capacityMinutes);
            this.includeWeekend = includeWeekend;
        }
    }

    public static final class Assignment {
        public final Store.Task task;
        public final String oldDate, oldTime, newDate, newTime;
        public final boolean overCapacity;

        Assignment(Store.Task task, String oldDate, String oldTime, String newDate, String newTime, boolean overCapacity) {
            this.task = task;
            this.oldDate = oldDate == null ? "" : oldDate;
            this.oldTime = oldTime == null ? "" : oldTime;
            this.newDate = newDate == null ? "" : newDate;
            this.newTime = newTime == null ? "" : newTime;
            this.overCapacity = overCapacity;
        }

        public boolean moved() {
            return !oldDate.equals(newDate) || !oldTime.equals(newTime);
        }
    }

    public static final class Result {
        public final List<Assignment> assignments = new ArrayList<>();
        public final Map<String, Integer> loadMinutes = new LinkedHashMap<>();
        public int overloadedDays;
        public int eligibleTasks;
        public int movedTasks;

        public boolean isEmpty() { return assignments.isEmpty(); }
    }

    private static final class Interval {
        final int start, end;
        Interval(int start, int end) { this.start = start; this.end = end; }
    }

    public static Result plan(Store store, Settings settings) {
        Result result = new Result();
        String start = Store.today();
        String horizonEnd = Store.addDays(start, 6);

        Map<String, Integer> load = new LinkedHashMap<>();
        Map<String, List<Interval>> occupied = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            String date = Store.addDays(start, i);
            load.put(date, routineMinutes(store, date));
            occupied.put(date, new ArrayList<>());
        }

        List<Store.Task> eligible = new ArrayList<>();
        for (Store.Task task : store.tasks) {
            if ("done".equals(task.status)) continue;
            boolean canMove = task.flexible && "none".equals(task.recurrence);
            String due = effectiveDeadline(task);
            boolean relevantThisWeek = task.date.compareTo(horizonEnd) <= 0 || due.compareTo(horizonEnd) <= 0;
            if (canMove && relevantThisWeek) {
                eligible.add(task);
                continue;
            }
            if (task.date.compareTo(start) < 0 || task.date.compareTo(horizonEnd) > 0) continue;
            load.put(task.date, load.get(task.date) + Math.max(0, task.minutes));
            addOccupied(occupied.get(task.date), task.time, task.minutes);
        }
        result.eligibleTasks = eligible.size();

        Collections.sort(eligible, new Comparator<Store.Task>() {
            @Override public int compare(Store.Task a, Store.Task b) {
                int pa = priorityScore(a.effectivePriority());
                int pb = priorityScore(b.effectivePriority());
                if (pa != pb) return Integer.compare(pb, pa);
                String da = effectiveDeadline(a);
                String db = effectiveDeadline(b);
                int dc = da.compareTo(db);
                if (dc != 0) return dc;
                return Integer.compare(Math.max(0, b.minutes), Math.max(0, a.minutes));
            }
        });

        for (Store.Task task : eligible) {
            String deadline = effectiveDeadline(task);
            if (deadline.compareTo(start) < 0) deadline = start;
            if (deadline.compareTo(horizonEnd) > 0) deadline = horizonEnd;

            String bestDate = null;
            String bestTime = "";
            int bestLoad = Integer.MAX_VALUE;
            boolean bestFits = false;

            for (int i = 0; i < 7; i++) {
                String date = Store.addDays(start, i);
                if (date.compareTo(deadline) > 0) break;
                if (!settings.includeWeekend && isWeekend(date)) continue;

                int current = load.get(date);
                int mins = Math.max(15, task.minutes);
                String slot = findSlot(occupied.get(date), settings.startHour * 60, settings.endHour * 60, mins);
                boolean fitsCapacity = current + mins <= settings.capacityMinutes;
                boolean hasSlot = !slot.isEmpty();
                boolean fits = fitsCapacity && hasSlot;

                if (bestDate == null || (fits && !bestFits) || (fits == bestFits && current < bestLoad)) {
                    bestDate = date;
                    bestTime = hasSlot ? slot : "";
                    bestLoad = current;
                    bestFits = fits;
                }
            }

            // Se o usuário desativou fins de semana e o prazo cair no fim de semana,
            // ainda escolhemos o dia útil com menor carga dentro da janela.
            if (bestDate == null) {
                for (int i = 0; i < 7; i++) {
                    String date = Store.addDays(start, i);
                    if (!settings.includeWeekend && isWeekend(date)) continue;
                    int current = load.get(date);
                    if (bestDate == null || current < bestLoad) {
                        bestDate = date;
                        bestLoad = current;
                        bestTime = findSlot(occupied.get(date), settings.startHour * 60, settings.endHour * 60, Math.max(15, task.minutes));
                    }
                }
            }
            if (bestDate == null) bestDate = start;

            int mins = Math.max(15, task.minutes);
            boolean over = load.get(bestDate) + mins > settings.capacityMinutes || bestTime.isEmpty();
            Assignment a = new Assignment(task, task.date, task.time, bestDate, bestTime, over);
            result.assignments.add(a);
            if (a.moved()) result.movedTasks++;
            load.put(bestDate, load.get(bestDate) + mins);
            if (!bestTime.isEmpty()) addOccupied(occupied.get(bestDate), bestTime, mins);
        }

        result.loadMinutes.putAll(load);
        int overloaded = 0;
        for (Integer value : load.values()) if (value > settings.capacityMinutes) overloaded++;
        result.overloadedDays = overloaded;
        return result;
    }

    public static void apply(Context context, Store store, Result result) {
        if (result == null || result.assignments.isEmpty()) return;
        saveRollback(context, result);
        for (Assignment a : result.assignments) {
            a.task.date = a.newDate;
            a.task.time = a.newTime;
        }
        store.save();
    }

    public static boolean canUndo(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ROLLBACK, null);
        return raw != null && !raw.trim().isEmpty();
    }

    public static int undo(Context context, Store store) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_ROLLBACK, null);
        if (raw == null || raw.trim().isEmpty()) return 0;
        int restored = 0;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Store.Task task = store.findTask(o.optLong("id", -1));
                if (task == null) continue;
                task.date = o.optString("date", task.date);
                task.time = o.optString("time", task.time);
                restored++;
            }
            store.save();
        } catch (Exception ignored) { }
        prefs.edit().remove(KEY_ROLLBACK).apply();
        return restored;
    }

    private static void saveRollback(Context context, Result result) {
        try {
            JSONArray arr = new JSONArray();
            for (Assignment a : result.assignments) {
                JSONObject o = new JSONObject();
                o.put("id", a.task.id);
                o.put("date", a.oldDate);
                o.put("time", a.oldTime);
                arr.put(o);
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ROLLBACK, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static int routineMinutes(Store store, String date) {
        int total = 0;
        for (Store.Routine r : store.routines) if (r.dueOn(date) && !r.doneOn(date)) total += Math.max(0, r.minutes);
        return total;
    }

    private static String effectiveDeadline(Store.Task task) {
        String d = task.deadline;
        if (d == null || d.length() != 10) d = task.date;
        if (d == null || d.length() != 10) d = Store.today();
        return d;
    }

    private static int priorityScore(String p) {
        if ("high".equals(p)) return 3;
        if ("medium".equals(p)) return 2;
        return 1;
    }

    private static boolean isWeekend(String iso) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(Store.parse(iso));
        int dow = c.get(java.util.Calendar.DAY_OF_WEEK);
        return dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY;
    }

    private static void addOccupied(List<Interval> list, String time, int minutes) {
        int start = parseMinutes(time);
        if (start < 0 || minutes <= 0) return;
        list.add(new Interval(start, start + minutes));
    }

    private static String findSlot(List<Interval> occupied, int start, int end, int duration) {
        if (duration <= 0 || end <= start || duration > end - start) return "";
        for (int minute = start; minute + duration <= end; minute += 15) {
            boolean collision = false;
            for (Interval interval : occupied) {
                if (minute < interval.end && minute + duration > interval.start) {
                    collision = true;
                    break;
                }
            }
            if (!collision) return formatMinutes(minute);
        }
        return "";
    }

    private static int parseMinutes(String time) {
        if (time == null || time.length() < 4 || !time.contains(":")) return -1;
        try {
            String[] p = time.split(":");
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (Exception e) { return -1; }
    }

    private static String formatMinutes(int minute) {
        int h = Math.max(0, Math.min(23, minute / 60));
        int m = Math.max(0, Math.min(59, minute % 60));
        return String.format(java.util.Locale.US, "%02d:%02d", h, m);
    }
}
