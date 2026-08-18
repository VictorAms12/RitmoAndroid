from pathlib import Path
import re


def rd(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def wr(path: str, text: str) -> None:
    Path(path).write_text(text, encoding="utf-8", newline="\n")


def replace_once(path: str, old: str, new: str, label: str) -> None:
    text = rd(path)
    if new in text:
        print(f"already: {label}")
        return
    if old not in text:
        raise SystemExit(f"missing expected block: {label} ({path})")
    wr(path, text.replace(old, new, 1))
    print(f"fixed: {label}")


# ---------------------------------------------------------------------------
# Models: strict ISO dates and longer-lived streaks.
# ---------------------------------------------------------------------------
models = "lib/models/models.dart"
text = rd(models)
anchor = "String addDaysIso(String iso, int days) =>\n    isoDate(parseIso(iso).add(Duration(days: days)));\n"
helper = """String addDaysIso(String iso, int days) =>
    isoDate(parseIso(iso).add(Duration(days: days)));

bool isValidIsoDate(String? value) {
  if (value == null ||
      !RegExp(r'^\\d{4}-\\d{2}-\\d{2}$').hasMatch(value)) {
    return false;
  }
  final parsed = DateTime.tryParse(value);
  return parsed != null && isoDate(parsed) == value;
}
"""
if "bool isValidIsoDate(String? value)" not in text:
    if anchor not in text:
        raise SystemExit("models date helper anchor not found")
    text = text.replace(anchor, helper, 1)
text = text.replace("guard < 370", "guard < 10000")
wr(models, text)

# ---------------------------------------------------------------------------
# AppState: stronger sanitization, desktop focus persistence, recurring guard.
# ---------------------------------------------------------------------------
state_path = "lib/core/app_state.dart"
s = rd(state_path)

s = s.replace("if (task.date.length != 10) task.date = today;", "if (!isValidIsoDate(task.date)) task.date = today;")
s = s.replace("if (task.deadline.length != 10) task.deadline = task.date;", "if (!isValidIsoDate(task.deadline) || task.deadline.compareTo(task.date) < 0) {\n        task.deadline = task.date;\n      }")
s = s.replace("if (routine.startDate.length != 10) routine.startDate = today;", "if (!isValidIsoDate(routine.startDate)) routine.startDate = today;")
s = s.replace("guard++ < 370", "guard++ < 10000")

# Enrich sanitization after routines.
san_anchor = """    for (final routine in data.routines) {
      if (routine.title.trim().isEmpty) routine.title = 'Hábito';
      if (!isValidIsoDate(routine.startDate)) routine.startDate = today;
      routine.minutes = routine.minutes.clamp(0, 1440).toInt();
      if (!const {'daily', 'weekdays', 'weekly', 'custom'}
          .contains(routine.frequency)) {
        routine.frequency = 'daily';
      }
      if (routine.time.isNotEmpty &&
          !RegExp(r'^([01]\\d|2[0-3]):[0-5]\\d$').hasMatch(routine.time)) {
        routine.time = '';
      }
    }
"""
san_new = san_anchor + """    final projectIds = data.projects.map((e) => e.id).toSet();
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
"""
if "final projectIds = data.projects.map((e) => e.id).toSet();" not in s:
    if san_anchor not in s:
        raise SystemExit("AppState sanitize anchor not found")
    s = s.replace(san_anchor, san_new, 1)

# Replace native-only focus load with native + SharedPreferences fallback.
load_start = s.index("  Future<void> _loadFocus() async {")
load_end = s.index("\n  Future<bool> _recoverExpiredFocusIfNeeded()", load_start)
new_load = """  Future<void> _loadFocus() async {
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
"""
s = s[:load_start] + new_load + s[load_end:]

# Clear fallback after recovered expired session.
s = s.replace("    await NativeBridge.stopFocus();\n    return true;", "    await NativeBridge.stopFocus();\n    await _clearFocusFallback();\n    return true;", 1)

# Harden startFocus and persist fallback.
start_old = """    feedback();
    focusTaskId = task?.id ?? 0;
    focusTitle = task?.title ?? 'Foco livre';
    focusMode = mode;
    focusPlannedMinutes = plannedMinutes;
    focusStartedAt = DateTime.now().millisecondsSinceEpoch;
    focusRemainingSeconds = plannedMinutes * 60;
"""
start_new = """    feedback();
    final safeMinutes = plannedMinutes.clamp(1, 1440).toInt();
    focusTaskId = task?.id ?? 0;
    focusTitle = task?.title.trim().isNotEmpty == true ? task!.title.trim() : 'Foco livre';
    focusMode = mode.trim().isEmpty ? 'Foco' : mode.trim();
    focusPlannedMinutes = safeMinutes;
    focusStartedAt = DateTime.now().millisecondsSinceEpoch;
    focusRemainingSeconds = safeMinutes * 60;
"""
if start_old not in s:
    raise SystemExit("startFocus block not found")
s = s.replace(start_old, start_new, 1)
s = s.replace("      plannedMinutes: plannedMinutes,\n      startedAt: focusStartedAt,", "      plannedMinutes: safeMinutes,\n      startedAt: focusStartedAt,", 1)
# Persist after start native call.
needle = """      endAt: focusEndAt,
    );
  }

  Future<void> pauseFocus"""
repl = """      endAt: focusEndAt,
    );
    await _persistFocusFallback();
  }

  Future<void> pauseFocus"""
if needle not in s:
    raise SystemExit("startFocus end anchor not found")
s = s.replace(needle, repl, 1)

# Pause: clamp and persist.
s = s.replace(
    "    focusRemainingSeconds = max(0, remainingSeconds);",
    "    focusRemainingSeconds = remainingSeconds.clamp(0, focusPlannedMinutes * 60).toInt();",
    1,
)
needle = """      remainingSeconds: focusRemainingSeconds,
    );
  }

  Future<void> resumeFocus"""
repl = """      remainingSeconds: focusRemainingSeconds,
    );
    await _persistFocusFallback();
  }

  Future<void> resumeFocus"""
if needle not in s:
    raise SystemExit("pauseFocus end anchor not found")
s = s.replace(needle, repl, 1)

# Resume persist.
resume_anchor = """      endAt: focusEndAt,
    );
  }

  int currentFocusRemainingSeconds"""
resume_repl = """      endAt: focusEndAt,
    );
    await _persistFocusFallback();
  }

  int currentFocusRemainingSeconds"""
if resume_anchor not in s:
    raise SystemExit("resumeFocus end anchor not found")
s = s.replace(resume_anchor, resume_repl, 1)

# Finish/cancel clear fallback.
finish_anchor = """    await NativeBridge.stopFocus();
    await save(syncReminders: false);"""
finish_repl = """    await NativeBridge.stopFocus();
    await _clearFocusFallback();
    await save(syncReminders: false);"""
if finish_anchor not in s:
    raise SystemExit("finishFocus clear anchor not found")
s = s.replace(finish_anchor, finish_repl, 1)
cancel_anchor = """    notifyListeners();
    await NativeBridge.stopFocus();
  }

  Future<void> saveDayReview"""
cancel_repl = """    notifyListeners();
    await NativeBridge.stopFocus();
    await _clearFocusFallback();
  }

  Future<void> saveDayReview"""
if cancel_anchor not in s:
    raise SystemExit("cancelFocus clear anchor not found")
s = s.replace(cancel_anchor, cancel_repl, 1)
wr(state_path, s)

# ---------------------------------------------------------------------------
# Planner: use chronological focus history before selecting last five.
# ---------------------------------------------------------------------------
planner = "lib/services/planner_service.dart"
p = rd(planner)
old = """  static Map<int, double> _recentFocusAverages(List<FocusSession> sessions) {
    final recent = <int, List<int>>{};
    for (final session in sessions) {
"""
new = """  static Map<int, double> _recentFocusAverages(List<FocusSession> sessions) {
    final recent = <int, List<int>>{};
    final ordered = [...sessions]..sort((a, b) => a.startedAt.compareTo(b.startedAt));
    for (final session in ordered) {
"""
if old not in p:
    raise SystemExit("planner history anchor not found")
p = p.replace(old, new, 1)
wr(planner, p)

# ---------------------------------------------------------------------------
# UI consistency fixes.
# ---------------------------------------------------------------------------
calendar = "lib/screens/calendar_page.dart"
c = rd(calendar)
c = c.replace("if (count > 0 || score > 0)", "if (count > 0 || routineCount > 0)", 1)
wr(calendar, c)

today = "lib/screens/today_page.dart"
t = rd(today)
old = """                            final next = state.themeMode == RitmoThemeMode.dark
                                ? RitmoThemeMode.light
                                : RitmoThemeMode.dark;
"""
new = """                            final next = Theme.of(context).brightness == Brightness.dark
                                ? RitmoThemeMode.light
                                : RitmoThemeMode.dark;
"""
if old not in t:
    raise SystemExit("today theme toggle anchor not found")
t = t.replace(old, new, 1)
wr(today, t)

shell = "lib/screens/shell.dart"
sh = rd(shell)
sh = sh.replace(
    """              bottomNavigationBar: _RitmoBottomBar(
                index: _index,
                onSelected: _select,
              ),""",
    """              bottomNavigationBar: _RitmoBottomBar(
                index: _index,
                onSelected: _select,
                reduceMotion: state.reduceMotion,
              ),""",
    1,
)
sh = sh.replace(
    """  final ValueChanged<int> onSelected;
  const _RitmoBottomBar({required this.index, required this.onSelected});""",
    """  final ValueChanged<int> onSelected;
  final bool reduceMotion;
  const _RitmoBottomBar({
    required this.index,
    required this.onSelected,
    required this.reduceMotion,
  });""",
    1,
)
sh = sh.replace("onTap: () => onSelected(0),", "onTap: () => onSelected(0),\n              reduceMotion: reduceMotion,", 1)
sh = sh.replace("onTap: () => onSelected(1),", "onTap: () => onSelected(1),\n              reduceMotion: reduceMotion,", 1)
sh = sh.replace("onTap: () => onSelected(2),", "onTap: () => onSelected(2),\n              reduceMotion: reduceMotion,", 1)
sh = sh.replace("onTap: () => onSelected(3),", "onTap: () => onSelected(3),\n              reduceMotion: reduceMotion,", 1)
sh = sh.replace(
    """  final String label;
  final VoidCallback onTap;
  const _NavButton({
""",
    """  final String label;
  final VoidCallback onTap;
  final bool reduceMotion;
  const _NavButton({
""",
    1,
)
sh = sh.replace(
    """    required this.label,
    required this.onTap,
  });""",
    """    required this.label,
    required this.onTap,
    required this.reduceMotion,
  });""",
    1,
)
# Only nav button section uses these exact durations after class declaration.
nav_pos = sh.index("class _NavButton extends StatelessWidget")
prefix, nav = sh[:nav_pos], sh[nav_pos:]
nav = nav.replace("duration: const Duration(milliseconds: 220),", "duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 220),", 2)
sh = prefix + nav
wr(shell, sh)

# ---------------------------------------------------------------------------
# Native Android data validation and recurring robustness.
# ---------------------------------------------------------------------------
store = "android/app/src/main/java/com/ritmo/mobile/Store.java"
j = rd(store)
parse_anchor = """    public static Date parse(String iso) {
        try { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso); }
        catch (Exception e) { return new Date(); }
    }
"""
parse_new = """    public static Date parse(String iso) {
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            parser.setLenient(false);
            Date parsed = parser.parse(iso);
            return parsed == null ? new Date() : parsed;
        } catch (Exception e) { return new Date(); }
    }

    public static boolean isValidIsoDate(String iso) {
        if (iso == null || !iso.matches("\\\\d{4}-\\\\d{2}-\\\\d{2}")) return false;
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            parser.setLenient(false);
            Date parsed = parser.parse(iso);
            return parsed != null && iso.equals(parser.format(parsed));
        } catch (Exception e) { return false; }
    }
"""
if "public static boolean isValidIsoDate" not in j:
    if parse_anchor not in j:
        raise SystemExit("Store parse anchor not found")
    j = j.replace(parse_anchor, parse_new, 1)
j = j.replace("if (t.date == null || t.date.length() != 10) t.date = today();", "if (!isValidIsoDate(t.date)) t.date = today();")
j = j.replace("if (t.deadline == null || t.deadline.length() != 10) t.deadline = t.date;", "if (!isValidIsoDate(t.deadline) || t.deadline.compareTo(t.date) < 0) t.deadline = t.date;")
j = j.replace("if (r.startDate == null || r.startDate.length() != 10) r.startDate = today();", "if (!isValidIsoDate(r.startDate)) r.startDate = today();")
j = j.replace("guard < 370", "guard < 10000")
wr(store, j)

# ---------------------------------------------------------------------------
# Version + documentation.
# ---------------------------------------------------------------------------
pub = "pubspec.yaml"
ps = rd(pub)
ps = re.sub(r"^version:\s*.*$", "version: 3.4.2+14", ps, flags=re.M)
wr(pub, ps)

settings = "lib/screens/settings_page.dart"
ss = rd(settings).replace("Ritmo 3.4.1", "Ritmo 3.4.2")
wr(settings, ss)

# build.gradle fallback only; workflow values are updated separately by connector.
gradle = "android/app/build.gradle"
g = rd(gradle)
g = re.sub(r'flutterVersionCode = localProperties\.getProperty\("flutter\.versionCode"\) \?: "\d+"',
           'flutterVersionCode = localProperties.getProperty("flutter.versionCode") ?: "14"', g)
g = re.sub(r'flutterVersionName = localProperties\.getProperty\("flutter\.versionName"\) \?: "[^"]+"',
           'flutterVersionName = localProperties.getProperty("flutter.versionName") ?: "3.4.2"', g)
wr(gradle, g)

readme = "README.md"
r = rd(readme).replace("3.4.0", "3.4.2").replace("3.4.1", "3.4.2")
r = r.replace("versionCode: 12", "versionCode: 14").replace("versionCode: 13", "versionCode: 14")
wr(readme, r)

changelog = "CHANGELOG.md"
old_changelog = rd(changelog)
if "## 3.4.2" not in old_changelog:
    body = re.sub(r"^# Changelog\s*", "", old_changelog)
    entry = """# Changelog

## 3.4.2

### Confiabilidade
- Persistência do Modo Foco também no Windows, permitindo recuperar uma sessão após reiniciar o aplicativo.
- Validação mais rígida de datas e metadados locais antes de cálculos, agenda e planejamento.
- Histórico recente de foco do Smart Planner ordenado cronologicamente antes das estimativas.
- Recorrências e streaks antigos suportam períodos significativamente maiores.

### Interface
- Dias que possuem somente hábitos agora aparecem corretamente como ocupados no calendário.
- Alternância rápida de tema respeita a aparência efetivamente exibida quando o modo Sistema está ativo.
- A opção Reduzir animações também desativa as microanimações da navegação inferior.

### Qualidade
- Novos testes de regressão para datas e histórico do Smart Planner.
- Android e Windows validados por análise estática, testes e builds automatizados.

"""
    wr(changelog, entry + body)

# ---------------------------------------------------------------------------
# Regression tests.
# ---------------------------------------------------------------------------
models_test = "test/models_test.dart"
mt = rd(models_test)
if "validates ISO dates strictly" not in mt:
    insert = """

  test('validates ISO dates strictly', () {
    expect(isValidIsoDate('2026-08-18'), isTrue);
    expect(isValidIsoDate('2026-02-30'), isFalse);
    expect(isValidIsoDate('2026-8-18'), isFalse);
    expect(isValidIsoDate('not-a-date'), isFalse);
    expect(isValidIsoDate(null), isFalse);
  });
"""
    mt = mt.rsplit("}\n", 1)[0] + insert + "}\n"
    wr(models_test, mt)

planner_test = "test/planner_service_test.dart"
pt = rd(planner_test)
# Make existing tests deterministic with explicit clocks.
pt = pt.replace("fromDate: '2026-08-18',\n    );", "fromDate: '2026-08-18',\n      now: DateTime(2026, 8, 18, 7),\n    );")
if "uses the five newest focus sessions even when history is unordered" not in pt:
    extra = """

  test('uses the five newest focus sessions even when history is unordered', () {
    final task = TaskItem(
      id: 42,
      title: 'Histórico',
      date: '2026-08-18',
      deadline: '2026-08-18',
      flexible: true,
      minutes: 30,
    );
    final sessions = <FocusSession>[
      for (final started in [100, 90, 80, 70, 60])
        FocusSession(
          id: started,
          taskId: 42,
          title: 'Histórico',
          date: '2026-08-18',
          plannedMinutes: 30,
          actualMinutes: 20,
          startedAt: started,
        ),
      FocusSession(
        id: 1,
        taskId: 42,
        title: 'Histórico antigo',
        date: '2026-08-01',
        plannedMinutes: 30,
        actualMinutes: 100,
        startedAt: 1,
      ),
    ];
    final result = PlannerService.plan(
      RitmoData(tasks: [task], focusSessions: sessions),
      const PlannerSettings(horizonDays: 1),
      fromDate: '2026-08-18',
      now: DateTime(2026, 8, 18, 7),
    );
    expect(result.assignments.single.estimatedMinutes, 26);
  });
"""
    pt = pt.rsplit("}\n", 1)[0] + extra + "}\n"
wr(planner_test, pt)

print("Ritmo 3.4.2 comprehensive polish applied")
