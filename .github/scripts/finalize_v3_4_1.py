from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    Path(path).write_text(text, encoding='utf-8', newline='\n')


def replace(path: str, old: str, new: str, label: str) -> None:
    text = read(path)
    if old not in text:
        raise SystemExit(f'missing block: {label} ({path})')
    write(path, text.replace(old, new, 1))
    print(f'fixed: {label}')


# Flutter state consistency ----------------------------------------------------
app = 'lib/core/app_state.dart'

old = '''  Future<void> addOrUpdateTask(TaskItem task) async {
    final index = data.tasks.indexWhere((e) => e.id == task.id);
    if (index >= 0) {
      data.tasks[index] = task;
    } else {
      data.tasks.add(task);
    }
    notifyListeners();
    await save(syncReminders: false);
    await NativeBridge.syncTaskReminder(task.id);
  }
'''
new = '''  Future<void> addOrUpdateTask(TaskItem task) async {
    final index = data.tasks.indexWhere((e) => e.id == task.id);
    final previous = index >= 0 ? data.tasks[index] : null;
    if (index >= 0) {
      data.tasks[index] = task;
    } else {
      data.tasks.add(task);
    }

    // Keep completion history aligned if an already completed task is edited.
    if (previous != null && previous.status == 'done') {
      final completionIndex = data.completions.indexWhere(
        (e) => e.taskId == task.id && e.date == previous.date,
      );
      if (task.status == 'done') {
        final updated = CompletionItem(
          taskId: task.id,
          title: task.title,
          date: task.date,
          category: task.category,
          minutes: task.minutes,
        );
        if (completionIndex >= 0) {
          data.completions[completionIndex] = updated;
        } else {
          data.completions.add(updated);
        }
      } else if (completionIndex >= 0) {
        data.completions.removeAt(completionIndex);
      }
    } else if (task.status == 'done') {
      final exists = data.completions.any(
        (e) => e.taskId == task.id && e.date == task.date,
      );
      if (!exists) {
        data.completions.add(CompletionItem(
          taskId: task.id,
          title: task.title,
          date: task.date,
          category: task.category,
          minutes: task.minutes,
        ));
      }
    }

    notifyListeners();
    await save(syncReminders: false);
    await NativeBridge.syncTaskReminder(task.id);
  }
'''
replace(app, old, new, 'completion history after editing a done task')

old = '''    if (completeTask && focusTaskId != 0) {
      final task = taskById(focusTaskId);
      if (task != null && task.status != 'done') {
        task.status = 'done';
        data.completions.add(CompletionItem(
          taskId: task.id,
          title: task.title,
          date: task.date,
          category: task.category,
          minutes: task.minutes,
        ));
      }
    }
'''
new = '''    if (completeTask && focusTaskId != 0) {
      final task = taskById(focusTaskId);
      if (task != null && task.status != 'done') {
        task.status = 'done';
        final exists = data.completions.any(
          (e) => e.taskId == task.id && e.date == task.date,
        );
        if (!exists) {
          data.completions.add(CompletionItem(
            taskId: task.id,
            title: task.title,
            date: task.date,
            category: task.category,
            minutes: task.minutes,
          ));
        }
      }
    }
'''
replace(app, old, new, 'focus completion history dedupe')

old = '''    await NativeBridge.stopFocus();
    await save(syncReminders: !completeTask ? false : focusTaskId != 0);
  }
'''
new = '''    await NativeBridge.stopFocus();
    await save(syncReminders: false);
    if (completeTask && focusTaskId != 0) {
      await NativeBridge.syncTaskReminder(focusTaskId);
    }
  }
'''
replace(app, old, new, 'targeted focus completion reminder sync')

replace(
    app,
    '''        if (task.date == today &&
            task.status != 'done' &&
            task.flexible &&''',
    '''        if (!task.inbox &&
            task.date == today &&
            task.status != 'done' &&
            task.flexible &&''',
    'day review Inbox guard',
)

# Remove now-unused demo seeding method from Flutter state.
text = read(app)
text, count = re.subn(
    r'\n  RitmoData _seed\(\) \{.*?\n  \}\n\n  Future<void> save',
    '\n  Future<void> save',
    text,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit('could not remove Flutter seed method')
write(app, text)

# Calendar: build month metrics once rather than rescanning all data several
# times for every one of the 28-42 day cells.
calendar = 'lib/screens/calendar_page.dart'
replace(
    calendar,
    '''    final totalCells = ((offset + days) / 7).ceil() * 7;
    final today = state.today;

    return GridView.builder(''',
    '''    final totalCells = ((offset + days) / 7).ceil() * 7;
    final today = state.today;
    final taskTotals = <String, int>{};
    final taskDone = <String, int>{};
    for (final task in state.data.tasks) {
      if (task.inbox) continue;
      final date = task.date;
      final parsed = parseIso(date);
      if (parsed.year != month.year || parsed.month != month.month) continue;
      taskTotals[date] = (taskTotals[date] ?? 0) + 1;
      if (task.status == 'done') taskDone[date] = (taskDone[date] ?? 0) + 1;
    }

    final routineTotals = <String, int>{};
    final routineDone = <String, int>{};
    for (var day = 1; day <= days; day++) {
      final date = isoDate(DateTime(month.year, month.month, day));
      for (final routine in state.data.routines) {
        if (!routine.dueOn(date)) continue;
        routineTotals[date] = (routineTotals[date] ?? 0) + 1;
        if (routine.doneOn(date)) {
          routineDone[date] = (routineDone[date] ?? 0) + 1;
        }
      }
    }

    return GridView.builder(''',
    'calendar month metric precomputation',
)
replace(
    calendar,
    '''        final count = state.taskCountOn(date);
        final score = state.combinedDayScore(date);
''',
    '''        final count = taskTotals[date] ?? 0;
        final done = taskDone[date] ?? 0;
        final routineCount = routineTotals[date] ?? 0;
        final routineCompleted = routineDone[date] ?? 0;
        final taskScore = count == 0 ? 100 : (done * 100 / count).round();
        final routineScore = routineCount == 0
            ? 100
            : (routineCompleted * 100 / routineCount).round();
        final score = count == 0 && routineCount == 0
            ? 0
            : count == 0
                ? routineScore
                : routineCount == 0
                    ? taskScore
                    : (taskScore * .65 + routineScore * .35).round();
''',
    'calendar cached score use',
)

# Android Store data safety ----------------------------------------------------
store = 'android/app/src/main/java/com/ritmo/mobile/Store.java'
replace(
    store,
    '''        } catch (Exception e) {
            try { prefs.edit().putString("ritmo_data_corrupt_backup", raw).apply(); } catch (Throwable ignored) { }
            tasks.clear(); goals.clear(); routines.clear(); completions.clear(); projects.clear(); focusSessions.clear(); dayReviews.clear();
            seed(); save();
        }
''',
    '''        } catch (Exception e) {
            // Preserve the unreadable payload. Never overwrite user data with
            // demonstration content merely because parsing failed.
            try { prefs.edit().putString("ritmo_data_corrupt_backup", raw).apply(); } catch (Throwable ignored) { }
            tasks.clear(); goals.clear(); routines.clear(); completions.clear(); projects.clear(); focusSessions.clear(); dayReviews.clear();
        }
''',
    'native corrupt data preservation',
)

# Stronger native sanitation keeps notification/widget writes compatible with
# the same invariants enforced by Dart.
replace(
    store,
    '''            if (t.priority == null) t.priority = "low";
            if (t.category == null || t.category.trim().isEmpty()) t.category = "Pessoal";
            if (t.status == null) t.status = "todo";
            if (t.recurrence == null) t.recurrence = "none";
            if (t.deadline == null || t.deadline.length() != 10) t.deadline = t.date;
            if (t.minutes < 0) t.minutes = 0;
            if (t.energy == null || t.energy.trim().isEmpty()) t.energy = "medium";
            if (t.preferredPeriod == null || t.preferredPeriod.trim().isEmpty()) t.preferredPeriod = "any";
''',
    '''            if (!"auto".equals(t.priority) && !"high".equals(t.priority) && !"medium".equals(t.priority) && !"low".equals(t.priority)) t.priority = "low";
            if (t.category == null || t.category.trim().isEmpty()) t.category = "Pessoal";
            if (!"todo".equals(t.status) && !"doing".equals(t.status) && !"done".equals(t.status)) t.status = "todo";
            if (!"none".equals(t.recurrence) && !"daily".equals(t.recurrence) && !"weekdays".equals(t.recurrence) && !"weekly".equals(t.recurrence) && !"monthly".equals(t.recurrence)) t.recurrence = "none";
            if (t.deadline == null || t.deadline.length() != 10) t.deadline = t.date;
            t.minutes = Math.max(0, Math.min(1440, t.minutes));
            if (!"low".equals(t.energy) && !"medium".equals(t.energy) && !"high".equals(t.energy)) t.energy = "medium";
            if (!"any".equals(t.preferredPeriod) && !"morning".equals(t.preferredPeriod) && !"afternoon".equals(t.preferredPeriod) && !"evening".equals(t.preferredPeriod)) t.preferredPeriod = "any";
            if (t.inbox) { t.time = ""; t.reminderMinutes = -1; }
''',
    'native task sanitation parity',
)
replace(
    store,
    '''            if (r.frequency == null) r.frequency = "daily";
            if (r.startDate == null || r.startDate.length() != 10) r.startDate = today();
            if (r.time == null) r.time = "";
            if (r.category == null || r.category.trim().isEmpty()) r.category = "Pessoal";
            if (r.accent == null || r.accent.trim().isEmpty()) r.accent = "indigo";
            if (r.minutes < 0) r.minutes = 0;
''',
    '''            if (!"daily".equals(r.frequency) && !"weekdays".equals(r.frequency) && !"weekly".equals(r.frequency) && !"custom".equals(r.frequency)) r.frequency = "daily";
            if (r.startDate == null || r.startDate.length() != 10) r.startDate = today();
            if (r.time == null) r.time = "";
            if (r.category == null || r.category.trim().isEmpty()) r.category = "Pessoal";
            if (r.accent == null || r.accent.trim().isEmpty()) r.accent = "indigo";
            r.minutes = Math.max(0, Math.min(1440, r.minutes));
''',
    'native routine sanitation parity',
)

# Remove the dead demo seed function on the native side too.
text = read(store)
text, count = re.subn(
    r'\n    private void seed\(\) \{.*?\n    \}\n\n    public DayReview findDayReview',
    '\n    public DayReview findDayReview',
    text,
    count=1,
    flags=re.S,
)
if count != 1:
    raise SystemExit('could not remove native seed method')
write(store, text)

# Recurring task reminders -----------------------------------------------------
reminder = 'android/app/src/main/java/com/ritmo/mobile/ReminderScheduler.java'
old = '''    public static void schedule(Context context, Store.Task task) {
        if (task == null) return;
        cancel(context, task.id);
        if ("done".equals(task.status) || task.inbox || task.reminderMinutes < 0 || task.time == null || task.time.trim().isEmpty()) return;
        try {
            Date due = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(task.date + " " + task.time);
            if (due == null) return;
            long when = due.getTime() - task.reminderMinutes * 60_000L;
            if (when <= System.currentTimeMillis()) return;
            scheduleAt(context, task, when);
        } catch (Exception ignored) { }
    }
'''
new = '''    public static void schedule(Context context, Store.Task task) {
        if (task == null) return;
        cancel(context, task.id);
        if (task.inbox || task.reminderMinutes < 0 || task.time == null || task.time.trim().isEmpty()) return;
        if ("done".equals(task.status) && "none".equals(task.recurrence)) return;
        try {
            String date = task.date;
            if ("done".equals(task.status)) {
                int guard = 0;
                do {
                    date = Store.nextOccurrence(date, task.recurrence);
                    guard++;
                } while (guard < 370 && reminderTime(date, task) <= System.currentTimeMillis());
            }
            long when = reminderTime(date, task);
            if (when <= System.currentTimeMillis()) return;
            schedulePending(context, task, when);
        } catch (Exception ignored) { }
    }

    private static long reminderTime(String date, Store.Task task) throws Exception {
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        parser.setLenient(false);
        Date due = parser.parse(date + " " + task.time);
        if (due == null) return 0L;
        return due.getTime() - task.reminderMinutes * 60_000L;
    }
'''
replace(reminder, old, new, 'next reminder for completed recurring tasks')
# scheduleAt is for snoozing a currently open task; route normal scheduling
# through an internal method that can also schedule a completed recurrence.
replace(
    reminder,
    '''    public static void scheduleAt(Context context, Store.Task task, long when) {
        if (task == null || "done".equals(task.status) || task.inbox || when <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(context, task.id, task.title));
    }
''',
    '''    public static void scheduleAt(Context context, Store.Task task, long when) {
        if (task == null || "done".equals(task.status) || task.inbox || when <= System.currentTimeMillis()) return;
        schedulePending(context, task, when);
    }

    private static void schedulePending(Context context, Store.Task task, long when) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(context, task.id, task.title));
    }
''',
    'shared reminder scheduling primitive',
)

receiver = 'android/app/src/main/java/com/ritmo/mobile/ReminderReceiver.java'
replace(
    receiver,
    '''            Store.Task task = store.findTask(taskId);
            if (task != null && !"done".equals(task.status)) store.setTaskStatus(task, "done");
            cancelNotification(context, notificationId);
            return;
''',
    '''            Store.Task task = store.findTask(taskId);
            if (task != null && !"done".equals(task.status)) {
                store.setTaskStatus(task, "done");
                ReminderScheduler.schedule(context, task);
            }
            cancelNotification(context, notificationId);
            return;
''',
    'schedule next recurrence after notification completion',
)

# Changelog: keep exactly one 3.4.1 entry after the accidental second trigger.
changelog = 'CHANGELOG.md'
text = read(changelog)
heading = '## 3.4.1 — Stability & Performance Polish'
first = text.find(heading)
second = text.find(heading, first + len(heading)) if first >= 0 else -1
if second >= 0:
    next_heading = text.find('## 3.4.0', second)
    if next_heading < 0:
        raise SystemExit('3.4.0 changelog heading missing')
    text = text[:second] + text[next_heading:]
    write(changelog, text)

print('Ritmo 3.4.1 final polish complete')
