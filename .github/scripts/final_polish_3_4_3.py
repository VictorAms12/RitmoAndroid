from pathlib import Path
import re


def rd(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def wr(path: str, text: str) -> None:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8", newline="\n")


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
# Calendar-date helpers: keep day arithmetic independent from DST/timezone.
# ---------------------------------------------------------------------------
models = "lib/models/models.dart"
text = rd(models)
old = """String addDaysIso(String iso, int days) =>
    isoDate(parseIso(iso).add(Duration(days: days)));
"""
new = """String addDaysIso(String iso, int days) {
  final d = parseIso(iso);
  return isoDate(DateTime(d.year, d.month, d.day + days));
}
"""
if old not in text and new not in text:
    raise SystemExit("models addDaysIso block not found")
text = text.replace(old, new, 1)
old = """int daysBetween(String from, String to) =>
    parseIso(to).difference(parseIso(from)).inDays;
"""
new = """int daysBetween(String from, String to) {
  final a = parseIso(from);
  final b = parseIso(to);
  final au = DateTime.utc(a.year, a.month, a.day);
  final bu = DateTime.utc(b.year, b.month, b.day);
  return bu.difference(au).inDays;
}
"""
if old not in text and new not in text:
    raise SystemExit("models daysBetween block not found")
text = text.replace(old, new, 1)
wr(models, text)


# ---------------------------------------------------------------------------
# Focus lifecycle: recover Android sessions completed while app is background,
# keep paused sessions paused, and record the real completion date.
# ---------------------------------------------------------------------------
state_path = "lib/core/app_state.dart"
s = rd(state_path)
load_old = """    if (focusActive && focusRunning && focusEndAt > 0) {
      focusRemainingSeconds = max(
        0,
        ((focusEndAt - DateTime.now().millisecondsSinceEpoch) / 1000).ceil(),
      );
    }
"""
load_new = """    if (focusActive && focusRunning && focusEndAt > 0) {
      focusRemainingSeconds = max(
        0,
        ((focusEndAt - DateTime.now().millisecondsSinceEpoch) / 1000).ceil(),
      );
    } else if (focusActive && !focusRunning && focusRemainingSeconds > 0) {
      // Paused sessions do not have a live deadline. This also migrates stale
      // Flutter fallback state created before 3.4.3.
      focusEndAt = 0;
    }
"""
if load_old not in s and load_new not in s:
    raise SystemExit("focus load anchor not found")
s = s.replace(load_old, load_new, 1)

recover_start = s.index("  Future<bool> _recoverExpiredFocusIfNeeded() async {")
recover_end = s.index("\n  void _sanitize()", recover_start)
recover_new = """  Future<bool> _recoverExpiredFocusIfNeeded() async {
    if (!focusActive || focusEndAt <= 0) return false;

    final now = DateTime.now().millisecondsSinceEpoch;
    if (focusEndAt > now) {
      if (focusRunning) {
        focusRemainingSeconds = max(0, ((focusEndAt - now) / 1000).ceil());
      }
      return false;
    }

    final completedAt = focusEndAt;
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
        date: isoDate(DateTime.fromMillisecondsSinceEpoch(completedAt)),
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
"""
s = s[:recover_start] + recover_new + s[recover_end:]

pause_old = """    focusRemainingSeconds = remainingSeconds.clamp(0, focusPlannedMinutes * 60).toInt();
    focusRunning = false;
    focusActive = true;
"""
pause_new = """    focusRemainingSeconds = remainingSeconds.clamp(0, focusPlannedMinutes * 60).toInt();
    focusRunning = false;
    focusActive = true;
    focusEndAt = 0;
"""
if pause_old not in s and pause_new not in s:
    raise SystemExit("pause focus anchor not found")
s = s.replace(pause_old, pause_new, 1)
wr(state_path, s)


# ---------------------------------------------------------------------------
# Root rebuild optimization: MaterialApp now reacts only to app-shell state
# (theme/loading/error). Individual main pages observe AppState themselves.
# ---------------------------------------------------------------------------
main_path = "lib/main.dart"
m = rd(main_path)
class_start = m.index("class _RitmoAppState extends State<RitmoApp> with WidgetsBindingObserver {")
class_end = m.index("\nclass _LoadingScreen", class_start)
main_class = """class _RitmoAppState extends State<RitmoApp> with WidgetsBindingObserver {
  late bool _loading;
  String? _errorMessage;
  late RitmoThemeMode _themeMode;

  @override
  void initState() {
    super.initState();
    _captureAppSnapshot();
    widget.state.addListener(_onAppStateChanged);
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didUpdateWidget(covariant RitmoApp oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.state, widget.state)) {
      oldWidget.state.removeListener(_onAppStateChanged);
      _captureAppSnapshot();
      widget.state.addListener(_onAppStateChanged);
    }
  }

  void _captureAppSnapshot() {
    _loading = widget.state.loading;
    _errorMessage = widget.state.errorMessage;
    _themeMode = widget.state.themeMode;
  }

  void _onAppStateChanged() {
    final state = widget.state;
    final needsRebuild = _loading != state.loading ||
        _errorMessage != state.errorMessage ||
        _themeMode != state.themeMode;
    if (!needsRebuild || !mounted) return;
    setState(_captureAppSnapshot);
  }

  @override
  void dispose() {
    widget.state.removeListener(_onAppStateChanged);
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState lifecycleState) {
    if (lifecycleState == AppLifecycleState.resumed) {
      widget.state.refreshFromNative();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    return MaterialApp(
      title: 'Ritmo',
      debugShowCheckedModeBanner: false,
      theme: buildLightTheme(),
      darkTheme: buildDarkTheme(),
      themeMode: state.materialThemeMode,
      home: _loading
          ? const _LoadingScreen()
          : _errorMessage != null
              ? _ErrorScreen(
                  error: _errorMessage!,
                  onRetry: state.initialize,
                )
              : RitmoShell(state: state),
    );
  }
}
"""
m = m[:class_start] + main_class + m[class_end:]
wr(main_path, m)


# ---------------------------------------------------------------------------
# Shell: page-level state listeners + reduce-motion-only shell listener.
# This keeps navigation/UI chrome out of ordinary data rebuilds.
# ---------------------------------------------------------------------------
shell_path = "lib/screens/shell.dart"
sh = rd(shell_path)
head_old = """class _RitmoShellState extends State<RitmoShell> {
  int _index = 0;
  bool _tabTransitioning = false;
  late final PageController _pages = PageController();

  @override
  void dispose() {
    _pages.dispose();
    super.dispose();
  }
"""
head_new = """class _RitmoShellState extends State<RitmoShell> {
  int _index = 0;
  bool _tabTransitioning = false;
  late bool _reduceMotion;
  late final PageController _pages = PageController();

  @override
  void initState() {
    super.initState();
    _reduceMotion = widget.state.reduceMotion;
    widget.state.addListener(_onStateChanged);
  }

  @override
  void didUpdateWidget(covariant RitmoShell oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.state, widget.state)) {
      oldWidget.state.removeListener(_onStateChanged);
      _reduceMotion = widget.state.reduceMotion;
      widget.state.addListener(_onStateChanged);
    }
  }

  void _onStateChanged() {
    final next = widget.state.reduceMotion;
    if (next == _reduceMotion || !mounted) return;
    setState(() => _reduceMotion = next);
  }

  @override
  void dispose() {
    widget.state.removeListener(_onStateChanged);
    _pages.dispose();
    super.dispose();
  }
"""
if head_old not in sh and head_new not in sh:
    raise SystemExit("shell head anchor not found")
sh = sh.replace(head_old, head_new, 1)
sh = sh.replace("if (widget.state.reduceMotion) {", "if (_reduceMotion) {", 1)

pages_old = """    final pages = [
      TodayPage(
        state: state,
        onAdd: _showAddMenu,
        onOpenPlanner: () => _openOrganizer(tab: 0),
      ),
      CalendarPage(state: state),
      ProgressPage(state: state),
      SettingsPage(
        state: state,
        onOpenPlanner: () => _openOrganizer(tab: 0),
      ),
    ];
"""
pages_new = """    final pages = [
      _StatePage(
        state: state,
        builder: (_) => TodayPage(
          state: state,
          onAdd: _showAddMenu,
          onOpenPlanner: () => _openOrganizer(tab: 0),
        ),
      ),
      _StatePage(
        state: state,
        builder: (_) => CalendarPage(state: state),
      ),
      _StatePage(
        state: state,
        builder: (_) => ProgressPage(state: state),
      ),
      _StatePage(
        state: state,
        builder: (_) => SettingsPage(
          state: state,
          onOpenPlanner: () => _openOrganizer(tab: 0),
        ),
      ),
    ];
"""
if pages_old not in sh and pages_new not in sh:
    raise SystemExit("shell pages anchor not found")
sh = sh.replace(pages_old, pages_new, 1)
sh = sh.replace("reduceMotion: state.reduceMotion,", "reduceMotion: _reduceMotion,", 1)

insert_anchor = "\nclass _RitmoBottomBar extends StatelessWidget {"
state_page = """
class _StatePage extends StatelessWidget {
  final AppState state;
  final WidgetBuilder builder;

  const _StatePage({required this.state, required this.builder});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: state,
      builder: (context, _) => builder(context),
    );
  }
}

class _RitmoBottomBar extends StatelessWidget {"""
if "class _StatePage extends StatelessWidget" not in sh:
    if insert_anchor not in sh:
        raise SystemExit("shell state page insert anchor not found")
    sh = sh.replace(insert_anchor, "\n" + state_page, 1)
wr(shell_path, sh)


# ---------------------------------------------------------------------------
# Inbox/attention rescheduling: keep a future deadline instead of shortening it
# when only the planned execution date is changed.
# ---------------------------------------------------------------------------
center = "lib/screens/command_center_page.dart"
replace_once(
    center,
    """  Future<void> _schedule(TaskItem task, String date) async {
    task.inbox = false;
    task.date = date;
    task.deadline = date;
    task.time = '';
    await widget.state.addOrUpdateTask(task);
  }
""",
    """  Future<void> _schedule(TaskItem task, String date) async {
    final wasInbox = task.inbox;
    final previousDeadline = task.deadline;
    task.inbox = false;
    task.date = date;
    task.deadline = wasInbox ||
            !isValidIsoDate(previousDeadline) ||
            previousDeadline.compareTo(date) < 0
        ? date
        : previousDeadline;
    task.time = '';
    await widget.state.addOrUpdateTask(task);
  }
""",
    "preserve future task deadlines",
)


# ---------------------------------------------------------------------------
# Android alarm reliability: reschedule after reboot, clock/timezone changes,
# and package replacement. Avoid an invalid foreground timer spin-loop.
# ---------------------------------------------------------------------------
boot = "android/app/src/main/java/com/ritmo/mobile/BootReceiver.java"
wr(
    boot,
    """package com.ritmo.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        boolean bootLike = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        boolean clockChanged = Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action);
        if (!bootLike && !clockChanged) return;

        try {
            Store store = new Store(context);
            ReminderScheduler.rescheduleAll(context, store);
            RoutineReminderScheduler.rescheduleAll(context, store);
        } catch (Throwable ignored) { }

        if (!bootLike) return;
        try {
            SharedPreferences focus =
                    context.getSharedPreferences("ritmo_focus", Context.MODE_PRIVATE);
            if (focus.getBoolean("active", false)
                    && focus.getBoolean("running", false)
                    && focus.getLong("endAt", 0L) > System.currentTimeMillis()) {
                FocusTimerService.start(context);
            }
        } catch (Throwable ignored) { }
    }
}
""",
)

manifest = "android/app/src/main/AndroidManifest.xml"
replace_once(
    manifest,
    """            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
""",
    """            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
""",
    "reschedule intents",
)

focus_service = "android/app/src/main/java/com/ritmo/mobile/FocusTimerService.java"
replace_once(
    focus_service,
    """            if (!active || !running) {
                stopForegroundCompat(true);
                stopSelf();
                return;
            }

            if (endAt > 0L && endAt <= System.currentTimeMillis()) {
""",
    """            if (!active || !running) {
                stopForegroundCompat(true);
                stopSelf();
                return;
            }

            if (endAt <= 0L) {
                p.edit().putBoolean("running", false).apply();
                stopForegroundCompat(true);
                stopSelf();
                return;
            }

            if (endAt <= System.currentTimeMillis()) {
""",
    "invalid focus deadline guard",
)


# ---------------------------------------------------------------------------
# Tests for date-only semantics.
# ---------------------------------------------------------------------------
test_path = "test/models_test.dart"
t = rd(test_path)
if "date helpers use calendar-day semantics" not in t:
    marker = "\n  test('validates ISO dates strictly', () {"
    extra = """
  test('date helpers use calendar-day semantics', () {
    expect(addDaysIso('2024-02-28', 1), '2024-02-29');
    expect(addDaysIso('2024-02-29', 1), '2024-03-01');
    expect(addDaysIso('2026-12-31', 1), '2027-01-01');
    expect(daysBetween('2026-08-18', '2026-08-25'), 7);
    expect(daysBetween('2026-08-25', '2026-08-18'), -7);
  });
"""
    if marker not in t:
        raise SystemExit("models test insertion anchor not found")
    t = t.replace(marker, extra + marker, 1)
wr(test_path, t)


# ---------------------------------------------------------------------------
# Version/docs for the final sweep.
# ---------------------------------------------------------------------------
pub = rd("pubspec.yaml")
pub = re.sub(r"^version:\s*.*$", "version: 3.4.3+15", pub, flags=re.M)
wr("pubspec.yaml", pub)

gradle = rd("android/app/build.gradle")
gradle = re.sub(
    r'flutterVersionCode = localProperties\.getProperty\("flutter\.versionCode"\) \?: "\d+"',
    'flutterVersionCode = localProperties.getProperty("flutter.versionCode") ?: "15"',
    gradle,
)
gradle = re.sub(
    r'flutterVersionName = localProperties\.getProperty\("flutter\.versionName"\) \?: "[^"]+"',
    'flutterVersionName = localProperties.getProperty("flutter.versionName") ?: "3.4.3"',
    gradle,
)
wr("android/app/build.gradle", gradle)

settings = rd("lib/screens/settings_page.dart").replace("Ritmo 3.4.2", "Ritmo 3.4.3")
wr("lib/screens/settings_page.dart", settings)

readme = rd("README.md").replace("3.4.2", "3.4.3")
readme = readme.replace("versionCode: 14", "versionCode: 15")
wr("README.md", readme)

changelog = rd("CHANGELOG.md")
if "## 3.4.3" not in changelog:
    changelog = changelog.replace(
        "# Changelog\n",
        """# Changelog

## 3.4.3

### Desempenho
- MaterialApp deixou de reconstruir a aplicação inteira a cada alteração de tarefa, hábito ou foco.
- Telas principais agora observam o estado individualmente, reduzindo trabalho de renderização durante navegação e microinterações.

### Correções
- Sessões de foco concluídas em segundo plano no Android são recuperadas e registradas corretamente ao voltar ao app.
- Pausar uma sessão remove o deadline ativo do cronômetro, evitando conclusão indevida após longas pausas.
- Reagendar uma tarefa mantém um prazo futuro existente quando ele continua válido.
- Cálculos de dias usam semântica de calendário, evitando inconsistências causadas por mudança de fuso/DST.
- Alarmes são reagendados após reinício, atualização do app e alterações de hora/fuso no Android.
- Serviço de foco encerra com segurança se encontrar um deadline nativo inválido.

### Qualidade
- Testes adicionais cobrem aritmética de datas.
- Versão 3.4.3 mantém schemaVersion 8 e o novo ícone multiplataforma da linha 3.4.

""",
        1,
    )
wr("CHANGELOG.md", changelog)

print("Ritmo 3.4.3 final polish applied")
