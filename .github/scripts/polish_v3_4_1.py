from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8", newline="\n")


def replace_once(path: str, old: str, new: str, label: str) -> None:
    text = read(path)
    if new in text:
        print(f"already applied: {label}")
        return
    if old not in text:
        raise SystemExit(f"expected block not found: {label} in {path}")
    write(path, text.replace(old, new, 1))
    print(f"updated: {label}")


# -----------------------------------------------------------------------------
# Models: routines must never become due before their configured start date.
# -----------------------------------------------------------------------------
replace_once(
    "lib/models/models.dart",
    "  bool dueOn(String date) {\n    final d = parseIso(date);",
    "  bool dueOn(String date) {\n    if (date.compareTo(startDate) < 0) return false;\n    final d = parseIso(date);",
    "routine start date guard",
)

# -----------------------------------------------------------------------------
# App state: data safety, reminder efficiency, consistent Inbox semantics and
# recovery of focus sessions that finish while Flutter is not in foreground.
# -----------------------------------------------------------------------------
app = "lib/core/app_state.dart"
replace_once(
    app,
    "  final SharedPreferencesAsync _prefs = SharedPreferencesAsync();\n",
    "  final SharedPreferencesAsync _prefs = SharedPreferencesAsync();\n  Future<void> _saveChain = Future<void>.value();\n",
    "serialized persistence chain",
)
replace_once(
    app,
    "      await _loadFocus();\n      _normalizeRecurringTasks();",
    "      await _loadFocus();\n      await _recoverExpiredFocusIfNeeded();\n      _normalizeRecurringTasks();",
    "recover expired focus on startup",
)
replace_once(
    app,
    "          replanOverdueFlexible(notify: false);",
    "          await replanOverdueFlexible(notify: false);",
    "await automatic overdue replan",
)
replace_once(
    app,
    "      await _loadData();\n      await _loadFocus();\n      notifyListeners();",
    "      await _loadData();\n      await _loadFocus();\n      final recoveredFocus = await _recoverExpiredFocusIfNeeded();\n      final recurringChanged = _normalizeRecurringTasks();\n      if (recoveredFocus || recurringChanged) {\n        await save();\n      }\n      notifyListeners();",
    "refresh normalization and focus recovery",
)

# Replace corrupt-data behavior: never overwrite unreadable user data with demo
# content. Preserve it and surface the startup error instead.
replace_once(
    app,
    "    } catch (e) {\n      await _prefs.setString('ritmo_data_corrupt_backup_flutter', raw);\n      data = _seed();\n    }",
    "    } catch (e) {\n      await _prefs.setString('ritmo_data_corrupt_backup_flutter', raw);\n      throw FormatException(\n        'Não foi possível ler os dados locais. Um backup foi preservado para recuperação.',\n        e,\n      );\n    }",
    "preserve corrupt data instead of replacing it",
)

# Fresh installs should start clean, not with sample/demo tasks.
replace_once(
    app,
    "      data = _seed();\n      return;",
    "      data = RitmoData();\n      return;",
    "clean fresh install",
)

# Add focus completion recovery right after native focus loading.
needle = "  void _sanitize() {\n"
recovery = r'''  Future<bool> _recoverExpiredFocusIfNeeded() async {
    if (!focusActive || focusEndAt <= 0 || focusRemainingSeconds > 0) {
      return false;
    }
    if (focusEndAt > DateTime.now().millisecondsSinceEpoch) return false;

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
    return true;
  }

'''
text = read(app)
if recovery not in text:
    if needle not in text:
        raise SystemExit("sanitize marker not found")
    text = text.replace(needle, recovery + needle, 1)
    write(app, text)

# Harden loaded enum/time values and Inbox invariants.
replace_once(
    app,
    "      if (task.deadline.length != 10) task.deadline = task.date;\n      if (task.minutes < 0) task.minutes = 0;",
    "      if (task.deadline.length != 10) task.deadline = task.date;\n      task.minutes = task.minutes.clamp(0, 1440).toInt();\n      if (!const {'todo', 'doing', 'done'}.contains(task.status)) task.status = 'todo';\n      if (!const {'auto', 'high', 'medium', 'low'}.contains(task.priority)) task.priority = 'low';\n      if (!const {'none', 'daily', 'weekdays', 'weekly', 'monthly'}.contains(task.recurrence)) task.recurrence = 'none';\n      if (!const {'low', 'medium', 'high'}.contains(task.energy)) task.energy = 'medium';\n      if (!const {'any', 'morning', 'afternoon', 'evening'}.contains(task.preferredPeriod)) {\n        task.preferredPeriod = 'any';\n      }\n      if (task.time.isNotEmpty && !RegExp(r'^([01]\\d|2[0-3]):[0-5]\\d$').hasMatch(task.time)) {\n        task.time = '';\n      }\n      if (task.inbox) {\n        task.time = '';\n        task.reminderMinutes = -1;\n      }",
    "sanitize task values",
)
replace_once(
    app,
    "      if (routine.startDate.length != 10) routine.startDate = today;\n      if (routine.minutes < 0) routine.minutes = 0;",
    "      if (routine.startDate.length != 10) routine.startDate = today;\n      routine.minutes = routine.minutes.clamp(0, 1440).toInt();\n      if (!const {'daily', 'weekdays', 'weekly', 'custom'}.contains(routine.frequency)) {\n        routine.frequency = 'daily';\n      }\n      if (routine.time.isNotEmpty && !RegExp(r'^([01]\\d|2[0-3]):[0-5]\\d$').hasMatch(routine.time)) {\n        routine.time = '';\n      }",
    "sanitize routine values",
)

# Serialize writes so rapid taps cannot race SharedPreferences/native persistence.
old_save = r'''  Future<void> save({bool syncReminders = true}) async {
    final raw = jsonEncode(data.toJson());
    await NativeBridge.saveData(raw);
    await _prefs.setString('ritmo_data_flutter', raw);
    if (syncReminders) await NativeBridge.syncReminders();
  }
'''
new_save = r'''  Future<void> save({bool syncReminders = true}) {
    final raw = jsonEncode(data.toJson());
    final operation = _saveChain.then((_) async {
      await NativeBridge.saveData(raw);
      await _prefs.setString('ritmo_data_flutter', raw);
      if (syncReminders) await NativeBridge.syncReminders();
    });
    _saveChain = operation.catchError((Object _) {});
    return operation;
  }
'''
replace_once(app, old_save, new_save, "serialize app saves")

replace_once(
    app,
    "    final list = data.tasks.where((e) => e.date == date).toList();",
    "    final list = data.tasks.where((e) => !e.inbox && e.date == date).toList();",
    "exclude Inbox from day task list",
)
replace_once(
    app,
    "  int taskCountOn(String date) => data.tasks.where((e) => e.date == date).length;\n  int doneCountOn(String date) =>\n      data.tasks.where((e) => e.date == date && e.status == 'done').length;",
    "  int taskCountOn(String date) =>\n      data.tasks.where((e) => !e.inbox && e.date == date).length;\n  int doneCountOn(String date) => data.tasks\n      .where((e) => !e.inbox && e.date == date && e.status == 'done')\n      .length;",
    "exclude Inbox from day counters",
)
replace_once(
    app,
    "  int plannedMinutesOn(String date) =>\n      data.tasks.where((e) => e.date == date).fold(0, (sum, e) => sum + e.minutes);",
    "  int plannedMinutesOn(String date) => data.tasks\n      .where((e) => !e.inbox && e.date == date)\n      .fold(0, (sum, e) => sum + e.minutes);",
    "exclude Inbox from planned minutes",
)
replace_once(
    app,
    "  int overdueCount() => data.tasks\n      .where((e) => e.status != 'done' && e.date.compareTo(today) < 0)\n      .length;",
    "  int overdueCount() => data.tasks\n      .where((e) => !e.inbox && e.status != 'done' && e.date.compareTo(today) < 0)\n      .length;",
    "exclude Inbox from overdue count",
)

# Completion history must not duplicate when status is set repeatedly.
replace_once(
    app,
    "    if (!wasDone && status == 'done') {\n      data.completions.add(CompletionItem(\n        taskId: task.id,\n        title: task.title,\n        date: task.date,\n        category: task.category,\n        minutes: task.minutes,\n      ));",
    "    if (!wasDone && status == 'done') {\n      final exists = data.completions.any(\n        (e) => e.taskId == task.id && e.date == task.date,\n      );\n      if (!exists) {\n        data.completions.add(CompletionItem(\n          taskId: task.id,\n          title: task.title,\n          date: task.date,\n          category: task.category,\n          minutes: task.minutes,\n        ));\n      }",
    "deduplicate status completion history",
)

# Use targeted reminder synchronization for routine task edits instead of
# rescheduling every alarm in the app on every small mutation.
replacements = [
    ("    await save();\n  }\n\n  Future<void> setTaskStatus", "    await save(syncReminders: false);\n    await NativeBridge.syncTaskReminder(task.id);\n  }\n\n  Future<void> setTaskStatus", "targeted toggle task reminder"),
    ("    notifyListeners();\n    await save();\n  }\n\n  Future<void> addOrUpdateTask", "    notifyListeners();\n    await save(syncReminders: false);\n    await NativeBridge.syncTaskReminder(task.id);\n  }\n\n  Future<void> addOrUpdateTask", "targeted status reminder"),
    ("    notifyListeners();\n    await save();\n  }\n\n  Future<void> deleteTask", "    notifyListeners();\n    await save(syncReminders: false);\n    await NativeBridge.syncTaskReminder(task.id);\n  }\n\n  Future<void> deleteTask", "targeted task edit reminder"),
]
for old, new, label in replacements:
    replace_once(app, old, new, label)

replace_once(
    app,
    "    data.tasks.removeWhere((e) => e.id == task.id);\n    data.completions.removeWhere((e) => e.taskId == task.id);\n    notifyListeners();\n    await save();",
    "    data.tasks.removeWhere((e) => e.id == task.id);\n    data.completions.removeWhere((e) => e.taskId == task.id);\n    notifyListeners();\n    await save(syncReminders: false);\n    await NativeBridge.syncTaskReminder(task.id);",
    "targeted deleted task reminder",
)
replace_once(
    app,
    "    notifyListeners();\n    await save();\n  }\n\n  Future<void> addOrUpdateRoutine",
    "    notifyListeners();\n    await save(syncReminders: false);\n    await NativeBridge.syncRoutineReminder(routine.id);\n  }\n\n  Future<void> addOrUpdateRoutine",
    "targeted routine toggle reminder",
)
replace_once(
    app,
    "    notifyListeners();\n    await save();\n  }\n\n  Future<void> deleteRoutine",
    "    notifyListeners();\n    await save(syncReminders: false);\n    await NativeBridge.syncRoutineReminder(routine.id);\n  }\n\n  Future<void> deleteRoutine",
    "targeted routine edit reminder",
)
replace_once(
    app,
    "    data.routines.removeWhere((e) => e.id == routine.id);\n    notifyListeners();\n    await save();",
    "    data.routines.removeWhere((e) => e.id == routine.id);\n    notifyListeners();\n    await save(syncReminders: false);\n    await NativeBridge.syncRoutineReminder(routine.id);",
    "targeted deleted routine reminder",
)

# Goals/projects cannot affect alarms; avoid unnecessary full alarm scans.
text = read(app)
for marker in [
    "Future<void> addOrUpdateGoal",
    "Future<void> deleteGoal",
    "Future<void> addOrUpdateProject",
    "Future<void> deleteProject",
]:
    start = text.find(marker)
    if start < 0:
        raise SystemExit(f"missing {marker}")
    next_method = text.find("\n  Future<", start + len(marker))
    if next_method < 0:
        next_method = text.find("\n  int ", start + len(marker))
    section = text[start:next_method]
    section2 = section.replace("await save();", "await save(syncReminders: false);")
    text = text[:start] + section2 + text[next_method:]
write(app, text)

# Overdue replanning is async and excludes Inbox items.
replace_once(
    app,
    "  int replanOverdueFlexible({bool notify = true}) {",
    "  Future<int> replanOverdueFlexible({bool notify = true}) async {",
    "async overdue replan",
)
replace_once(
    app,
    "      if (task.status == 'done' || !task.flexible || task.recurrence != 'none') continue;",
    "      if (task.inbox || task.status == 'done' || !task.flexible || task.recurrence != 'none') continue;",
    "exclude Inbox from overdue replan",
)
replace_once(
    app,
    "      save();\n    }\n    return moved;\n  }\n\n  void _normalizeRecurringTasks()",
    "      await save();\n    }\n    return moved;\n  }\n\n  bool _normalizeRecurringTasks()",
    "await replan save and normalize return type",
)
replace_once(
    app,
    "    if (changed) save(syncReminders: false);\n  }\n\n  String _nextOccurrence",
    "    return changed;\n  }\n\n  String _nextOccurrence",
    "remove unawaited recurring save",
)

# Focus completion without task completion does not change alarms.
replace_once(
    app,
    "    await NativeBridge.stopFocus();\n    await save();\n  }\n\n  Future<void> cancelFocus",
    "    await NativeBridge.stopFocus();\n    await save(syncReminders: !completeTask ? false : focusTaskId != 0);\n  }\n\n  Future<void> cancelFocus",
    "avoid unnecessary reminder sync after free focus",
)

# -----------------------------------------------------------------------------
# Native bridge: targeted task/routine alarm refreshes.
# -----------------------------------------------------------------------------
bridge = "lib/core/native_bridge.dart"
insert_marker = "  static Future<Map<String, dynamic>> loadFocusState() async {\n"
bridge_methods = r'''  static Future<void> syncTaskReminder(int taskId) async {
    try {
      await _channel.invokeMethod<void>('syncTaskReminder', {'taskId': taskId});
    } on PlatformException {
      // A full sync still runs at startup and after planner operations.
    } on MissingPluginException {
      // No-op outside Android.
    }
  }

  static Future<void> syncRoutineReminder(int routineId) async {
    try {
      await _channel.invokeMethod<void>('syncRoutineReminder', {'routineId': routineId});
    } on PlatformException {
      // A full sync still runs at startup and after planner operations.
    } on MissingPluginException {
      // No-op outside Android.
    }
  }

'''
text = read(bridge)
if bridge_methods not in text:
    if insert_marker not in text:
        raise SystemExit("native bridge insertion marker missing")
    text = text.replace(insert_marker, bridge_methods + insert_marker, 1)
    write(bridge, text)

# -----------------------------------------------------------------------------
# Planner: avoid scanning all focus history once for every task and avoid sorting
# an already-sorted interval list on every candidate check.
# -----------------------------------------------------------------------------
planner = "lib/services/planner_service.dart"
replace_once(
    planner,
    "    final load = <String, int>{};\n    final occupied = <String, List<_Interval>>{};",
    "    final load = <String, int>{};\n    final occupied = <String, List<_Interval>>{};\n    final focusHistory = settings.useHistory\n        ? _recentFocusAverages(data.focusSessions)\n        : const <int, double>{};",
    "planner focus history index",
)
replace_once(
    planner,
    "      final estimate = _estimateMinutes(task, data, settings.useHistory);",
    "      final estimate = _estimateMinutes(task, focusHistory);",
    "planner indexed history use",
)
old_estimate = r'''  static int _estimateMinutes(TaskItem task, RitmoData data, bool useHistory) {
    final base = max(15, task.minutes);
    if (!useHistory) return base;

    final sessions = data.focusSessions
        .where((e) => e.taskId == task.id && e.actualMinutes > 0)
        .toList();
    if (sessions.isEmpty) return base;

    final recent = sessions.length <= 5 ? sessions : sessions.sublist(sessions.length - 5);
    final avg = recent.fold<int>(0, (sum, e) => sum + e.actualMinutes) / recent.length;
    final blended = (base * .55 + avg * .45).round();
    return blended.clamp(15, max(30, base * 2)).toInt();
  }
'''
new_estimate = r'''  static Map<int, double> _recentFocusAverages(List<FocusSession> sessions) {
    final recent = <int, List<int>>{};
    for (final session in sessions) {
      if (session.taskId == 0 || session.actualMinutes <= 0) continue;
      final values = recent.putIfAbsent(session.taskId, () => <int>[]);
      values.add(session.actualMinutes);
      if (values.length > 5) values.removeAt(0);
    }
    return {
      for (final entry in recent.entries)
        entry.key: entry.value.fold<int>(0, (sum, value) => sum + value) /
            entry.value.length,
    };
  }

  static int _estimateMinutes(TaskItem task, Map<int, double> history) {
    final base = max(15, task.minutes);
    final avg = history[task.id];
    if (avg == null) return base;
    final blended = (base * .55 + avg * .45).round();
    return blended.clamp(15, max(30, base * 2)).toInt();
  }
'''
replace_once(planner, old_estimate, new_estimate, "planner history O(n) optimization")
replace_once(
    planner,
    "    final sorted = [...list]..sort((a, b) => a.start.compareTo(b.start));\n    for (final interval in sorted) {",
    "    for (final interval in list) {",
    "avoid repeated interval sorting",
)

# -----------------------------------------------------------------------------
# Today/Central/Kanban polish.
# -----------------------------------------------------------------------------
replace_once(
    "lib/screens/today_page.dart",
    "                    title: 'Nenhuma rotina para hoje',",
    "                    title: 'Nenhuma tarefa para mostrar',",
    "correct empty-state wording",
)

center = "lib/screens/command_center_page.dart"
replace_once(center, "          autofocus: true,", "          autofocus: false,", "do not force keyboard in Central")
replace_once(
    center,
    "    final q = query.trim().toLowerCase();\n    final tasks = q.isEmpty",
    "    final q = query.trim().toLowerCase();\n    final projectTitles = {for (final p in state.data.projects) p.id: p.title};\n    final tasks = q.isEmpty",
    "search project lookup index",
)
replace_once(
    center,
    "            final project = state.projectTitle(e.projectId);",
    "            final project = projectTitles[e.projectId] ?? 'Sem projeto';",
    "search indexed project title",
)
old_header = r'''        SectionHeader(
          title: 'Caixa de entrada',
          actionLabel: inbox.isEmpty ? null : '${inbox.length} item${inbox.length == 1 ? '' : 's'}',
          onAction: inbox.isEmpty ? null : () {},
        ),'''
new_header = r'''        Padding(
          padding: const EdgeInsets.fromLTRB(2, 8, 2, 10),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  'Caixa de entrada',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
              ),
              if (inbox.isNotEmpty)
                Text(
                  '${inbox.length} item${inbox.length == 1 ? '' : 's'}',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
            ],
          ),
        ),'''
replace_once(center, old_header, new_header, "remove no-op Inbox action")
replace_once(
    center,
    "    final next = items.where((e) => !e.done && e.time.isNotEmpty).cast<_TimelineItem?>().firstOrNull;",
    "    final now = TimeOfDay.now();\n    final nowMinutes = now.hour * 60 + now.minute;\n    final next = items\n        .where((e) =>\n            !e.done &&\n            e.time.isNotEmpty &&\n            _timeMinutes(e.time) >= nowMinutes)\n        .cast<_TimelineItem?>()\n        .firstOrNull;",
    "timeline future next action",
)
text = read(center)
time_helper = r'''int _timeMinutes(String value) {
  final parts = value.split(':');
  if (parts.length != 2) return -1;
  final hour = int.tryParse(parts[0]);
  final minute = int.tryParse(parts[1]);
  if (hour == null || minute == null) return -1;
  return hour * 60 + minute;
}

'''
marker = "String _dateLabel(String iso) {\n"
if time_helper not in text:
    if marker not in text:
        raise SystemExit("timeline helper marker missing")
    text = text.replace(marker, time_helper + marker, 1)
    write(center, text)

organizer = "lib/screens/organizer_page.dart"
replace_once(
    organizer,
    "          final tasks = state.data.tasks.where((e) => e.status == column.$1).toList();",
    "          final tasks = state.data.tasks\n              .where((e) => !e.inbox && e.status == column.$1)\n              .toList();",
    "exclude Inbox from Kanban",
)
replace_once(
    organizer,
    "                duration: const Duration(milliseconds: 180),",
    "                duration: state.reduceMotion\n                    ? Duration.zero\n                    : const Duration(milliseconds: 180),",
    "respect reduced motion in Kanban",
)

# -----------------------------------------------------------------------------
# Focus UI: one rebuild per displayed second, no idle ticker while paused, and
# no meaningless complete-task checkbox for free focus.
# -----------------------------------------------------------------------------
focus = "lib/screens/focus_page.dart"
replace_once(
    focus,
    "    if (widget.state.focusActive) {\n      _minutes = widget.state.focusPlannedMinutes;\n      _mode = widget.state.focusMode;\n      _startTicker();\n    }",
    "    if (widget.state.focusActive) {\n      _minutes = widget.state.focusPlannedMinutes;\n      _mode = widget.state.focusMode;\n      if (widget.state.focusRunning) _startTicker();\n    }",
    "avoid paused focus ticker",
)
replace_once(
    focus,
    "    _timer = Timer.periodic(const Duration(milliseconds: 500), (_) {",
    "    _timer = Timer.periodic(const Duration(seconds: 1), (_) {",
    "focus ticker frequency",
)
replace_once(
    focus,
    "                  completeTask: _completeTask,\n                  onCompleteTaskChanged:",
    "                  completeTask: _completeTask,\n                  hasLinkedTask: state.focusTaskId != 0,\n                  reduceMotion: state.reduceMotion,\n                  onCompleteTaskChanged:",
    "focus active presentation flags",
)
replace_once(
    focus,
    "                    if (state.focusRunning) {\n                      await state.pauseFocus(remaining);\n                    } else {",
    "                    if (state.focusRunning) {\n                      await state.pauseFocus(remaining);\n                      _timer?.cancel();\n                    } else {",
    "stop ticker while focus paused",
)
replace_once(
    focus,
    "  final bool completeTask;\n  final ValueChanged<bool> onCompleteTaskChanged;",
    "  final bool completeTask;\n  final bool hasLinkedTask;\n  final bool reduceMotion;\n  final ValueChanged<bool> onCompleteTaskChanged;",
    "focus active properties",
)
replace_once(
    focus,
    "    required this.completeTask,\n    required this.onCompleteTaskChanged,",
    "    required this.completeTask,\n    required this.hasLinkedTask,\n    required this.reduceMotion,\n    required this.onCompleteTaskChanged,",
    "focus active constructor properties",
)
replace_once(
    focus,
    "            duration: const Duration(milliseconds: 450),",
    "            duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 240),",
    "lighter focus ring animation",
)
old_checkbox = r'''        const SizedBox(height: 8),
        CheckboxListTile(
          value: completeTask,
          onChanged: (v) => onCompleteTaskChanged(v ?? false),
          title: const Text('Concluir tarefa ao finalizar'),
          contentPadding: EdgeInsets.zero,
          controlAffinity: ListTileControlAffinity.leading,
        ),'''
new_checkbox = r'''        if (hasLinkedTask) ...[
          const SizedBox(height: 8),
          CheckboxListTile(
            value: completeTask,
            onChanged: (v) => onCompleteTaskChanged(v ?? false),
            title: const Text('Concluir tarefa ao finalizar'),
            contentPadding: EdgeInsets.zero,
            controlAffinity: ListTileControlAffinity.leading,
          ),
        ],'''
replace_once(focus, old_checkbox, new_checkbox, "hide free-focus task checkbox")
replace_once(
    focus,
    "      oldDelegate.value != value || oldDelegate.color != color;",
    "      oldDelegate.value != value ||\n      oldDelegate.color != color ||\n      oldDelegate.track != track;",
    "focus painter repaint correctness",
)

# -----------------------------------------------------------------------------
# Common widgets: accurate zero bars and fewer decorative rebuild animations.
# -----------------------------------------------------------------------------
common = "lib/widgets/common.dart"
replace_once(
    common,
    "      oldDelegate.color != color ||\n      oldDelegate.track != track;",
    "      oldDelegate.color != color ||\n      oldDelegate.track != track ||\n      oldDelegate.stroke != stroke;",
    "progress ring stroke repaint",
)
replace_once(
    common,
    "                        heightFactor: factor.clamp(.04, 1).toDouble(),",
    "                        heightFactor: values[index] == 0\n                            ? 0\n                            : factor.clamp(.04, 1).toDouble(),",
    "weekly chart zero accuracy",
)
replace_once(
    common,
    "          child: AnimatedContainer(\n            duration: const Duration(milliseconds: 300),",
    "          child: Container(",
    "remove unnecessary heatmap animation",
)

# -----------------------------------------------------------------------------
# Editors: numeric input validation, reminder requirements, custom habit guard,
# and correct subtask identity/completion state when items are removed.
# -----------------------------------------------------------------------------
editors = "lib/sheets/editors.dart"
replace_once(
    editors,
    "import 'package:flutter/material.dart';\nimport 'package:intl/intl.dart';",
    "import 'package:flutter/material.dart';\nimport 'package:flutter/services.dart';\nimport 'package:intl/intl.dart';",
    "editor input formatter import",
)
subtask_class = r'''class _SubtaskDraft {
  final int id;
  final TextEditingController controller;
  bool done;

  _SubtaskDraft({required this.id, required String title, this.done = false})
      : controller = TextEditingController(text: title);

  void dispose() => controller.dispose();
}

'''
text = read(editors)
marker = "class TaskEditorSheet extends StatefulWidget {\n"
if subtask_class not in text:
    if marker not in text:
        raise SystemExit("subtask draft marker missing")
    text = text.replace(marker, subtask_class + marker, 1)
    write(editors, text)
replace_once(
    editors,
    "  final List<TextEditingController> _subtasks = [];",
    "  final List<_SubtaskDraft> _subtasks = [];",
    "subtask draft storage",
)
replace_once(
    editors,
    "      _subtasks.add(TextEditingController(text: s.title));",
    "      _subtasks.add(_SubtaskDraft(id: s.id, title: s.title, done: s.done));",
    "preserve subtask identities",
)
replace_once(
    editors,
    "    for (final c in _subtasks) {\n      c.dispose();\n    }",
    "    for (final subtask in _subtasks) {\n      subtask.dispose();\n    }",
    "dispose subtask drafts",
)
old_task_save_start = r'''    final title = _title.text.trim();
    if (title.isEmpty) return;
    final original = widget.original;
    final result = TaskItem('''
new_task_save_start = r'''    final title = _title.text.trim();
    if (title.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Informe um título para a tarefa.')),
      );
      return;
    }
    if (!_inbox && widget.commitment && _time.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Defina um horário para o compromisso.')),
      );
      return;
    }
    if (!_inbox && _reminder >= 0 && _time.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Defina um horário para usar um lembrete.')),
      );
      return;
    }
    final original = widget.original;
    final duration = (int.tryParse(_minutes.text.trim()) ?? 30).clamp(0, 1440).toInt();
    final result = TaskItem('''
replace_once(editors, old_task_save_start, new_task_save_start, "task editor validation")
replace_once(
    editors,
    "      minutes: int.tryParse(_minutes.text.trim()) ?? 30,",
    "      minutes: duration,",
    "clamp task duration",
)
old_subtask_save = r'''      subtasks: List.generate(_subtasks.length, (index) {
        final old = original != null && index < original.subtasks.length
            ? original.subtasks[index]
            : null;
        return Subtask(
          id: old?.id ?? DateTime.now().microsecondsSinceEpoch + index,
          title: _subtasks[index].text.trim().isEmpty
              ? 'Subtarefa ${index + 1}'
              : _subtasks[index].text.trim(),
          done: old?.done ?? false,
        );
      }),'''
new_subtask_save = r'''      subtasks: List.generate(_subtasks.length, (index) {
        final draft = _subtasks[index];
        return Subtask(
          id: draft.id,
          title: draft.controller.text.trim().isEmpty
              ? 'Subtarefa ${index + 1}'
              : draft.controller.text.trim(),
          done: draft.done,
        );
      }),'''
replace_once(editors, old_subtask_save, new_subtask_save, "correct subtask save identity")
# Numeric task input formatter.
replace_once(
    editors,
    "                    controller: _minutes,\n                    keyboardType: TextInputType.number,\n                    decoration: const InputDecoration(\n                      labelText: 'Duração (min)',",
    "                    controller: _minutes,\n                    keyboardType: TextInputType.number,\n                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],\n                    decoration: const InputDecoration(\n                      labelText: 'Duração (min)',",
    "task duration digits only",
)
replace_once(
    editors,
    "                  onPressed: () => setState(\n                    () => _subtasks.add(TextEditingController()),\n                  ),",
    "                  onPressed: () => setState(\n                    () => _subtasks.add(_SubtaskDraft(\n                      id: DateTime.now().microsecondsSinceEpoch,\n                      title: '',\n                    )),\n                  ),",
    "new subtask draft",
)
old_subtask_field = r'''                child: TextField(
                  controller: _subtasks[index],
                  decoration: InputDecoration(
                    hintText: 'Subtarefa ${index + 1}',
                    prefixIcon: const Icon(Icons.subdirectory_arrow_right_rounded),
                    suffixIcon: IconButton(
                      onPressed: () {
                        final c = _subtasks.removeAt(index);
                        c.dispose();
                        setState(() {});
                      },
                      icon: const Icon(Icons.close_rounded),
                    ),
                  ),
                ),'''
new_subtask_field = r'''                child: Row(
                  children: [
                    Checkbox(
                      value: _subtasks[index].done,
                      onChanged: (value) => setState(
                        () => _subtasks[index].done = value ?? false,
                      ),
                    ),
                    Expanded(
                      child: TextField(
                        controller: _subtasks[index].controller,
                        decoration: InputDecoration(
                          hintText: 'Subtarefa ${index + 1}',
                          suffixIcon: IconButton(
                            onPressed: () {
                              final draft = _subtasks.removeAt(index);
                              draft.dispose();
                              setState(() {});
                            },
                            icon: const Icon(Icons.close_rounded),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),'''
replace_once(editors, old_subtask_field, new_subtask_field, "editable subtask completion")

# Routine validation and duration formatting.
replace_once(
    editors,
    "  Future<void> _save() async {\n    if (_title.text.trim().isEmpty) return;\n    final old = widget.original;\n    final item = RoutineItem(",
    "  Future<void> _save() async {\n    if (_title.text.trim().isEmpty) {\n      ScaffoldMessenger.of(context).showSnackBar(\n        const SnackBar(content: Text('Informe um nome para o hábito.')),\n      );\n      return;\n    }\n    if (_frequency == 'custom' && _selectedWeekdays.isEmpty) {\n      ScaffoldMessenger.of(context).showSnackBar(\n        const SnackBar(content: Text('Selecione pelo menos um dia da semana.')),\n      );\n      return;\n    }\n    if (_reminder >= 0 && _time.isEmpty) {\n      ScaffoldMessenger.of(context).showSnackBar(\n        const SnackBar(content: Text('Defina um horário para usar um lembrete.')),\n      );\n      return;\n    }\n    final old = widget.original;\n    final duration = (int.tryParse(_minutes.text.trim()) ?? 15).clamp(0, 1440).toInt();\n    final item = RoutineItem(",
    "routine editor validation",
)
replace_once(
    editors,
    "      minutes: int.tryParse(_minutes.text) ?? 15,",
    "      minutes: duration,",
    "clamp routine duration",
)
# The second duration field occurrence belongs to routine editor.
text = read(editors)
needle = "                    controller: _minutes,\n                    keyboardType: TextInputType.number,\n                    decoration: const InputDecoration(\n                      labelText: 'Duração (min)',"
if needle in text:
    text = text.replace(
        needle,
        "                    controller: _minutes,\n                    keyboardType: TextInputType.number,\n                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],\n                    decoration: const InputDecoration(\n                      labelText: 'Duração (min)',",
        1,
    )
    write(editors, text)
# Goal progress is numeric only.
replace_once(
    editors,
    "                controller: progress,\n                keyboardType: TextInputType.number,\n                decoration: const InputDecoration(\n                  labelText: 'Progresso (%)',",
    "                controller: progress,\n                keyboardType: TextInputType.number,\n                inputFormatters: [FilteringTextInputFormatter.digitsOnly],\n                decoration: const InputDecoration(\n                  labelText: 'Progresso (%)',",
    "goal progress digits only",
)

# -----------------------------------------------------------------------------
# Settings: numeric formatters and platform-correct notification messaging.
# -----------------------------------------------------------------------------
settings = "lib/screens/settings_page.dart"
replace_once(
    settings,
    "import 'package:flutter/material.dart';",
    "import 'package:flutter/foundation.dart';\nimport 'package:flutter/material.dart';\nimport 'package:flutter/services.dart';",
    "settings platform/input imports",
)
# Apply digits-only to all three planner fields.
text = read(settings)
for label in ["'Capacidade (h/dia)'", "'Começa às'", "'Termina às'"]:
    pos = text.find(label)
    if pos < 0:
        raise SystemExit(f"settings field missing: {label}")
    keypos = text.rfind("keyboardType: TextInputType.number,", 0, pos)
    if keypos < 0:
        raise SystemExit(f"keyboard field missing before {label}")
    insert_at = keypos + len("keyboardType: TextInputType.number,")
    if "FilteringTextInputFormatter.digitsOnly" not in text[keypos:pos]:
        text = text[:insert_at] + "\n                    inputFormatters: [FilteringTextInputFormatter.digitsOnly]," + text[insert_at:]
write(settings, text)
replace_once(
    settings,
    "    await widget.state.setPlannerSettings(\n      startHour: start,\n      endHour: end,\n      capacityMinutes: capacityHours.clamp(1, 16).toInt() * 60,\n      includeWeekend: widget.state.plannerIncludeWeekend,\n    );",
    "    final capacity = capacityHours.clamp(1, 16).toInt();\n    await widget.state.setPlannerSettings(\n      startHour: start,\n      endHour: end,\n      capacityMinutes: capacity * 60,\n      includeWeekend: widget.state.plannerIncludeWeekend,\n    );\n    _capacity.text = '$capacity';\n    _startHour.text = '$start';\n    _endHour.text = '$end';",
    "normalize planner field values",
)
old_notif = r'''        _SettingsCard(
          title: 'Notificações',
          icon: Icons.notifications_none_rounded,
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.notifications_active_outlined),
              title: const Text('Permissão de notificações'),
              subtitle: const Text(
                'Necessária para lembretes e o cronômetro de foco em background.',
              ),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () async {
                await NativeBridge.requestNotificationPermission();
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Solicitação enviada ao Android.'),
                      behavior: SnackBarBehavior.floating,
                    ),
                  );
                }
              },
            ),
          ],
        ),'''
new_notif = r'''        if (defaultTargetPlatform == TargetPlatform.android)
          _SettingsCard(
            title: 'Notificações',
            icon: Icons.notifications_none_rounded,
            children: [
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.notifications_active_outlined),
                title: const Text('Permissão de notificações'),
                subtitle: const Text(
                  'Necessária para lembretes e o cronômetro de foco em background.',
                ),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () async {
                  await NativeBridge.requestNotificationPermission();
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Solicitação de permissão enviada.'),
                        behavior: SnackBarBehavior.floating,
                      ),
                    );
                  }
                },
              ),
            ],
          )
        else
          const _SettingsCard(
            title: 'Notificações',
            icon: Icons.notifications_none_rounded,
            children: [
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(Icons.desktop_windows_outlined),
                title: Text('Lembretes no Windows'),
                subtitle: Text(
                  'A versão portátil mantém os dados e recursos de produtividade, mas os lembretes nativos ainda são exclusivos do Android.',
                ),
              ),
            ],
          ),'''
replace_once(settings, old_notif, new_notif, "platform-correct notification settings")
replace_once(settings, "title: const Text('Ritmo 3.4.0'),", "title: const Text('Ritmo 3.4.1'),", "settings version")

# -----------------------------------------------------------------------------
# Android targeted sync and reminder correctness.
# -----------------------------------------------------------------------------
activity = "android/app/src/main/java/com/ritmo/mobile/MainActivity.java"
insert_case = r'''                    case "syncTaskReminder": {
                        Number taskId = call.argument("taskId");
                        long id = taskId == null ? 0L : taskId.longValue();
                        Store store = new Store(this);
                        Store.Task task = store.findTask(id);
                        if (task == null) ReminderScheduler.cancel(this, id);
                        else ReminderScheduler.schedule(this, task);
                        result.success(null);
                        break;
                    }
                    case "syncRoutineReminder": {
                        Number routineId = call.argument("routineId");
                        long id = routineId == null ? 0L : routineId.longValue();
                        Store store = new Store(this);
                        Store.Routine routine = store.findRoutine(id);
                        if (routine == null) RoutineReminderScheduler.cancel(this, id);
                        else RoutineReminderScheduler.schedule(this, routine);
                        result.success(null);
                        break;
                    }
'''
text = read(activity)
marker = '                    case "loadFocusState": {\n'
if insert_case not in text:
    if marker not in text:
        raise SystemExit("MainActivity reminder insertion marker missing")
    text = text.replace(marker, insert_case + marker, 1)
    write(activity, text)

reminder = "android/app/src/main/java/com/ritmo/mobile/ReminderScheduler.java"
replace_once(
    reminder,
    "    public static void schedule(Context context, Store.Task task) {\n        cancel(context, task.id);\n        if (task.reminderMinutes < 0 || task.time == null || task.time.trim().isEmpty()) return;",
    "    public static void schedule(Context context, Store.Task task) {\n        if (task == null) return;\n        cancel(context, task.id);\n        if (\"done\".equals(task.status) || task.inbox || task.reminderMinutes < 0 || task.time == null || task.time.trim().isEmpty()) return;",
    "skip completed/Inbox task reminders",
)
replace_once(
    reminder,
    "    public static void scheduleAt(Context context, Store.Task task, long when) {\n        if (when <= System.currentTimeMillis()) return;",
    "    public static void scheduleAt(Context context, Store.Task task, long when) {\n        if (task == null || \"done\".equals(task.status) || task.inbox || when <= System.currentTimeMillis()) return;",
    "guard snoozed task reminder",
)

routine_scheduler = "android/app/src/main/java/com/ritmo/mobile/RoutineReminderScheduler.java"
replace_once(
    routine_scheduler,
    "    public static void schedule(Context context, Store.Routine routine) {\n        cancel(context, routine.id);\n        if (routine == null || routine.reminderMinutes < 0 || routine.time == null || routine.time.trim().isEmpty()) return;",
    "    public static void schedule(Context context, Store.Routine routine) {\n        if (routine == null) return;\n        cancel(context, routine.id);\n        if (routine.reminderMinutes < 0 || routine.time == null || routine.time.trim().isEmpty()) return;",
    "routine null safety",
)
replace_once(
    routine_scheduler,
    "    public static void scheduleAt(Context context, Store.Routine routine, long when) {\n        if (routine == null || when <= System.currentTimeMillis()) return;",
    "    public static void scheduleAt(Context context, Store.Routine routine, long when) {\n        if (routine == null || routine.doneOn(Store.today()) || when <= System.currentTimeMillis()) return;",
    "guard snoozed completed routine",
)

receiver = "android/app/src/main/java/com/ritmo/mobile/ReminderReceiver.java"
# Validate task state at the moment a potentially stale alarm fires.
replace_once(
    receiver,
    "        String title = intent.getStringExtra(\"title\");\n        if (title == null || title.trim().isEmpty()) title = \"Você tem uma tarefa agora\";",
    "        Store currentStore = new Store(context);\n        Store.Task currentTask = currentStore.findTask(taskId);\n        if (currentTask == null || \"done\".equals(currentTask.status) || currentTask.inbox) {\n            cancelNotification(context, notificationId);\n            return;\n        }\n        String title = currentTask.title;\n        if (title == null || title.trim().isEmpty()) title = \"Você tem uma tarefa agora\";",
    "ignore stale task alarms",
)
# Validate habit state before displaying stale alarms and reschedule the next due occurrence.
replace_once(
    receiver,
    "        String title = intent.getStringExtra(\"title\");\n        if (title == null || title.trim().isEmpty()) title = routine == null ? \"Hora da sua rotina\" : routine.title;",
    "        if (routine == null || !routine.dueOn(Store.today()) || routine.doneOn(Store.today())) {\n            cancelNotification(context, notificationId);\n            if (routine != null) RoutineReminderScheduler.schedule(context, routine);\n            return;\n        }\n        String title = routine.title;\n        if (title == null || title.trim().isEmpty()) title = \"Hora da sua rotina\";",
    "ignore stale routine alarms",
)

store = "android/app/src/main/java/com/ritmo/mobile/Store.java"
# No demo data or destructive replacement when storage is missing/corrupt.
replace_once(
    store,
    "        if (raw == null || raw.trim().isEmpty()) {\n            seed(); save(); return;\n        }",
    "        if (raw == null || raw.trim().isEmpty()) {\n            return;\n        }",
    "native clean fresh store",
)
replace_once(
    store,
    "            tasks.clear(); goals.clear(); routines.clear(); completions.clear(); projects.clear(); focusSessions.clear(); dayReviews.clear();\n            seed(); save();",
    "            tasks.clear(); goals.clear(); routines.clear(); completions.clear(); projects.clear(); focusSessions.clear(); dayReviews.clear();",
    "native preserve corrupt store",
)
# Native day metrics follow Inbox semantics.
text = read(store)
text = text.replace('for (Task t : tasks) if (iso.equals(t.date)) total += t.minutes;', 'for (Task t : tasks) if (!t.inbox && iso.equals(t.date)) total += t.minutes;')
text = text.replace('for (Task t : tasks) if (iso.equals(t.date)) total++;', 'for (Task t : tasks) if (!t.inbox && iso.equals(t.date)) total++;')
text = text.replace('for (Task t : tasks) if (iso.equals(t.date)) { total++; if ("done".equals(t.status)) done++; }', 'for (Task t : tasks) if (!t.inbox && iso.equals(t.date)) { total++; if ("done".equals(t.status)) done++; }')
text = text.replace('for (Task t : tasks) if (!"done".equals(t.status) && t.date.compareTo(today()) < 0) total++;', 'for (Task t : tasks) if (!t.inbox && !"done".equals(t.status) && t.date.compareTo(today()) < 0) total++;')
text = text.replace('for (Task t : tasks) if (date.equals(t.date) && !"done".equals(t.status)) total++;', 'for (Task t : tasks) if (!t.inbox && date.equals(t.date) && !"done".equals(t.status)) total++;')
text = text.replace('for (Task t : tasks) if (date.equals(t.date) && "done".equals(t.status)) total++;', 'for (Task t : tasks) if (!t.inbox && date.equals(t.date) && "done".equals(t.status)) total++;')
write(store, text)
replace_once(
    store,
    "            task.date = next; task.status = \"todo\"; changed = true;",
    "            task.date = next; task.deadline = next; task.status = \"todo\"; changed = true;",
    "native recurring deadline parity",
)
old_monthly = '        else if ("monthly".equals(recurrence)) c.add(Calendar.MONTH, 1);'
new_monthly = r'''        else if ("monthly".equals(recurrence)) {
            int day = c.get(Calendar.DAY_OF_MONTH);
            c.set(Calendar.DAY_OF_MONTH, 1);
            c.add(Calendar.MONTH, 1);
            int maxDay = c.getActualMaximum(Calendar.DAY_OF_MONTH);
            c.set(Calendar.DAY_OF_MONTH, Math.min(day, maxDay));
        }'''
replace_once(store, old_monthly, new_monthly, "native monthly recurrence parity")
replace_once(
    store,
    "            if (\"done\".equals(t.status) || !t.flexible || !\"none\".equals(t.recurrence)) continue;",
    "            if (t.inbox || \"done\".equals(t.status) || !t.flexible || !\"none\".equals(t.recurrence)) continue;",
    "native overdue replan Inbox guard",
)
replace_once(
    store,
    "            if (!fromDate.equals(t.date) || \"done\".equals(t.status)) continue;",
    "            if (t.inbox || !fromDate.equals(t.date) || \"done\".equals(t.status)) continue;",
    "native move flexible Inbox guard",
)
# Routine start-date and accent parity with Dart.
text = read(store).replace('"Pessoal", "violet", -1, 0)', '"Pessoal", "indigo", -1, 0)')
text = text.replace('? "violet" : accent;', '? "indigo" : accent;')
text = text.replace('o.optString("accent", "violet")', 'o.optString("accent", "indigo")')
write(store, text)
replace_once(
    store,
    "        public boolean dueOn(String date) {\n            Calendar c = Calendar.getInstance(); c.setTime(parse(date)); int dow = c.get(Calendar.DAY_OF_WEEK);",
    "        public boolean dueOn(String date) {\n            if (startDate != null && startDate.length() == 10 && date.compareTo(startDate) < 0) return false;\n            Calendar c = Calendar.getInstance(); c.setTime(parse(date)); int dow = c.get(Calendar.DAY_OF_WEEK);",
    "native routine start date guard",
)

# Widget counts current task state rather than relying on historical completions.
widget = "android/app/src/main/java/com/ritmo/mobile/RitmoWidgetProvider.java"
replace_once(
    widget,
    "        int done = store.completedOn(today);",
    "        int done = store.doneTasksOn(today);",
    "widget current completion count",
)

# Foreground focus notification already has a system chronometer; rebuilding the
# whole notification every second is unnecessary. Wake only at completion.
service = "android/app/src/main/java/com/ritmo/mobile/FocusTimerService.java"
replace_once(
    service,
    "            startForeground(NOTIFICATION_ID, buildRunningNotification(p));\n            handler.postDelayed(this, 1000L);",
    "            long delay = Math.max(500L, endAt - System.currentTimeMillis());\n            handler.postDelayed(this, delay);",
    "efficient native focus timer",
)

# -----------------------------------------------------------------------------
# New launcher identity: graphite squircle + indigo rhythm ring + white pulse.
# Android receives vector + adaptive icon resources; Windows receives a matching
# multi-resolution ICO generated with Pillow.
# -----------------------------------------------------------------------------
base_icon = '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#111318"
        android:pathData="M26,10H82Q98,10 98,26V82Q98,98 82,98H26Q10,98 10,82V26Q10,10 26,10Z" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="#8B95FF"
        android:strokeWidth="7.5"
        android:strokeLineCap="round"
        android:pathData="M28,70A32,32 0,1 1,78 77" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="#F7F8FF"
        android:strokeWidth="5.5"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M29,55L41,55L47,42L56,68L63,52L78,52" />
    <path
        android:fillColor="#A78BFA"
        android:pathData="M82,74A4,4 0,1 1,74 74A4,4 0,1 1,82 74" />
</vector>
'''
foreground_icon = '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#00000000"
        android:strokeColor="#8B95FF"
        android:strokeWidth="7.5"
        android:strokeLineCap="round"
        android:pathData="M28,70A32,32 0,1 1,78 77" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="#F7F8FF"
        android:strokeWidth="5.5"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M29,55L41,55L47,42L56,68L63,52L78,52" />
    <path
        android:fillColor="#A78BFA"
        android:pathData="M82,74A4,4 0,1 1,74 74A4,4 0,1 1,82 74" />
</vector>
'''
adaptive = '''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ritmo_launcher_bg" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
write("android/app/src/main/res/drawable/ic_launcher.xml", base_icon)
write("android/app/src/main/res/drawable/ic_launcher_foreground.xml", foreground_icon)
write("android/app/src/main/res/mipmap-anydpi/ic_launcher.xml", base_icon)
write("android/app/src/main/res/mipmap-anydpi/ic_launcher_round.xml", base_icon)
write("android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml", adaptive)
write("android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)

colors_path = "android/app/src/main/res/values/colors.xml"
colors = '''<resources>
    <color name="ritmo_launcher_bg">#111318</color>
</resources>
'''
write(colors_path, colors)

manifest = "android/app/src/main/AndroidManifest.xml"
text = read(manifest)
text = text.replace('android:icon="@drawable/ic_launcher"', 'android:icon="@mipmap/ic_launcher"')
text = text.replace('android:roundIcon="@drawable/ic_launcher"', 'android:roundIcon="@mipmap/ic_launcher_round"')
write(manifest, text)

# Generate a matching Windows icon.
from PIL import Image, ImageDraw

size = 1024
img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle((62, 62, 962, 962), radius=220, fill="#111318")
d.rounded_rectangle((82, 82, 942, 942), radius=200, outline="#20232C", width=8)
d.arc((205, 205, 819, 819), start=205, end=505, fill="#8B95FF", width=72)
# Pulse / check waveform.
pulse = [(278, 520), (405, 520), (468, 390), (557, 656), (630, 500), (760, 500)]
d.line(pulse, fill="#F7F8FF", width=55, joint="curve")
for point in pulse:
    x, y = point
    r = 27
    d.ellipse((x-r, y-r, x+r, y+r), fill="#F7F8FF")
# Accent endpoint.
d.ellipse((748, 700, 824, 776), fill="#A78BFA")
Path("windows/runner/resources").mkdir(parents=True, exist_ok=True)
img.save(
    "windows/runner/resources/app_icon.ico",
    format="ICO",
    sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)],
)

# -----------------------------------------------------------------------------
# Version, docs and tests.
# -----------------------------------------------------------------------------
pub = read("pubspec.yaml")
pub = re.sub(r"^version:\s*.*$", "version: 3.4.1+13", pub, flags=re.M)
write("pubspec.yaml", pub)

gradle = read("android/app/build.gradle")
gradle = re.sub(
    r'flutterVersionCode = localProperties\.getProperty\("flutter\.versionCode"\) \?: "\d+"',
    'flutterVersionCode = localProperties.getProperty("flutter.versionCode") ?: "13"',
    gradle,
)
gradle = re.sub(
    r'flutterVersionName = localProperties\.getProperty\("flutter\.versionName"\) \?: "[^"]+"',
    'flutterVersionName = localProperties.getProperty("flutter.versionName") ?: "3.4.1"',
    gradle,
)
write("android/app/build.gradle", gradle)

readme = read("README.md").replace("3.4.0", "3.4.1")
write("README.md", readme)

changelog = read("CHANGELOG.md")
entry = '''# Changelog\n\n## 3.4.1 — Stability & Performance Polish\n\n### Correções\n- Hábitos respeitam corretamente a data de início no Flutter e no Android nativo.\n- Tarefas da Inbox não entram em métricas diárias, Kanban, atraso ou alarmes.\n- Alarmes antigos não exibem notificações de tarefas já concluídas ou movidas para a Inbox.\n- Recorrência mensal nativa foi alinhada à regra do Flutter para fins de mês.\n- Sessões de foco concluídas em background são recuperadas e registradas ao retornar ao app.\n- Subtarefas preservam identidade e estado ao remover/reordenar itens e agora podem ser marcadas no editor.\n- Lembretes exigem horário e hábitos personalizados exigem pelo menos um dia.\n- Estado vazio da tela Hoje e próxima ação da Timeline foram corrigidos.\n\n### Desempenho\n- Persistência serializada para evitar escritas concorrentes.\n- Sincronização de lembretes passou a ser direcionada para tarefas/hábitos alterados.\n- Smart Planner indexa histórico de foco em uma única passagem.\n- Timer de foco Flutter atualiza uma vez por segundo e pausa o ticker quando necessário.\n- Foreground service Android deixa o cronômetro do sistema atualizar a notificação sem reconstruí-la a cada segundo.\n- Animações decorativas desnecessárias foram reduzidas.\n\n### Design\n- Novo ícone do Ritmo: base grafite, anel índigo e pulso branco.\n- Ícone adaptativo no Android e ICO multirresolução no Windows.\n\n### Qualidade\n- Testes unitários adicionados para modelos, recorrências, Inbox e Smart Planner.\n- Builds Android e Windows passam a executar `flutter test` antes do release.\n\n'''
changelog = re.sub(r"^# Changelog\s*", "", changelog)
write("CHANGELOG.md", entry + changelog)

models_test = r'''import 'package:flutter_test/flutter_test.dart';
import 'package:ritmo/models/models.dart';

void main() {
  group('RoutineItem', () {
    test('does not become due before start date', () {
      final routine = RoutineItem(
        id: 1,
        title: 'Começar depois',
        startDate: '2026-08-20',
        frequency: 'daily',
      );
      expect(routine.dueOn('2026-08-19'), isFalse);
      expect(routine.dueOn('2026-08-20'), isTrue);
    });

    test('custom weekday respects both mask and start date', () {
      // Monday in the Java-compatible mask used by the app = bit 1.
      final routine = RoutineItem(
        id: 2,
        title: 'Segunda',
        startDate: '2026-08-17',
        frequency: 'custom',
        daysMask: 1 << 1,
      );
      expect(routine.dueOn('2026-08-10'), isFalse);
      expect(routine.dueOn('2026-08-17'), isTrue);
      expect(routine.dueOn('2026-08-18'), isFalse);
    });
  });

  test('TaskItem round-trip preserves Smart Planner metadata', () {
    final task = TaskItem(
      id: 7,
      title: 'Auditar app',
      date: '2026-08-18',
      deadline: '2026-08-20',
      inbox: true,
      flexible: true,
      energy: 'high',
      preferredPeriod: 'morning',
      subtasks: [Subtask(id: 8, title: 'Teste', done: true)],
    );
    final restored = TaskItem.fromJson(task.toJson());
    expect(restored.inbox, isTrue);
    expect(restored.energy, 'high');
    expect(restored.preferredPeriod, 'morning');
    expect(restored.subtasks.single.done, isTrue);
  });
}
'''
write("test/models_test.dart", models_test)

planner_test = r'''import 'package:flutter_test/flutter_test.dart';
import 'package:ritmo/models/models.dart';
import 'package:ritmo/services/planner_service.dart';

void main() {
  test('Smart Planner ignores Inbox and fixed tasks', () {
    final data = RitmoData(tasks: [
      TaskItem(
        id: 1,
        title: 'Inbox',
        date: '2026-08-18',
        inbox: true,
        flexible: true,
      ),
      TaskItem(
        id: 2,
        title: 'Fixa',
        date: '2026-08-18',
        time: '09:00',
        flexible: false,
        minutes: 60,
      ),
      TaskItem(
        id: 3,
        title: 'Flexível',
        date: '2026-08-18',
        deadline: '2026-08-19',
        flexible: true,
        minutes: 30,
      ),
    ]);

    final result = PlannerService.plan(
      data,
      const PlannerSettings(
        startHour: 8,
        endHour: 18,
        capacityMinutes: 360,
        horizonDays: 2,
      ),
      fromDate: '2026-08-18',
    );

    expect(result.assignments.map((e) => e.taskId), contains(3));
    expect(result.assignments.map((e) => e.taskId), isNot(contains(1)));
    expect(result.assignments.map((e) => e.taskId), isNot(contains(2)));
    expect(result.assignments.single.newTime, isNot('09:00'));
  });

  test('focus history adjusts estimate without scanning semantics changes', () {
    final task = TaskItem(
      id: 10,
      title: 'Estudo',
      date: '2026-08-18',
      deadline: '2026-08-18',
      flexible: true,
      minutes: 30,
    );
    final data = RitmoData(
      tasks: [task],
      focusSessions: List.generate(
        5,
        (i) => FocusSession(
          id: i,
          taskId: 10,
          title: 'Estudo',
          date: '2026-08-${(10 + i).toString().padLeft(2, '0')}',
          plannedMinutes: 30,
          actualMinutes: 50,
          startedAt: i,
        ),
      ),
    );
    final result = PlannerService.plan(
      data,
      const PlannerSettings(horizonDays: 1),
      fromDate: '2026-08-18',
    );
    expect(result.assignments.single.estimatedMinutes, greaterThan(30));
  });
}
'''
write("test/planner_service_test.dart", planner_test)

print("Ritmo 3.4.1 polish patch generated successfully")
