import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/models.dart';
import '../services/planner_service.dart';
import 'native_bridge.dart';

enum RitmoThemeMode { system, light, dark }

class AppState extends ChangeNotifier {
  final SharedPreferencesAsync _prefs = SharedPreferencesAsync();
  Future<void> _saveChain = Future<void>.value();

  RitmoData data = RitmoData();
  bool loading = true;
  String? errorMessage;

  RitmoThemeMode themeMode = RitmoThemeMode.dark;
  String userName = '';
  bool haptics = true;
  bool reduceMotion = false;
  bool autoReplanOverdue = false;
  int plannerStartHour = 8;
  int plannerEndHour = 22;
  int plannerCapacityMinutes = 360;
  bool plannerIncludeWeekend = true;

  PlannerResult? lastPlannerPreview;
  List<Map<String, dynamic>> _plannerRollback = [];

  bool focusActive = false;
  bool focusRunning = false;
  int focusTaskId = 0;
  String focusTitle = 'Foco livre';
  String focusMode = 'Pomodoro 25';
  int focusPlannedMinutes = 25;
  int focusStartedAt = 0;
  int focusEndAt = 0;
  int focusRemainingSeconds = 0;

  String get today => isoDate(DateTime.now());

  ThemeMode get materialThemeMode => switch (themeMode) {
        RitmoThemeMode.system => ThemeMode.system,
        RitmoThemeMode.light => ThemeMode.light,
        RitmoThemeMode.dark => ThemeMode.dark,
      };

  Future<void> initialize() async {
    loading = true;
    errorMessage = null;
    notifyListeners();
    try {
      await _loadSettings();
      await _loadData();
      await _loadFocus();
      await _recoverExpiredFocusIfNeeded();
      _normalizeRecurringTasks();
      if (autoReplanOverdue) {
        final last = await _prefs.getString('last_auto_replan_date');
        if (last != today) {
          await replanOverdueFlexible(notify: false);
          await _prefs.setString('last_auto_replan_date', today);
        }
      }
      await save(syncReminders: false);
      await NativeBridge.syncReminders();
    } catch (e, st) {
      errorMessage = '$e\n$st';
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  Future<void> refreshFromNative() async {
    if (loading) return;
    try {
      // Do not replace current memory with an older native snapshot while the
      // previous mutation is still being persisted.
      await _saveChain;
      await _loadData();
      await _loadFocus();
      final recoveredFocus = await _recoverExpiredFocusIfNeeded();
      final recurringChanged = _normalizeRecurringTasks();
      if (recoveredFocus || recurringChanged) {
        await save();
      }
      notifyListeners();
    } catch (_) {
      // Keep current in-memory state if the native refresh fails.
    }
  }

  Future<void> _loadData() async {
    String? raw = await NativeBridge.loadData();
    raw ??= await _prefs.getString('ritmo_data_flutter');
    if (raw == null || raw.trim().isEmpty) {
      data = RitmoData();
      return;
    }
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map) throw const FormatException('Formato de dados inválido');
      data = RitmoData.fromJson(Map<String, dynamic>.from(decoded));
      _sanitize();
    } catch (e) {
      await _prefs.setString('ritmo_data_corrupt_backup_flutter', raw);
      throw FormatException(
        'Não foi possível ler os dados locais. Um backup foi preservado para recuperação.',
        e,
      );
    }
  }

  Future<void> _loadSettings() async {
    final legacy = await NativeBridge.loadLegacySettings();

    final savedTheme = await _prefs.getString('theme_mode') ??
        legacy['theme_mode']?.toString() ??
        'dark';
    themeMode = switch (savedTheme) {
      'system' => RitmoThemeMode.system,
      'light' => RitmoThemeMode.light,
      _ => RitmoThemeMode.dark,
    };

    userName = await _prefs.getString('user_name') ??
        legacy['user_name']?.toString() ??
        '';
    haptics = await _prefs.getBool('haptics') ??
        (legacy['haptics'] as bool?) ??
        true;
    reduceMotion = await _prefs.getBool('reduce_motion') ??
        (legacy['reduce_motion'] as bool?) ??
        false;
    autoReplanOverdue = await _prefs.getBool('auto_replan') ??
        (legacy['autoReplanOverdue'] as bool?) ??
        false;
    plannerStartHour = await _prefs.getInt('planner_start_hour') ??
        (legacy['plannerStartHour'] as num?)?.toInt() ??
        8;
    plannerEndHour = await _prefs.getInt('planner_end_hour') ??
        (legacy['plannerEndHour'] as num?)?.toInt() ??
        22;
    plannerCapacityMinutes = await _prefs.getInt('planner_capacity') ??
        (legacy['plannerCapacityMinutes'] as num?)?.toInt() ??
        360;
    plannerIncludeWeekend = await _prefs.getBool('planner_weekend') ??
        (legacy['plannerIncludeWeekend'] as bool?) ??
        true;

    plannerStartHour = plannerStartHour.clamp(0, 23).toInt();
    plannerEndHour =
        plannerEndHour.clamp(plannerStartHour + 1, 24).toInt();
    plannerCapacityMinutes =
        plannerCapacityMinutes.clamp(60, 16 * 60).toInt();
  }

  Future<void> _loadFocus() async {
    final native = await NativeBridge.loadFocusState();
    Map<String, dynamic> source = native;
    if (source.isEmpty) {
      final active = await _prefs.getBool('focus_active_flutter') ?? false;
      if (!active) {
        focusActive = false;
        focusRunning = false;
        focusRemainingSeconds = 0;
        focusEndAt = 0;
        return;
      }
      source = <String, dynamic>{
        'active': active,
        'running': await _prefs.getBool('focus_running_flutter') ?? false,
        'taskId': await _prefs.getInt('focus_task_id_flutter') ?? 0,
        'title': await _prefs.getString('focus_title_flutter') ?? 'Foco livre',
        'mode': await _prefs.getString('focus_mode_flutter') ?? 'Pomodoro 25',
        'plannedMinutes': await _prefs.getInt('focus_planned_minutes_flutter') ?? 25,
        'startedAt': await _prefs.getInt('focus_started_at_flutter') ?? 0,
        'endAt': await _prefs.getInt('focus_end_at_flutter') ?? 0,
        'remainingSeconds': await _prefs.getInt('focus_remaining_seconds_flutter') ?? 0,
      };
    }

    focusActive = source['active'] == true;
    focusRunning = source['running'] == true;
    focusTaskId = (source['taskId'] as num?)?.toInt() ?? 0;
    focusTitle = source['title']?.toString() ?? 'Foco livre';
    focusMode = source['mode']?.toString() ?? 'Pomodoro 25';
    focusPlannedMinutes =
        ((source['plannedMinutes'] as num?)?.toInt() ?? 25).clamp(1, 1440).toInt();
    focusStartedAt = (source['startedAt'] as num?)?.toInt() ?? 0;
    focusEndAt = (source['endAt'] as num?)?.toInt() ?? 0;
    focusRemainingSeconds = max(
      0,
      (source['remainingSeconds'] as num?)?.toInt() ?? 0,
    );

    if (focusActive && focusRunning && focusEndAt > 0) {
      focusRemainingSeconds = max(
        0,
        ((focusEndAt - DateTime.now().millisecondsSinceEpoch) / 1000).ceil(),
      );
    }
  }

  Future<void> _persistFocusFallback() async {
    await _prefs.setBool('focus_active_flutter', focusActive);
    await _prefs.setBool('focus_running_flutter', focusRunning);
    await _prefs.setInt('focus_task_id_flutter', focusTaskId);
    await _prefs.setString('focus_title_flutter', focusTitle);
    await _prefs.setString('focus_mode_flutter', focusMode);
    await _prefs.setInt('focus_planned_minutes_flutter', focusPlannedMinutes);
    await _prefs.setInt('focus_started_at_flutter', focusStartedAt);
    await _prefs.setInt('focus_end_at_flutter', focusEndAt);
    await _prefs.setInt('focus_remaining_seconds_flutter', focusRemainingSeconds);
  }

  Future<void> _clearFocusFallback() async {
    for (final key in const [
      'focus_active_flutter',
      'focus_running_flutter',
      'focus_task_id_flutter',
      'focus_title_flutter',
      'focus_mode_flutter',
      'focus_planned_minutes_flutter',
      'focus_started_at_flutter',
      'focus_end_at_flutter',
      'focus_remaining_seconds_flutter',
    ]) {
      await _prefs.remove(key);
    }
  }

  Future<bool> _recoverExpiredFocusIfNeeded() async {
    if (!focusActive || !focusRunning || focusEndAt <= 0) return false;

    final now = DateTime.now().millisecondsSinceEpoch;
    if (focusEndAt > now) {
      focusRemainingSeconds = max(0, ((focusEndAt - now) / 1000).ceil());
      return false;
    }
    focusRemainingSeconds = 0;

    final exists = data.focusSessions.any(
      (session) =>
          session.startedAt == focusStartedAt &&
          session.taskId == focusTaskId &&
          session.title == focusTitle,
    );
    if (!exists && focusPlannedMinutes > 0) {
      data.focusSessions.add(FocusSession(
        id: DateTime.now().microsecondsSinceEpoch,
        taskId: focusTaskId,
        title: focusTitle,
        date: today,
        mode: focusMode,
        plannedMinutes: focusPlannedMinutes,
        actualMinutes: focusPlannedMinutes,
        startedAt: focusStartedAt,
      ));
    }

    focusActive = false;
    focusRunning = false;
    focusRemainingSeconds = 0;
    focusEndAt = 0;
    await NativeBridge.stopFocus();
    await _clearFocusFallback();
    return true;
  }

  void _sanitize() {
    for (final task in data.tasks) {
      if (task.title.trim().isEmpty) task.title = 'Tarefa';
      if (!isValidIsoDate(task.date)) task.date = today;
      if (!isValidIsoDate(task.deadline) || task.deadline.compareTo(task.date) < 0) {
        task.deadline = task.date;
      }
      task.minutes = task.minutes.clamp(0, 1440).toInt();
      if (!const {'todo', 'doing', 'done'}.contains(task.status)) {
        task.status = 'todo';
      }
      if (!const {'auto', 'high', 'medium', 'low'}.contains(task.priority)) {
        task.priority = 'low';
      }
      if (!const {'none', 'daily', 'weekdays', 'weekly', 'monthly'}
          .contains(task.recurrence)) {
        task.recurrence = 'none';
      }
      if (!const {'low', 'medium', 'high'}.contains(task.energy)) {
        task.energy = 'medium';
      }
      if (!const {'any', 'morning', 'afternoon', 'evening'}
          .contains(task.preferredPeriod)) {
        task.preferredPeriod = 'any';
      }
      if (task.time.isNotEmpty &&
          !RegExp(r'^([01]\d|2[0-3]):[0-5]\d$').hasMatch(task.time)) {
        task.time = '';
      }
      if (task.inbox) {
        task.time = '';
        task.reminderMinutes = -1;
      }
    }
    for (final routine in data.routines) {
      if (routine.title.trim().isEmpty) routine.title = 'Hábito';
      if (!isValidIsoDate(routine.startDate)) routine.startDate = today;
      routine.minutes = routine.minutes.clamp(0, 1440).toInt();
      if (!const {'daily', 'weekdays', 'weekly', 'custom'}
          .contains(routine.frequency)) {
        routine.frequency = 'daily';
      }
      if (routine.time.isNotEmpty &&
          !RegExp(r'^([01]\d|2[0-3]):[0-5]\d$').hasMatch(routine.time)) {
        routine.time = '';
      }
    }
    final projectIds = data.projects.map((e) => e.id).toSet();
    for (final task in data.tasks) {
      if (task.projectId != 0 && !projectIds.contains(task.projectId)) {
        task.projectId = 0;
      }
      task.reminderMinutes = task.reminderMinutes.clamp(-1, 7 * 24 * 60).toInt();
    }
    for (final routine in data.routines) {
      routine.reminderMinutes = routine.reminderMinutes.clamp(-1, 7 * 24 * 60).toInt();
      routine.doneDates = routine.doneDates
          .where(isValidIsoDate)
          .where((date) => date.compareTo(routine.startDate) >= 0)
          .toSet()
          .toList()
        ..sort();
    }
    for (final project in data.projects) {
      if (project.title.trim().isEmpty) project.title = 'Projeto';
      if (project.targetDate.isNotEmpty && !isValidIsoDate(project.targetDate)) {
        project.targetDate = '';
      }
    }
    for (final goal in data.goals) {
      if (goal.title.trim().isEmpty) goal.title = 'Meta';
      goal.progress = goal.progress.clamp(0, 100).toInt();
      if (goal.targetDate.isNotEmpty && !isValidIsoDate(goal.targetDate)) {
        goal.targetDate = '';
      }
    }
    for (final completion in data.completions) {
      if (!isValidIsoDate(completion.date)) completion.date = today;
      completion.minutes = completion.minutes.clamp(0, 1440).toInt();
    }
    for (final session in data.focusSessions) {
      if (!isValidIsoDate(session.date)) {
        session.date = session.startedAt > 0
            ? isoDate(DateTime.fromMillisecondsSinceEpoch(session.startedAt))
            : today;
      }
      session.plannedMinutes = session.plannedMinutes.clamp(1, 1440).toInt();
      session.actualMinutes = session.actualMinutes.clamp(0, 1440).toInt();
    }
    for (final review in data.dayReviews) {
      if (!isValidIsoDate(review.date)) review.date = today;
      review.mood = review.mood.clamp(1, 5).toInt();
      review.doneCount = max(0, review.doneCount);
      review.pendingCount = max(0, review.pendingCount);
      review.focusMinutes = max(0, review.focusMinutes);
    }
  }

  Future<void> save({bool syncReminders = true}) {
    final raw = jsonEncode(data.toJson());
    final operation = _saveChain.then((_) async {
      await NativeBridge.saveData(raw);
      await _prefs.setString('ritmo_data_flutter', raw);
      if (syncReminders) await NativeBridge.syncReminders();
    });
    _saveChain = operation.catchError((Object _) {});
    return operation;
  }

  Future<void> saveSettings() async {
    await _prefs.setString('theme_mode', themeMode.name);
    await _prefs.setString('user_name', userName);
    await _prefs.setBool('haptics', haptics);
    await _prefs.setBool('reduce_motion', reduceMotion);
    await _prefs.setBool('auto_replan', autoReplanOverdue);
    await _prefs.setInt('planner_start_hour', plannerStartHour);
    await _prefs.setInt('planner_end_hour', plannerEndHour);
    await _prefs.setInt('planner_capacity', plannerCapacityMinutes);
    await _prefs.setBool('planner_weekend', plannerIncludeWeekend);
  }

  Future<void> setTheme(RitmoThemeMode value) async {
    themeMode = value;
    notifyListeners();
    await saveSettings();
  }

  Future<void> setUserName(String value) async {
    userName = value.trim();
    notifyListeners();
    await saveSettings();
  }

  Future<void> setReduceMotion(bool value) async {
    reduceMotion = value;
    notifyListeners();
    await saveSettings();
  }

  Future<void> setHaptics(bool value) async {
    haptics = value;
    notifyListeners();
    await saveSettings();
  }

  Future<void> setAutoReplan(bool value) async {
    autoReplanOverdue = value;
    notifyListeners();
    await saveSettings();
  }

  Future<void> setPlannerSettings({
    required int startHour,
    required int endHour,
    required int capacityMinutes,
    required bool includeWeekend,
  }) async {
    final safeStart = startHour.clamp(0, 23).toInt();
    plannerStartHour = safeStart;
    plannerEndHour = endHour.clamp(safeStart + 1, 24).toInt();
    plannerCapacityMinutes = capacityMinutes.clamp(60, 16 * 60).toInt();
    plannerIncludeWeekend = includeWeekend;
    notifyListeners();
    await saveSettings();
  }

  void feedback() {
    if (haptics) HapticFeedback.selectionClick();
  }

  List<TaskItem> tasksOn(String date) {
    final list = data.tasks.where((e) => !e.inbox && e.date == date).toList();
    list.sort((a, b) {
      if (a.status == 'done' && b.status != 'done') return 1;
      if (a.status != 'done' && b.status == 'done') return -1;
      if (a.time.isEmpty && b.time.isNotEmpty) return 1;
      if (a.time.isNotEmpty && b.time.isEmpty) return -1;
      return a.time.compareTo(b.time);
    });
    return list;
  }

  List<RoutineItem> routinesOn(String date) =>
      data.routines.where((e) => e.dueOn(date)).toList()
        ..sort((a, b) => a.time.compareTo(b.time));

  TaskItem? taskById(int id) {
    for (final task in data.tasks) {
      if (task.id == id) return task;
    }
    return null;
  }

  ProjectItem? projectById(int id) {
    for (final p in data.projects) {
      if (p.id == id) return p;
    }
    return null;
  }

  String projectTitle(int id) => projectById(id)?.title ?? 'Sem projeto';

  int taskCountOn(String date) =>
      data.tasks.where((e) => !e.inbox && e.date == date).length;
  int doneCountOn(String date) => data.tasks
      .where((e) => !e.inbox && e.date == date && e.status == 'done')
      .length;
  int completionPercentOn(String date) {
    final total = taskCountOn(date);
    return total == 0 ? 0 : (doneCountOn(date) * 100 / total).round();
  }

  int routinePercentOn(String date) {
    final due = routinesOn(date);
    if (due.isEmpty) return 0;
    return (due.where((e) => e.doneOn(date)).length * 100 / due.length).round();
  }

  int combinedDayScore(String date) {
    final tasks = taskCountOn(date);
    final routines = routinesOn(date).length;
    if (tasks == 0 && routines == 0) return 0;
    final t = tasks == 0 ? 100 : completionPercentOn(date);
    final r = routines == 0 ? 100 : routinePercentOn(date);
    if (tasks == 0) return r;
    if (routines == 0) return t;
    return (t * .65 + r * .35).round();
  }

  int focusMinutesOn(String date) => data.focusSessions
      .where((e) => e.date == date)
      .fold(0, (sum, e) => sum + e.actualMinutes);

  int plannedMinutesOn(String date) => data.tasks
      .where((e) => !e.inbox && e.date == date)
      .fold(0, (sum, e) => sum + e.minutes);

  int bestRoutineStreak() {
    var best = 0;
    for (final r in data.routines) {
      best = max(best, r.streak(today));
    }
    return best;
  }

  int overdueCount() => data.tasks
      .where((e) =>
          !e.inbox && e.status != 'done' && e.date.compareTo(today) < 0)
      .length;

  int totalCompletedLast7() {
    final start = addDaysIso(today, -6);
    return data.completions
        .where(
            (e) => e.date.compareTo(start) >= 0 && e.date.compareTo(today) <= 0)
        .length;
  }

  int focusMinutesLast7() {
    final start = addDaysIso(today, -6);
    return data.focusSessions
        .where(
            (e) => e.date.compareTo(start) >= 0 && e.date.compareTo(today) <= 0)
        .fold(0, (sum, e) => sum + e.actualMinutes);
  }

  List<int> last7Done() =>
      List.generate(7, (i) => doneCountOn(addDaysIso(today, i - 6)));
  List<int> last7Focus() =>
      List.generate(7, (i) => focusMinutesOn(addDaysIso(today, i - 6)));
  List<int> last30Scores() =>
      List.generate(30, (i) => combinedDayScore(addDaysIso(today, i - 29)));

  Map<String, int> categoryMinutesLast7() {
    final start = addDaysIso(today, -6);
    final result = <String, int>{};
    for (final c in data.completions) {
      if (c.date.compareTo(start) < 0 || c.date.compareTo(today) > 0) continue;
      result[c.category] = (result[c.category] ?? 0) + c.minutes;
    }
    return result;
  }

  Future<void> toggleTask(TaskItem task) async {
    feedback();
    if (task.status == 'done') {
      task.status = 'todo';
      data.completions
          .removeWhere((e) => e.taskId == task.id && e.date == task.date);
    } else {
      task.status = 'done';
      final exists = data.completions
          .any((e) => e.taskId == task.id && e.date == task.date);
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

  Future<void> setTaskStatus(TaskItem task, String status) async {
    if (!const {'todo', 'doing', 'done'}.contains(status)) return;
    final wasDone = task.status == 'done';
    task.status = status;
    if (!wasDone && status == 'done') {
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
    } else if (wasDone && status != 'done') {
      data.completions
          .removeWhere((e) => e.taskId == task.id && e.date == task.date);
    }
    notifyListeners();
    await save(syncReminders: false);
    await NativeBridge.syncTaskReminder(task.id);
  }

  Future<void> addOrUpdateTask(TaskItem task) async {
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

  Future<void> deleteTask(TaskItem task) async {
    data.tasks.removeWhere((e) => e.id == task.id);
    data.completions.removeWhere((e) => e.taskId == task.id);
    notifyListeners();
    await save(syncReminders: false);
    await NativeBridge.syncTaskReminder(task.id);
  }

  Future<void> toggleRoutine(RoutineItem routine, String date) async {
    feedback();
    if (routine.doneDates.contains(date)) {
      routine.doneDates.remove(date);
    } else {
      routine.doneDates.add(date);
    }
    notifyListeners();
    await save(syncReminders: false);
    await NativeBridge.syncRoutineReminder(routine.id);
  }

  Future<void> addOrUpdateRoutine(RoutineItem routine) async {
    final index = data.routines.indexWhere((e) => e.id == routine.id);
    if (index >= 0) {
      data.routines[index] = routine;
    } else {
      data.routines.add(routine);
    }
    notifyListeners();
    await save(syncReminders: false);
    await NativeBridge.syncRoutineReminder(routine.id);
  }

  Future<void> deleteRoutine(RoutineItem routine) async {
    data.routines.removeWhere((e) => e.id == routine.id);
    notifyListeners();
    await save(syncReminders: false);
    await NativeBridge.syncRoutineReminder(routine.id);
  }

  Future<void> addOrUpdateGoal(GoalItem goal) async {
    final index = data.goals.indexWhere((e) => e.id == goal.id);
    if (index >= 0) {
      data.goals[index] = goal;
    } else {
      data.goals.add(goal);
    }
    notifyListeners();
    await save(syncReminders: false);
  }

  Future<void> deleteGoal(GoalItem goal) async {
    data.goals.removeWhere((e) => e.id == goal.id);
    notifyListeners();
    await save(syncReminders: false);
  }

  Future<void> addOrUpdateProject(ProjectItem project) async {
    final index = data.projects.indexWhere((e) => e.id == project.id);
    if (index >= 0) {
      data.projects[index] = project;
    } else {
      data.projects.add(project);
    }
    notifyListeners();
    await save(syncReminders: false);
  }

  Future<void> deleteProject(ProjectItem project) async {
    data.projects.removeWhere((e) => e.id == project.id);
    for (final task in data.tasks) {
      if (task.projectId == project.id) task.projectId = 0;
    }
    notifyListeners();
    await save(syncReminders: false);
  }

  int projectProgress(ProjectItem p) {
    final tasks = data.tasks.where((e) => e.projectId == p.id).toList();
    if (tasks.isEmpty) return 0;
    return (tasks.where((e) => e.status == 'done').length * 100 / tasks.length)
        .round();
  }

  PlannerResult previewPlanner() {
    lastPlannerPreview = PlannerService.plan(
      data,
      PlannerSettings(
        startHour: plannerStartHour,
        endHour: plannerEndHour,
        capacityMinutes: plannerCapacityMinutes,
        includeWeekend: plannerIncludeWeekend,
      ),
    );
    notifyListeners();
    return lastPlannerPreview!;
  }

  Future<void> applyPlanner(PlannerResult result) async {
    _plannerRollback = result.assignments
        .map((a) => {'id': a.taskId, 'date': a.oldDate, 'time': a.oldTime})
        .toList();
    for (final a in result.assignments) {
      final task = taskById(a.taskId);
      if (task == null) continue;
      task.date = a.newDate;
      task.time = a.newTime;
    }
    await _prefs.setString('planner_rollback', jsonEncode(_plannerRollback));
    lastPlannerPreview = null;
    notifyListeners();
    await save();
  }

  Future<int> undoPlanner() async {
    if (_plannerRollback.isEmpty) {
      final raw = await _prefs.getString('planner_rollback');
      if (raw != null) {
        final parsed = jsonDecode(raw);
        if (parsed is List) {
          _plannerRollback = parsed
              .whereType<Map>()
              .map((e) => Map<String, dynamic>.from(e))
              .toList();
        }
      }
    }
    var restored = 0;
    for (final item in _plannerRollback) {
      final task = taskById((item['id'] as num?)?.toInt() ?? -1);
      if (task == null) continue;
      task.date = item['date']?.toString() ?? task.date;
      task.time = item['time']?.toString() ?? task.time;
      restored++;
    }
    _plannerRollback = [];
    await _prefs.remove('planner_rollback');
    notifyListeners();
    if (restored > 0) await save();
    return restored;
  }

  Future<int> replanOverdueFlexible({bool notify = true}) async {
    var moved = 0;
    for (final task in data.tasks) {
      if (task.inbox ||
          task.status == 'done' ||
          !task.flexible ||
          task.recurrence != 'none') {
        continue;
      }
      if (task.date.compareTo(today) >= 0) continue;
      task.date = today;
      if (task.deadline.compareTo(today) < 0) task.deadline = today;
      moved++;
    }
    if (moved > 0) {
      if (notify) notifyListeners();
      await save();
    }
    return moved;
  }

  bool _normalizeRecurringTasks() {
    var changed = false;
    for (final task in data.tasks) {
      if (task.recurrence == 'none' || task.status != 'done') continue;
      if (task.date.compareTo(today) >= 0) continue;
      var next = task.date;
      var guard = 0;
      while (next.compareTo(today) < 0 && guard++ < 10000) {
        next = _nextOccurrence(next, task.recurrence);
      }
      task.date = next;
      task.deadline = next;
      task.status = 'todo';
      changed = true;
    }
    return changed;
  }

  String _nextOccurrence(String iso, String recurrence) {
    var d = parseIso(iso);
    if (recurrence == 'weekly') {
      return isoDate(d.add(const Duration(days: 7)));
    }
    if (recurrence == 'monthly') {
      final month = d.month == 12 ? 1 : d.month + 1;
      final year = d.month == 12 ? d.year + 1 : d.year;
      final maxDay = DateTime(year, month + 1, 0).day;
      return isoDate(DateTime(year, month, min(d.day, maxDay)));
    }
    d = d.add(const Duration(days: 1));
    if (recurrence == 'weekdays') {
      while (d.weekday == DateTime.saturday ||
          d.weekday == DateTime.sunday) {
        d = d.add(const Duration(days: 1));
      }
    }
    return isoDate(d);
  }

  Future<void> startFocus({
    TaskItem? task,
    required int plannedMinutes,
    required String mode,
  }) async {
    feedback();
    final safeMinutes = plannedMinutes.clamp(1, 1440).toInt();
    focusTaskId = task?.id ?? 0;
    focusTitle = task?.title.trim().isNotEmpty == true ? task!.title.trim() : 'Foco livre';
    focusMode = mode.trim().isEmpty ? 'Foco' : mode.trim();
    focusPlannedMinutes = safeMinutes;
    focusStartedAt = DateTime.now().millisecondsSinceEpoch;
    focusRemainingSeconds = safeMinutes * 60;
    focusEndAt = focusStartedAt + focusRemainingSeconds * 1000;
    focusActive = true;
    focusRunning = true;
    notifyListeners();
    await NativeBridge.startFocus(
      taskId: focusTaskId,
      title: focusTitle,
      mode: mode,
      plannedMinutes: safeMinutes,
      startedAt: focusStartedAt,
      endAt: focusEndAt,
    );
    await _persistFocusFallback();
  }

  Future<void> pauseFocus(int remainingSeconds) async {
    focusRemainingSeconds = remainingSeconds.clamp(0, focusPlannedMinutes * 60).toInt();
    focusRunning = false;
    focusActive = true;
    notifyListeners();
    await NativeBridge.pauseFocus(
      taskId: focusTaskId,
      title: focusTitle,
      mode: focusMode,
      plannedMinutes: focusPlannedMinutes,
      startedAt: focusStartedAt,
      remainingSeconds: focusRemainingSeconds,
    );
    await _persistFocusFallback();
  }

  Future<void> resumeFocus() async {
    if (!focusActive || focusRemainingSeconds <= 0) return;
    focusEndAt =
        DateTime.now().millisecondsSinceEpoch + focusRemainingSeconds * 1000;
    focusRunning = true;
    notifyListeners();
    await NativeBridge.startFocus(
      taskId: focusTaskId,
      title: focusTitle,
      mode: focusMode,
      plannedMinutes: focusPlannedMinutes,
      startedAt: focusStartedAt,
      endAt: focusEndAt,
    );
    await _persistFocusFallback();
  }

  int currentFocusRemainingSeconds() {
    if (!focusActive) return 0;
    if (!focusRunning) return focusRemainingSeconds;
    return max(
      0,
      ((focusEndAt - DateTime.now().millisecondsSinceEpoch) / 1000).ceil(),
    );
  }

  Future<void> finishFocus({bool completeTask = false}) async {
    if (!focusActive) return;
    final remaining = currentFocusRemainingSeconds();
    final executedSeconds = max(0, focusPlannedMinutes * 60 - remaining);
    final actual = min(
      focusPlannedMinutes,
      (executedSeconds / 60).round(),
    ).clamp(0, focusPlannedMinutes).toInt();

    // Do not create a fake one-minute session when focus is stopped instantly.
    if (actual > 0) {
      data.focusSessions.add(FocusSession(
        id: DateTime.now().microsecondsSinceEpoch,
        taskId: focusTaskId,
        title: focusTitle,
        date: today,
        mode: focusMode,
        plannedMinutes: focusPlannedMinutes,
        actualMinutes: actual,
        startedAt: focusStartedAt,
      ));
    }

    if (completeTask && focusTaskId != 0) {
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
    focusActive = false;
    focusRunning = false;
    focusRemainingSeconds = 0;
    focusEndAt = 0;
    notifyListeners();
    await NativeBridge.stopFocus();
    await _clearFocusFallback();
    await save(syncReminders: false);
    if (completeTask && focusTaskId != 0) {
      await NativeBridge.syncTaskReminder(focusTaskId);
    }
  }

  Future<void> cancelFocus() async {
    focusActive = false;
    focusRunning = false;
    focusRemainingSeconds = 0;
    focusEndAt = 0;
    notifyListeners();
    await NativeBridge.stopFocus();
    await _clearFocusFallback();
  }

  Future<void> saveDayReview({
    required int mood,
    required String note,
    bool moveFlexibleToTomorrow = false,
  }) async {
    data.dayReviews.removeWhere((e) => e.date == today);
    data.dayReviews.add(DayReview(
      date: today,
      mood: mood,
      note: note.trim(),
      doneCount: doneCountOn(today),
      pendingCount: tasksOn(today).where((e) => e.status != 'done').length,
      focusMinutes: focusMinutesOn(today),
      createdAt: DateTime.now().millisecondsSinceEpoch,
    ));

    if (moveFlexibleToTomorrow) {
      final tomorrow = addDaysIso(today, 1);
      for (final task in data.tasks) {
        if (!task.inbox &&
            task.date == today &&
            task.status != 'done' &&
            task.flexible &&
            task.recurrence == 'none') {
          task.date = tomorrow;
          if (task.deadline.compareTo(tomorrow) < 0) {
            task.deadline = tomorrow;
          }
        }
      }
    }
    notifyListeners();
    await save();
  }
}
