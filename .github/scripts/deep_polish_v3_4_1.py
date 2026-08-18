from pathlib import Path


def read(path: str) -> str:
    return Path(path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    Path(path).write_text(text, encoding='utf-8', newline='\n')


def replace_once(path: str, old: str, new: str, label: str) -> None:
    text = read(path)
    if old not in text:
        raise SystemExit(f'missing block: {label} ({path})')
    write(path, text.replace(old, new, 1))
    print(f'fixed: {label}')


# -----------------------------------------------------------------------------
# Editors: finish numeric validation and make Inbox behavior explicit.
# -----------------------------------------------------------------------------
editors = 'lib/sheets/editors.dart'
replace_once(
    editors,
    "      minutes: int.tryParse(_minutes.text) ?? 15,",
    "      minutes: duration,",
    'routine duration clamp actually used',
)
replace_once(
    editors,
    "              onChanged: widget.commitment ? null : (v) => setState(() => _inbox = v),",
    "              onChanged: widget.commitment\n                  ? null\n                  : (v) => setState(() {\n                        _inbox = v;\n                        if (v) {\n                          _time = '';\n                          _reminder = -1;\n                          _recurrence = 'none';\n                          _flexible = true;\n                        }\n                      }),",
    'Inbox editor invariants',
)
replace_once(
    editors,
    "              onChanged: (v) {\n                setState(() {\n                  _recurrence = v ?? _recurrence;\n                  if (_recurrence != 'none') _flexible = false;\n                });\n              },",
    "              onChanged: _inbox\n                  ? null\n                  : (v) {\n                      setState(() {\n                        _recurrence = v ?? _recurrence;\n                        if (_recurrence != 'none') _flexible = false;\n                      });\n                    },",
    'disable recurrence while in Inbox',
)
# Only the task reminder dropdown contains 1440, so this replacement is unique.
replace_once(
    editors,
    "              onChanged: (v) => setState(() => _reminder = v ?? -1),\n            ),\n            const SizedBox(height: 10),\n            DropdownButtonFormField<int>(\n              initialValue: widget.state.data.projects.any((p) => p.id == _projectId)",
    "              onChanged: _inbox\n                  ? null\n                  : (v) => setState(() => _reminder = v ?? -1),\n            ),\n            const SizedBox(height: 10),\n            DropdownButtonFormField<int>(\n              initialValue: widget.state.data.projects.any((p) => p.id == _projectId)",
    'disable task reminder while in Inbox',
)
replace_once(
    editors,
    "              onChanged: _recurrence == 'none'\n                  ? (v) => setState(() => _flexible = v)\n                  : null,",
    "              onChanged: !_inbox && _recurrence == 'none'\n                  ? (v) => setState(() => _flexible = v)\n                  : null,",
    'disable flexible toggle while in Inbox',
)
# Extend picker to support intentionally disabled controls.
replace_once(
    editors,
    "  final VoidCallback onTap;\n  final VoidCallback? onClear;",
    "  final VoidCallback? onTap;\n  final VoidCallback? onClear;",
    'nullable picker action',
)
# Disable task date/deadline/time controls when Inbox is enabled.
replace_once(
    editors,
    "                    onTap: () async {\n                      final value = await _pickDate(context, _date);\n                      if (value != null) setState(() => _date = value);\n                    },",
    "                    onTap: _inbox\n                        ? null\n                        : () async {\n                            final value = await _pickDate(context, _date);\n                            if (value != null) setState(() => _date = value);\n                          },",
    'disable Inbox date picker',
)
replace_once(
    editors,
    "                    onTap: () async {\n                      final value = await _pickDate(context, _deadline);\n                      if (value != null) setState(() => _deadline = value);\n                    },",
    "                    onTap: _inbox\n                        ? null\n                        : () async {\n                            final value = await _pickDate(context, _deadline);\n                            if (value != null) setState(() => _deadline = value);\n                          },",
    'disable Inbox deadline picker',
)
replace_once(
    editors,
    "                    onTap: () async {\n                      final value = await _pickTime(context, _time);\n                      if (value != null) setState(() => _time = value);\n                    },\n                    onClear: _time.isEmpty ? null : () => setState(() => _time = ''),",
    "                    onTap: _inbox\n                        ? null\n                        : () async {\n                            final value = await _pickTime(context, _time);\n                            if (value != null) setState(() => _time = value);\n                          },\n                    onClear: _inbox || _time.isEmpty\n                        ? null\n                        : () => setState(() => _time = ''),",
    'disable Inbox time picker',
)
# Give disabled picker tiles correct visual feedback.
replace_once(
    editors,
    "              Icon(icon, size: 20, color: Theme.of(context).colorScheme.primary),",
    "              Icon(\n                icon,\n                size: 20,\n                color: onTap == null\n                    ? Theme.of(context).disabledColor\n                    : Theme.of(context).colorScheme.primary,\n              ),",
    'disabled picker styling',
)

# -----------------------------------------------------------------------------
# AppState: sanitize settings/history and persist focus state cross-platform.
# -----------------------------------------------------------------------------
app = 'lib/core/app_state.dart'
replace_once(
    app,
    "    plannerIncludeWeekend = await _prefs.getBool('planner_weekend') ??\n        (legacy['plannerIncludeWeekend'] as bool?) ??\n        true;\n  }",
    "    plannerIncludeWeekend = await _prefs.getBool('planner_weekend') ??\n        (legacy['plannerIncludeWeekend'] as bool?) ??\n        true;\n\n    plannerStartHour = plannerStartHour.clamp(0, 23).toInt();\n    plannerEndHour = plannerEndHour.clamp(plannerStartHour + 1, 24).toInt();\n    plannerCapacityMinutes = plannerCapacityMinutes.clamp(60, 960).toInt();\n  }",
    'sanitize loaded planner settings',
)
old_load_focus = """  Future<void> _loadFocus() async {
    final native = await NativeBridge.loadFocusState();
    if (native.isEmpty) return;
    focusActive = native['active'] == true;
    focusRunning = native['running'] == true;
    focusTaskId = (native['taskId'] as num?)?.toInt() ?? 0;
    focusTitle = native['title']?.toString() ?? 'Foco livre';
    focusMode = native['mode']?.toString() ?? 'Pomodoro 25';
    focusPlannedMinutes = (native['plannedMinutes'] as num?)?.toInt() ?? 25;
    focusStartedAt = (native['startedAt'] as num?)?.toInt() ?? 0;
    focusEndAt = (native['endAt'] as num?)?.toInt() ?? 0;
    focusRemainingSeconds =
        (native['remainingSeconds'] as num?)?.toInt() ?? 0;

    if (focusActive && focusRunning && focusEndAt > 0) {
      focusRemainingSeconds =
          max(0, ((focusEndAt - DateTime.now().millisecondsSinceEpoch) / 1000).ceil());
    }
  }
"""
new_load_focus = """  Future<void> _loadFocus() async {
    var source = await NativeBridge.loadFocusState();
    if (source.isEmpty) {
      final raw = await _prefs.getString('ritmo_focus_flutter');
      if (raw != null && raw.isNotEmpty) {
        try {
          final decoded = jsonDecode(raw);
          if (decoded is Map) {
            source = Map<String, dynamic>.from(decoded);
          }
        } catch (_) {
          await _prefs.remove('ritmo_focus_flutter');
        }
      }
    }
    if (source.isEmpty) return;

    focusActive = source['active'] == true;
    focusRunning = source['running'] == true;
    focusTaskId = (source['taskId'] as num?)?.toInt() ?? 0;
    focusTitle = source['title']?.toString() ?? 'Foco livre';
    focusMode = source['mode']?.toString() ?? 'Pomodoro 25';
    focusPlannedMinutes =
        ((source['plannedMinutes'] as num?)?.toInt() ?? 25).clamp(1, 1440).toInt();
    focusStartedAt = (source['startedAt'] as num?)?.toInt() ?? 0;
    focusEndAt = (source['endAt'] as num?)?.toInt() ?? 0;
    focusRemainingSeconds =
        max(0, (source['remainingSeconds'] as num?)?.toInt() ?? 0);

    if (focusActive && focusRunning && focusEndAt > 0) {
      focusRemainingSeconds = max(
        0,
        ((focusEndAt - DateTime.now().millisecondsSinceEpoch) / 1000).ceil(),
      );
    }
  }

  Future<void> _persistFocusFallback() async {
    await _prefs.setString('ritmo_focus_flutter', jsonEncode({
      'active': focusActive,
      'running': focusRunning,
      'taskId': focusTaskId,
      'title': focusTitle,
      'mode': focusMode,
      'plannedMinutes': focusPlannedMinutes,
      'startedAt': focusStartedAt,
      'endAt': focusEndAt,
      'remainingSeconds': focusRemainingSeconds,
    }));
  }

  Future<void> _clearFocusFallback() => _prefs.remove('ritmo_focus_flutter');
"""
replace_once(app, old_load_focus, new_load_focus, 'cross-platform focus persistence')
replace_once(
    app,
    "    focusEndAt = 0;\n    await NativeBridge.stopFocus();\n    return true;",
    "    focusEndAt = 0;\n    await _clearFocusFallback();\n    await NativeBridge.stopFocus();\n    return true;",
    'clear recovered focus fallback',
)
# Sanitize completion history, routine dates and day review uniqueness.
replace_once(
    app,
    "    for (final routine in data.routines) {\n      if (routine.title.trim().isEmpty) routine.title = 'Hábito';",
    "    final completionKeys = <String>{};\n    data.completions.removeWhere((completion) {\n      if (completion.title.trim().isEmpty) completion.title = 'Tarefa';\n      if (completion.date.length != 10) completion.date = today;\n      final key = '${completion.taskId}|${completion.date}';\n      return !completionKeys.add(key);\n    });\n\n    for (final routine in data.routines) {\n      if (routine.title.trim().isEmpty) routine.title = 'Hábito';",
    'deduplicate completion history',
)
replace_once(
    app,
    "      if (routine.time.isNotEmpty && !RegExp(r'^([01]\\d|2[0-3]):[0-5]\\d$').hasMatch(routine.time)) {\n        routine.time = '';\n      }\n    }\n  }",
    "      if (routine.time.isNotEmpty && !RegExp(r'^([01]\\d|2[0-3]):[0-5]\\d$').hasMatch(routine.time)) {\n        routine.time = '';\n      }\n      routine.doneDates = routine.doneDates.toSet().toList();\n    }\n\n    final reviewByDate = <String, DayReview>{};\n    for (final review in data.dayReviews) {\n      if (review.date.length != 10) review.date = today;\n      final previous = reviewByDate[review.date];\n      if (previous == null || review.createdAt >= previous.createdAt) {\n        reviewByDate[review.date] = review;\n      }\n    }\n    data.dayReviews = reviewByDate.values.toList();\n  }",
    'sanitize routine completion and day review history',
)
# Persist focus state before delegating to the Android native service, so Windows
# and bridge failures still have a recoverable source of truth.
replace_once(
    app,
    "    focusActive = true;\n    focusRunning = true;\n    notifyListeners();\n    await NativeBridge.startFocus(",
    "    focusActive = true;\n    focusRunning = true;\n    notifyListeners();\n    await _persistFocusFallback();\n    await NativeBridge.startFocus(",
    'persist focus on start',
)
replace_once(
    app,
    "    focusRunning = false;\n    focusActive = true;\n    notifyListeners();\n    await NativeBridge.pauseFocus(",
    "    focusRunning = false;\n    focusActive = true;\n    focusEndAt = 0;\n    notifyListeners();\n    await _persistFocusFallback();\n    await NativeBridge.pauseFocus(",
    'persist paused focus',
)
replace_once(
    app,
    "    focusEndAt = DateTime.now().millisecondsSinceEpoch + focusRemainingSeconds * 1000;\n    focusRunning = true;\n    notifyListeners();\n    await NativeBridge.startFocus(",
    "    focusEndAt = DateTime.now().millisecondsSinceEpoch + focusRemainingSeconds * 1000;\n    focusRunning = true;\n    notifyListeners();\n    await _persistFocusFallback();\n    await NativeBridge.startFocus(",
    'persist resumed focus',
)
replace_once(
    app,
    "    focusEndAt = 0;\n    notifyListeners();\n    await NativeBridge.stopFocus();\n    await save(syncReminders: false);",
    "    focusEndAt = 0;\n    notifyListeners();\n    await _clearFocusFallback();\n    await NativeBridge.stopFocus();\n    await save(syncReminders: false);",
    'clear focus fallback on finish',
)
replace_once(
    app,
    "    focusEndAt = 0;\n    notifyListeners();\n    await NativeBridge.stopFocus();\n  }\n\n  Future<void> saveDayReview",
    "    focusEndAt = 0;\n    notifyListeners();\n    await _clearFocusFallback();\n    await NativeBridge.stopFocus();\n  }\n\n  Future<void> saveDayReview",
    'clear focus fallback on cancel',
)

# -----------------------------------------------------------------------------
# Habit streaks: stop at the actual start date instead of an arbitrary 370-day
# ceiling. This keeps long-running habits correct and naturally bounded.
# -----------------------------------------------------------------------------
models = 'lib/models/models.dart'
replace_once(
    models,
    """    var value = 0;
    for (var guard = 0; guard < 370; guard++) {
      if (!dueOn(cursor)) {
        cursor = addDaysIso(cursor, -1);
        continue;
      }
      if (!doneOn(cursor)) break;
      value++;
      cursor = addDaysIso(cursor, -1);
    }
    return value;
""",
    """    var value = 0;
    while (cursor.compareTo(startDate) >= 0) {
      if (!dueOn(cursor)) {
        cursor = addDaysIso(cursor, -1);
        continue;
      }
      if (!doneOn(cursor)) break;
      value++;
      cursor = addDaysIso(cursor, -1);
    }
    return value;
""",
    'unbounded-by-age habit streak',
)

# -----------------------------------------------------------------------------
# Main app: ThemeData is immutable for this design system, so build it once
# instead of allocating both full themes after every AppState notification.
# -----------------------------------------------------------------------------
main = 'lib/main.dart'
replace_once(
    main,
    "import 'widgets/common.dart';\n\nFuture<void> main() async {",
    "import 'widgets/common.dart';\n\nfinal _lightTheme = buildLightTheme();\nfinal _darkTheme = buildDarkTheme();\n\nFuture<void> main() async {",
    'cache app themes',
)
replace_once(main, "          theme: buildLightTheme(),\n          darkTheme: buildDarkTheme(),", "          theme: _lightTheme,\n          darkTheme: _darkTheme,", 'use cached app themes')

# -----------------------------------------------------------------------------
# Android parity and alarm PendingIntent identity robustness.
# -----------------------------------------------------------------------------
store = 'android/app/src/main/java/com/ritmo/mobile/Store.java'
replace_once(
    store,
    "            r.minutes = Math.max(0, Math.min(1440, r.minutes));\n        }\n        for (Project p : projects) {",
    "            r.minutes = Math.max(0, Math.min(1440, r.minutes));\n            List<String> uniqueDoneDates = new ArrayList<>();\n            for (String date : r.doneDates) if (date != null && !uniqueDoneDates.contains(date)) uniqueDoneDates.add(date);\n            r.doneDates.clear();\n            r.doneDates.addAll(uniqueDoneDates);\n        }\n\n        Map<String, Completion> uniqueCompletions = new LinkedHashMap<>();\n        for (Completion completion : completions) {\n            if (completion.date == null || completion.date.length() != 10) completion.date = today();\n            if (completion.title == null || completion.title.trim().isEmpty()) completion.title = \"Tarefa\";\n            uniqueCompletions.put(completion.taskId + \"|\" + completion.date, completion);\n        }\n        completions.clear();\n        completions.addAll(uniqueCompletions.values());\n\n        Map<String, DayReview> uniqueReviews = new LinkedHashMap<>();\n        for (DayReview review : dayReviews) {\n            if (review.date == null || review.date.length() != 10) review.date = today();\n            DayReview previous = uniqueReviews.get(review.date);\n            if (previous == null || review.createdAt >= previous.createdAt) uniqueReviews.put(review.date, review);\n        }\n        dayReviews.clear();\n        dayReviews.addAll(uniqueReviews.values());\n\n        for (Project p : projects) {",
    'native history deduplication',
)
replace_once(
    store,
    """        public int streak(String referenceDate) {
            String cursor = referenceDate; if (dueOn(cursor) && !doneOn(cursor)) cursor = addDays(cursor, -1);
            int streak = 0, guard = 0;
            while (guard < 370) {
                if (!dueOn(cursor)) { cursor = addDays(cursor, -1); guard++; continue; }
                if (!doneOn(cursor)) break;
                streak++; cursor = addDays(cursor, -1); guard++;
            }
            return streak;
        }
""",
    """        public int streak(String referenceDate) {
            String cursor = referenceDate;
            if (dueOn(cursor) && !doneOn(cursor)) cursor = addDays(cursor, -1);
            int streak = 0;
            while (cursor.compareTo(startDate) >= 0) {
                if (!dueOn(cursor)) { cursor = addDays(cursor, -1); continue; }
                if (!doneOn(cursor)) break;
                streak++;
                cursor = addDays(cursor, -1);
            }
            return streak;
        }
""",
    'native long habit streak',
)

reminder = 'android/app/src/main/java/com/ritmo/mobile/ReminderScheduler.java'
replace_once(
    reminder,
    "        return PendingIntent.getBroadcast(context, (int)(id & 0x7fffffff), i, flags);",
    "        return PendingIntent.getBroadcast(context, stableId(id), i, flags);",
    'stable task PendingIntent id',
)
replace_once(
    reminder,
    "    private static PendingIntent pending(Context context, long id, String title) {",
    "    private static int stableId(long id) {\n        int mixed = (int)(id ^ (id >>> 32));\n        mixed &= 0x7fffffff;\n        return mixed == 0 ? 1 : mixed;\n    }\n\n    private static PendingIntent pending(Context context, long id, String title) {",
    'task id hash helper',
)

routine = 'android/app/src/main/java/com/ritmo/mobile/RoutineReminderScheduler.java'
replace_once(
    routine,
    "        int requestCode = 600000000 + (int)(Math.abs(id) % 100000000L);\n        return PendingIntent.getBroadcast(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);",
    "        return PendingIntent.getBroadcast(context, stableId(id), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);",
    'stable routine PendingIntent id',
)
replace_once(
    routine,
    "    private static PendingIntent pending(Context context, long id, String title) {",
    "    private static int stableId(long id) {\n        int mixed = (int)(id ^ (id >>> 32));\n        mixed &= 0x7fffffff;\n        return mixed == 0 ? 1 : mixed;\n    }\n\n    private static PendingIntent pending(Context context, long id, String title) {",
    'routine id hash helper',
)

receiver = 'android/app/src/main/java/com/ritmo/mobile/ReminderReceiver.java'
replace_once(
    receiver,
    """    private static int taskNotificationId(long taskId) {
        int id = (int)(taskId & 0x3fffffff);
        return id == 0 ? 1 : id;
    }

    private static int routineNotificationId(long routineId) {
        int id = (int)(Math.abs(routineId) & 0x1fffffff);
        return 1000000000 + (id == 0 ? 1 : id);
    }
""",
    """    private static int stableHash(long id, int mask) {
        int mixed = (int)(id ^ (id >>> 32));
        mixed &= mask;
        return mixed == 0 ? 1 : mixed;
    }

    private static int taskNotificationId(long taskId) {
        return stableHash(taskId, 0x3fffffff);
    }

    private static int routineNotificationId(long routineId) {
        return 1000000000 + stableHash(routineId, 0x1fffffff);
    }
""",
    'stable notification identifiers',
)

# -----------------------------------------------------------------------------
# Changelog: document the last consistency fixes without creating another
# version heading.
# -----------------------------------------------------------------------------
changelog = 'CHANGELOG.md'
text = read(changelog)
text = text.replace(
    '- Lembretes exigem horário e hábitos personalizados exigem pelo menos um dia.\n',
    '- Lembretes exigem horário e hábitos personalizados exigem pelo menos um dia.\n- Hábitos concluídos continuam agendando corretamente a próxima ocorrência.\n- Histórico duplicado de conclusões, hábitos e revisões é saneado ao carregar os dados.\n- Sessões de foco passam a ter fallback persistente também no Windows.\n',
    1,
)
text = text.replace(
    '- Animações decorativas desnecessárias foram reduzidas.\n',
    '- Animações decorativas desnecessárias foram reduzidas.\n- Temas claro e escuro são reutilizados em vez de reconstruídos em cada alteração de estado.\n- IDs de alarmes/notificações usam hash estável do identificador completo, reduzindo colisões.\n',
    1,
)
write(changelog, text)

print('Deep Ritmo 3.4.1 polish applied')
