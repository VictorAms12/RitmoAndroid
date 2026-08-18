from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    Path(path).write_text(text, encoding="utf-8", newline="\n")


def replace_once(path: str, old: str, new: str, label: str) -> None:
    text = read(path)
    if new in text:
        print(f"already applied: {label}")
        return
    if old not in text:
        raise SystemExit(f"expected block not found: {label} ({path})")
    write(path, text.replace(old, new, 1))
    print(f"updated: {label}")


# ---------------------------------------------------------------------------
# AppState: eliminate lifecycle/save races, sanitize planner configuration,
# reconcile completion history after edits and make focus statistics accurate.
# ---------------------------------------------------------------------------
app = "lib/core/app_state.dart"
replace_once(
    app,
    """  Future<void> refreshFromNative() async {
    if (loading) return;
    try {
      await _loadData();""",
    """  Future<void> refreshFromNative() async {
    if (loading) return;
    try {
      // Never reload an older native snapshot while a previous save is still
      // being flushed. This matters when the app resumes during a write.
      await _saveChain;
      await _loadData();""",
    "refresh waits for pending persistence",
)

replace_once(
    app,
    """    plannerIncludeWeekend = await _prefs.getBool('planner_weekend') ??
        (legacy['plannerIncludeWeekend'] as bool?) ??
        true;
  }""",
    """    plannerIncludeWeekend = await _prefs.getBool('planner_weekend') ??
        (legacy['plannerIncludeWeekend'] as bool?) ??
        true;

    // Settings may come from older/native versions. Keep malformed values from
    // producing an impossible planner window or capacity.
    plannerStartHour = plannerStartHour.clamp(0, 23).toInt();
    plannerEndHour = plannerEndHour.clamp(plannerStartHour + 1, 24).toInt();
    plannerCapacityMinutes = plannerCapacityMinutes.clamp(60, 16 * 60).toInt();
  }""",
    "sanitize planner settings",
)

replace_once(
    app,
    """  Future<void> setTaskStatus(TaskItem task, String status) async {
    final wasDone = task.status == 'done';""",
    """  Future<void> setTaskStatus(TaskItem task, String status) async {
    if (!const {'todo', 'doing', 'done'}.contains(status)) return;
    final wasDone = task.status == 'done';""",
    "validate task status",
)

replace_once(
    app,
    """  Future<void> addOrUpdateTask(TaskItem task) async {
    final index = data.tasks.indexWhere((e) => e.id == task.id);
    if (index >= 0) {
      data.tasks[index] = task;
    } else {
      data.tasks.add(task);
    }
    notifyListeners();
    await save(syncReminders: false);
    await NativeBridge.syncTaskReminder(task.id);
  }""",
    """  Future<void> addOrUpdateTask(TaskItem task) async {
    final index = data.tasks.indexWhere((e) => e.id == task.id);
    final previousDate = index >= 0 ? data.tasks[index].date : null;
    if (index >= 0) {
      data.tasks[index] = task;
    } else {
      data.tasks.add(task);
    }
    _reconcileTaskCompletion(task, previousDate: previousDate);
    notifyListeners();
    await save(syncReminders: false);
    await NativeBridge.syncTaskReminder(task.id);
  }

  void _reconcileTaskCompletion(TaskItem task, {String? previousDate}) {
    // Editing a completed task must not leave stale title/category/minutes or
    // a completion entry attached to its old scheduled date.
    if (previousDate != null && previousDate != task.date) {
      data.completions.removeWhere(
        (e) => e.taskId == task.id && e.date == previousDate,
      );
    }
    data.completions.removeWhere(
      (e) => e.taskId == task.id && e.date == task.date,
    );
    if (task.status == 'done') {
      data.completions.add(CompletionItem(
        taskId: task.id,
        title: task.title,
        date: task.date,
        category: task.category,
        minutes: task.minutes,
      ));
    }
  }""",
    "reconcile completion history after task edit",
)

replace_once(
    app,
    """  Future<bool> _recoverExpiredFocusIfNeeded() async {
    if (!focusActive || focusEndAt <= 0 || focusRemainingSeconds > 0) {
      return false;
    }
    if (focusEndAt > DateTime.now().millisecondsSinceEpoch) return false;

    final exists = data.focusSessions.any(""",
    """  Future<bool> _recoverExpiredFocusIfNeeded() async {
    if (!focusActive || !focusRunning || focusEndAt <= 0) return false;
    final now = DateTime.now().millisecondsSinceEpoch;
    if (focusEndAt > now) {
      focusRemainingSeconds =
          max(0, ((focusEndAt - now) / 1000).ceil());
      return false;
    }
    focusRemainingSeconds = 0;

    final exists = data.focusSessions.any(""",
    "recover expired focus from wall clock on every platform",
)

old_finish = """  Future<void> finishFocus({bool completeTask = false}) async {
    if (!focusActive) return;
    final remaining = currentFocusRemainingSeconds();
    final executedSeconds = max(0, focusPlannedMinutes * 60 - remaining);
    final actual = max(1, min(focusPlannedMinutes, (executedSeconds / 60).ceil()));
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
    if (completeTask && focusTaskId != 0) {
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
    focusActive = false;
    focusRunning = false;
    focusRemainingSeconds = 0;
    focusEndAt = 0;
    notifyListeners();
    await NativeBridge.stopFocus();
    await save(syncReminders: !completeTask ? false : focusTaskId != 0);
  }"""
new_finish = """  Future<void> finishFocus({bool completeTask = false}) async {
    if (!focusActive) return;
    final remaining = currentFocusRemainingSeconds();
    final executedSeconds = max(0, focusPlannedMinutes * 60 - remaining);
    final actual = min(
      focusPlannedMinutes,
      (executedSeconds / 60).round(),
    ).clamp(0, focusPlannedMinutes).toInt();

    // Finishing immediately should not create a fake one-minute session.
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

    var completedTaskId = 0;
    if (completeTask && focusTaskId != 0) {
      final task = taskById(focusTaskId);
      if (task != null && task.status != 'done') {
        task.status = 'done';
        _reconcileTaskCompletion(task);
        completedTaskId = task.id;
      }
    }
    focusActive = false;
    focusRunning = false;
    focusRemainingSeconds = 0;
    focusEndAt = 0;
    notifyListeners();
    await NativeBridge.stopFocus();
    await save(syncReminders: false);
    if (completedTaskId != 0) {
      await NativeBridge.syncTaskReminder(completedTaskId);
    }
  }"""
replace_once(app, old_finish, new_finish, "accurate focus finish and targeted reminder sync")

replace_once(
    app,
    """        if (task.date == today &&
            task.status != 'done' &&""",
    """        if (!task.inbox &&
            task.date == today &&
            task.status != 'done' &&""",
    "day review never moves Inbox items",
)

# ---------------------------------------------------------------------------
# Editors: the sanitized/clamped habit duration was calculated but ignored.
# ---------------------------------------------------------------------------
editors = "lib/sheets/editors.dart"
replace_once(
    editors,
    "minutes: int.tryParse(_minutes.text) ?? 15,",
    "minutes: duration,",
    "use clamped routine duration",
)

# ---------------------------------------------------------------------------
# Planner: respect elapsed time today, completed workload, breaks and deadlines.
# ---------------------------------------------------------------------------
planner = "lib/services/planner_service.dart"
replace_once(
    planner,
    """    String? fromDate,
  }) {
    final start = fromDate ?? isoDate(DateTime.now());""",
    """    String? fromDate,
    DateTime? now,
  }) {
    final clock = now ?? DateTime.now();
    final currentDate = isoDate(clock);
    final currentMinute = ((clock.hour * 60 + clock.minute + 4) ~/ 5) * 5;
    final start = fromDate ?? currentDate;""",
    "planner injectable/current clock",
)

replace_once(
    planner,
    """      for (final routine in data.routines) {
        if (!routine.dueOn(date) || routine.doneOn(date)) continue;
        load[date] = (load[date] ?? 0) + max(0, routine.minutes);""",
    """      for (final routine in data.routines) {
        if (!routine.dueOn(date)) continue;
        // A completed habit still consumed part of today's capacity.
        load[date] = (load[date] ?? 0) + max(0, routine.minutes);""",
    "completed routines consume planner capacity",
)

replace_once(
    planner,
    """    final eligible = <TaskItem>[];
    for (final task in data.tasks) {
      if (task.status == 'done' || task.inbox) continue;

      final canMove = task.flexible && task.recurrence == 'none';""",
    """    final eligible = <TaskItem>[];
    for (final task in data.tasks) {
      if (task.inbox) continue;

      if (task.status == 'done') {
        if (task.date.compareTo(start) >= 0 &&
            task.date.compareTo(horizon) <= 0) {
          final mins = max(0, task.minutes);
          load[task.date] = (load[task.date] ?? 0) + mins;
          if (task.time.isNotEmpty) {
            _addOccupied(
              occupied[task.date]!,
              task.time,
              max(15, mins),
              settings.breakMinutes,
            );
          }
        }
        continue;
      }

      final canMove = task.flexible && task.recurrence == 'none';""",
    "completed tasks consume planner capacity",
)

# Add minimum usable time for the current day to both candidate calls.
text = read(planner)
needle = """          settings: settings,
          startDate: start,
        );"""
replacement = """          settings: settings,
          startDate: start,
          minimumStartMinute: date == currentDate
              ? currentMinute
              : settings.startHour * 60,
        );"""
count = text.count(needle)
if count != 2:
    raise SystemExit(f"expected 2 planner candidate call sites, found {count}")
text = text.replace(needle, replacement)
write(planner, text)
print("updated: planner avoids elapsed time today")

replace_once(
    planner,
    """      final over = !best.fits ||
          (load[best.date] ?? 0) + estimate > settings.capacityMinutes ||
          best.time.isEmpty;

      assignments.add(PlannerAssignment(
        taskId: task.id,
        oldDate: task.date,
        oldTime: task.time,
        newDate: best.date,
        newTime: best.time,
        overCapacity: over,
        reason: best.reason,""",
    """      final originalDeadline =
          task.deadline.length == 10 ? task.deadline : task.date;
      final missesDeadline = best.date.compareTo(originalDeadline) > 0;
      final over = missesDeadline ||
          !best.fits ||
          (load[best.date] ?? 0) + estimate > settings.capacityMinutes ||
          best.time.isEmpty;
      final assignmentReason = missesDeadline
          ? '${best.reason} Prazo ultrapassado; revise o prazo ou a carga.'
          : best.reason;

      assignments.add(PlannerAssignment(
        taskId: task.id,
        oldDate: task.date,
        oldTime: task.time,
        newDate: best.date,
        newTime: best.time,
        overCapacity: over,
        reason: assignmentReason,""",
    "planner flags deadline misses",
)

replace_once(
    planner,
    """    required PlannerSettings settings,
    required String startDate,
  }) {
    final period = _preferredPeriod(task);
    final bounds = _periodBounds(period, settings.startHour * 60, settings.endHour * 60);
    var slot = _findSlot(occupied, bounds.$1, bounds.$2, estimatedMinutes);""",
    """    required PlannerSettings settings,
    required String startDate,
    required int minimumStartMinute,
  }) {
    final period = _preferredPeriod(task);
    final usableStart = max(settings.startHour * 60, minimumStartMinute);
    final bounds = _periodBounds(period, usableStart, settings.endHour * 60);
    var slot = _findSlot(
      occupied,
      bounds.$1,
      bounds.$2,
      estimatedMinutes,
      settings.breakMinutes,
    );""",
    "planner candidate minimum start and breaks",
)

replace_once(
    planner,
    """      slot = _findSlot(
        occupied,
        settings.startHour * 60,
        settings.endHour * 60,
        estimatedMinutes,
      );""",
    """      slot = _findSlot(
        occupied,
        usableStart,
        settings.endHour * 60,
        estimatedMinutes,
        settings.breakMinutes,
      );""",
    "planner fallback respects current time and break",
)

replace_once(
    planner,
    """  static String _findSlot(
    List<_Interval> list,
    int dayStart,
    int dayEnd,
    int minutes,
  ) {
    if (dayEnd <= dayStart || minutes <= 0) return '';
    var cursor = dayStart;
    for (final interval in list) {
      if (interval.end <= dayStart || interval.start >= dayEnd) continue;
      if (cursor + minutes <= interval.start) return _formatMinutes(cursor);
      if (interval.end > cursor) cursor = interval.end;
      if (cursor >= dayEnd) return '';
    }
    return cursor + minutes <= dayEnd ? _formatMinutes(cursor) : '';
  }""",
    """  static String _findSlot(
    List<_Interval> list,
    int dayStart,
    int dayEnd,
    int minutes,
    int breakMinutes,
  ) {
    if (dayEnd <= dayStart || minutes <= 0) return '';
    final gap = max(0, breakMinutes);
    var cursor = dayStart;
    for (final interval in list) {
      if (interval.end <= dayStart || interval.start >= dayEnd) continue;
      // When another block follows, reserve the configured break before it.
      if (cursor + minutes + gap <= interval.start) {
        return _formatMinutes(cursor);
      }
      if (interval.end > cursor) cursor = interval.end;
      if (cursor >= dayEnd) return '';
    }
    // No following block: the task only needs to finish before dayEnd.
    return cursor + minutes <= dayEnd ? _formatMinutes(cursor) : '';
  }""",
    "planner break spacing",
)

# ---------------------------------------------------------------------------
# Android native store/notifications: never replace corrupt user data with demo
# data and reject stale notification actions.
# ---------------------------------------------------------------------------
store = "android/app/src/main/java/com/ritmo/mobile/Store.java"
replace_once(
    store,
    """        } catch (Exception e) {
            try { prefs.edit().putString(\"ritmo_data_corrupt_backup\", raw).apply(); } catch (Throwable ignored) { }
            tasks.clear(); goals.clear(); routines.clear(); completions.clear(); projects.clear(); focusSessions.clear(); dayReviews.clear();
            seed(); save();
        }""",
    """        } catch (Exception e) {
            // Preserve the original payload for recovery. Never overwrite a
            // corrupted user database with demo/seed data from a receiver.
            try { prefs.edit().putString(\"ritmo_data_corrupt_backup\", raw).apply(); } catch (Throwable ignored) { }
            tasks.clear(); goals.clear(); routines.clear(); completions.clear(); projects.clear(); focusSessions.clear(); dayReviews.clear();
        }""",
    "native corrupt data preservation",
)

replace_once(
    store,
    """        for (Task t : tasks) {
            if (t.date.compareTo(start) < 0 || t.date.compareTo(today()) > 0) continue;
            total++; if (\"done\".equals(t.status)) done++;
        }""",
    """        for (Task t : tasks) {
            if (t.inbox || t.date.compareTo(start) < 0 || t.date.compareTo(today()) > 0) continue;
            total++; if (\"done\".equals(t.status)) done++;
        }""",
    "native completion rate ignores Inbox",
)

receiver = "android/app/src/main/java/com/ritmo/mobile/ReminderReceiver.java"
replace_once(
    receiver,
    """            Store.Task task = store.findTask(taskId);
            if (task != null && !\"done\".equals(task.status)) store.setTaskStatus(task, \"done\");""",
    """            Store.Task task = store.findTask(taskId);
            if (task != null && !task.inbox && !\"done\".equals(task.status)) {
                store.setTaskStatus(task, \"done\");
            }""",
    "stale task notification cannot complete Inbox",
)
replace_once(
    receiver,
    """        if (ACTION_ROUTINE_COMPLETE.equals(action)) {
            if (routine != null && !routine.doneOn(Store.today())) {
                routine.toggle(Store.today());
                store.save();
            }""",
    """        if (ACTION_ROUTINE_COMPLETE.equals(action)) {
            if (routine != null && routine.dueOn(Store.today()) && !routine.doneOn(Store.today())) {
                routine.toggle(Store.today());
                store.save();
            }""",
    "stale routine notification cannot complete off-schedule habit",
)
replace_once(
    receiver,
    """        if (ACTION_ROUTINE_SNOOZE.equals(action)) {
            if (routine != null) RoutineReminderScheduler.scheduleAt(context, routine, System.currentTimeMillis() + 10 * 60_000L);""",
    """        if (ACTION_ROUTINE_SNOOZE.equals(action)) {
            if (routine != null && routine.dueOn(Store.today())) {
                RoutineReminderScheduler.scheduleAt(context, routine, System.currentTimeMillis() + 10 * 60_000L);
            }""",
    "stale routine notification cannot snooze off-schedule habit",
)

routine_scheduler = "android/app/src/main/java/com/ritmo/mobile/RoutineReminderScheduler.java"
replace_once(
    routine_scheduler,
    """        if (routine == null || routine.doneOn(Store.today()) || when <= System.currentTimeMillis()) return;""",
    """        if (routine == null || !routine.dueOn(Store.today()) || routine.doneOn(Store.today()) || when <= System.currentTimeMillis()) return;""",
    "routine snooze validates current occurrence",
)

# ---------------------------------------------------------------------------
# Tests: cover planner regressions discovered by this audit.
# ---------------------------------------------------------------------------
planner_test = "test/planner_service_test.dart"
test_text = read(planner_test)
marker = "planner does not schedule in elapsed time"
if marker not in test_text:
    insert = r'''

  test('planner does not schedule in elapsed time', () {
    final data = RitmoData(tasks: [
      TaskItem(
        id: 20,
        title: 'Agora ou depois',
        date: '2026-08-18',
        deadline: '2026-08-18',
        flexible: true,
        minutes: 30,
      ),
    ]);
    final result = PlannerService.plan(
      data,
      const PlannerSettings(
        startHour: 8,
        endHour: 18,
        horizonDays: 1,
      ),
      fromDate: '2026-08-18',
      now: DateTime(2026, 8, 18, 11, 7),
    );
    expect(result.assignments.single.newTime.compareTo('11:10') >= 0, isTrue);
  });

  test('planner preserves break before a fixed block', () {
    final data = RitmoData(tasks: [
      TaskItem(
        id: 30,
        title: 'Fixa',
        date: '2026-08-18',
        time: '09:00',
        minutes: 60,
      ),
      TaskItem(
        id: 31,
        title: 'Flexível',
        date: '2026-08-18',
        deadline: '2026-08-18',
        flexible: true,
        minutes: 60,
      ),
    ]);
    final result = PlannerService.plan(
      data,
      const PlannerSettings(
        startHour: 8,
        endHour: 13,
        horizonDays: 1,
        breakMinutes: 10,
      ),
      fromDate: '2026-08-18',
      now: DateTime(2026, 8, 18, 7),
    );
    expect(result.assignments.single.newTime, '10:10');
  });

  test('planner flags assignment that falls after deadline', () {
    final data = RitmoData(tasks: [
      TaskItem(
        id: 40,
        title: 'Prazo no sábado',
        date: '2026-08-22',
        deadline: '2026-08-22',
        flexible: true,
        minutes: 30,
      ),
    ]);
    final result = PlannerService.plan(
      data,
      const PlannerSettings(
        startHour: 8,
        endHour: 18,
        horizonDays: 3,
        includeWeekend: false,
      ),
      fromDate: '2026-08-22',
      now: DateTime(2026, 8, 22, 7),
    );
    final assignment = result.assignments.single;
    expect(assignment.newDate, '2026-08-24');
    expect(assignment.overCapacity, isTrue);
    expect(assignment.reason, contains('Prazo ultrapassado'));
  });

  test('completed work still consumes daily capacity', () {
    final data = RitmoData(tasks: [
      TaskItem(
        id: 50,
        title: 'Já feita',
        date: '2026-08-18',
        time: '08:00',
        status: 'done',
        minutes: 300,
      ),
      TaskItem(
        id: 51,
        title: 'Nova carga',
        date: '2026-08-18',
        deadline: '2026-08-18',
        flexible: true,
        minutes: 120,
      ),
    ]);
    final result = PlannerService.plan(
      data,
      const PlannerSettings(
        startHour: 8,
        endHour: 18,
        capacityMinutes: 360,
        horizonDays: 1,
      ),
      fromDate: '2026-08-18',
      now: DateTime(2026, 8, 18, 7),
    );
    expect(result.assignments.single.overCapacity, isTrue);
    expect(result.loadMinutes['2026-08-18'], greaterThan(360));
  });
'''
    pos = test_text.rfind('\n}')
    if pos < 0:
        raise SystemExit('planner test closing brace not found')
    test_text = test_text[:pos] + insert + test_text[pos:]
    write(planner_test, test_text)
    print('updated: planner regression tests')

# ---------------------------------------------------------------------------
# Release identity/documentation for the audited build.
# ---------------------------------------------------------------------------
pub = read("pubspec.yaml")
pub = re.sub(r"^version:\s*.*$", "version: 3.4.2+14", pub, flags=re.M)
write("pubspec.yaml", pub)

gradle = read("android/app/build.gradle")
gradle = re.sub(
    r'flutterVersionCode = localProperties\.getProperty\("flutter\.versionCode"\) \?: "\d+"',
    'flutterVersionCode = localProperties.getProperty("flutter.versionCode") ?: "14"',
    gradle,
)
gradle = re.sub(
    r'flutterVersionName = localProperties\.getProperty\("flutter\.versionName"\) \?: "[^"]+"',
    'flutterVersionName = localProperties.getProperty("flutter.versionName") ?: "3.4.2"',
    gradle,
)
write("android/app/build.gradle", gradle)

settings = read("lib/screens/settings_page.dart").replace("Ritmo 3.4.1", "Ritmo 3.4.2")
write("lib/screens/settings_page.dart", settings)

readme = read("README.md")
readme = readme.replace("versão-3.4.1-", "versão-3.4.2-")
readme = readme.replace("Versão atual: **3.4.1 —", "Versão atual: **3.4.2 —")
readme = readme.replace("Ritmo-v3.4.1-APK", "Ritmo-v3.4.2-APK")
readme = readme.replace("Ritmo-v3.4.1.apk", "Ritmo-v3.4.2.apk")
readme = readme.replace("Ritmo-v3.4.1-Windows", "Ritmo-v3.4.2-Windows")
readme = readme.replace("Ritmo-v3.4.1-Windows.zip", "Ritmo-v3.4.2-Windows.zip")
readme = readme.replace("Na versão 3.4.1, os dados", "Na versão 3.4.2, os dados")
readme = readme.replace("A versão 3.4.1 possui builds", "A versão 3.4.2 possui builds")
readme = readme.replace("Ritmo 3.4.1\nversionCode: 12", "Ritmo 3.4.2\nversionCode: 14")
# Windows support began in 3.4.0; keep the historical statement accurate.
readme = readme.replace(
    "A partir da versão 3.4.1, Android e Windows compartilham",
    "A partir da versão 3.4.0, Android e Windows compartilham",
)
readme = readme.replace(
    "```bash\nflutter analyze --no-fatal-infos --no-fatal-warnings\n```\n\nA versão 3.4.2 possui builds",
    "```bash\nflutter analyze --no-fatal-infos --no-fatal-warnings\nflutter test\n```\n\nA versão 3.4.2 possui builds",
)
write("README.md", readme)

changelog = read("CHANGELOG.md")
if "## 3.4.2 — Reliability & Logic Polish" not in changelog:
    entry = """## 3.4.2 — Reliability & Logic Polish

### Correções
- Corrigida a duração de hábitos para respeitar o limite validado no editor.
- Conclusões são reconciliadas ao editar tarefas já concluídas, evitando estatísticas com metadados antigos.
- Sessões de foco encerradas imediatamente deixam de registrar um minuto inexistente.
- Recuperação do foco expirado passa a usar o relógio real também fora do Android.
- Refresh ao retornar ao app aguarda gravações pendentes para não restaurar um snapshot antigo.
- Dados nativos corrompidos são preservados para recuperação e não são substituídos por conteúdo de demonstração.
- Ações antigas de notificações não concluem itens da Inbox nem hábitos fora da programação atual.

### Smart Planner
- O Planner não agenda blocos em horários que já passaram no dia atual.
- Intervalos entre blocos são respeitados antes e depois de compromissos existentes.
- Trabalho e hábitos já concluídos continuam contando para a capacidade consumida do dia.
- Sugestões que ultrapassam o prazo agora são explicitamente marcadas como atenção.
- Preferências inválidas carregadas de versões antigas são normalizadas antes do planejamento.

### Qualidade
- Novos testes de regressão cobrem horário atual, intervalos, capacidade consumida e estouro de prazo.
- Pipelines Android e Windows executam análise estática e testes antes do release.

"""
    changelog = changelog.replace("# Changelog\n\n", "# Changelog\n\n" + entry, 1)
    write("CHANGELOG.md", changelog)

print("Final Ritmo 3.4.2 audit patch applied")