import 'dart:async';
import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/material.dart';

import '../core/app_state.dart';
import '../models/models.dart';

Future<void> openFocusPage(
  BuildContext context,
  AppState state, {
  TaskItem? task,
}) async {
  await Navigator.of(context).push(
    MaterialPageRoute(
      fullscreenDialog: true,
      builder: (_) => FocusPage(state: state, initialTask: task),
    ),
  );
}

class FocusPage extends StatefulWidget {
  final AppState state;
  final TaskItem? initialTask;
  const FocusPage({super.key, required this.state, this.initialTask});

  @override
  State<FocusPage> createState() => _FocusPageState();
}

class _FocusPageState extends State<FocusPage> {
  Timer? _timer;
  TaskItem? _selectedTask;
  int _minutes = 25;
  String _mode = 'Pomodoro 25';
  bool _completeTask = false;
  bool _finishing = false;

  @override
  void initState() {
    super.initState();
    _selectedTask = widget.initialTask;
    if (widget.state.focusActive) {
      _minutes = widget.state.focusPlannedMinutes;
      _mode = widget.state.focusMode;
      if (widget.state.focusRunning) _startTicker();
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _startTicker() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      final remaining = widget.state.currentFocusRemainingSeconds();
      if (widget.state.focusRunning && remaining <= 0 && !_finishing) {
        _finish(auto: true);
      } else {
        setState(() {});
      }
    });
  }

  Future<void> _start() async {
    await widget.state.startFocus(
      task: _selectedTask,
      plannedMinutes: _minutes,
      mode: _mode,
    );
    _startTicker();
    if (mounted) setState(() {});
  }

  Future<void> _finish({bool auto = false}) async {
    if (_finishing) return;
    _finishing = true;
    await widget.state.finishFocus(completeTask: _completeTask);
    _timer?.cancel();
    if (!mounted) return;
    setState(() => _finishing = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(auto ? 'Sessão concluída. Bom trabalho.' : 'Sessão registrada.'),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  String _format(int seconds) {
    final m = seconds ~/ 60;
    final s = seconds % 60;
    return '${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final active = state.focusActive;
    final remaining = active ? state.currentFocusRemainingSeconds() : _minutes * 60;
    final total = math.max(1, state.focusActive ? state.focusPlannedMinutes * 60 : _minutes * 60);
    final progress = (1 - remaining / total).clamp(0.0, 1.0).toDouble();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Modo foco'),
        actions: [
          if (active)
            IconButton(
              tooltip: 'Encerrar sem registrar',
              onPressed: () async {
                final ok = await showDialog<bool>(
                      context: context,
                      builder: (context) => AlertDialog(
                        title: const Text('Sair do foco?'),
                        content: const Text(
                          'A sessão atual será cancelada e não entrará nas estatísticas.',
                        ),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(context, false),
                            child: const Text('Continuar'),
                          ),
                          FilledButton(
                            onPressed: () => Navigator.pop(context, true),
                            child: const Text('Cancelar sessão'),
                          ),
                        ],
                      ),
                    ) ??
                    false;
                if (ok) {
                  await state.cancelFocus();
                  if (context.mounted) Navigator.pop(context);
                }
              },
              icon: const Icon(Icons.close_rounded),
            ),
        ],
      ),
      body: SafeArea(
        top: false,
        child: AnimatedSwitcher(
          duration: state.reduceMotion ? Duration.zero : const Duration(milliseconds: 300),
          child: active
              ? _ActiveFocus(
                  key: const ValueKey('active'),
                  title: state.focusTitle,
                  mode: state.focusMode,
                  remaining: remaining,
                  progress: progress,
                  running: state.focusRunning,
                  completeTask: _completeTask,
                  hasLinkedTask: state.focusTaskId != 0,
                  reduceMotion: state.reduceMotion,
                  onCompleteTaskChanged: (v) => setState(() => _completeTask = v),
                  onPauseResume: () async {
                    if (state.focusRunning) {
                      await state.pauseFocus(remaining);
                      _timer?.cancel();
                    } else {
                      await state.resumeFocus();
                      _startTicker();
                    }
                    if (mounted) setState(() {});
                  },
                  onFinish: () => _finish(),
                )
              : _FocusSetup(
                  key: const ValueKey('setup'),
                  state: state,
                  selectedTask: _selectedTask,
                  minutes: _minutes,
                  mode: _mode,
                  onTaskChanged: (task) => setState(() {
                    _selectedTask = task;
                    if (task == null && _minutes != 25 && _minutes != 50) {
                      _minutes = 25;
                      _mode = 'Pomodoro 25';
                    }
                  }),
                  onPreset: (minutes, mode) => setState(() {
                    _minutes = minutes;
                    _mode = mode;
                  }),
                  onStart: _start,
                ),
        ),
      ),
    );
  }
}

class _FocusSetup extends StatelessWidget {
  final AppState state;
  final TaskItem? selectedTask;
  final int minutes;
  final String mode;
  final ValueChanged<TaskItem?> onTaskChanged;
  final void Function(int minutes, String mode) onPreset;
  final VoidCallback onStart;

  const _FocusSetup({
    super.key,
    required this.state,
    required this.selectedTask,
    required this.minutes,
    required this.mode,
    required this.onTaskChanged,
    required this.onPreset,
    required this.onStart,
  });

  @override
  Widget build(BuildContext context) {
    final openTasks = state.data.tasks.where((e) => e.status != 'done').toList()
      ..sort((a, b) => a.date.compareTo(b.date));
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
      children: [
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                Theme.of(context).colorScheme.primary.withValues(alpha: .18),
                Theme.of(context).colorScheme.primary.withValues(alpha: .055),
              ],
            ),
            borderRadius: BorderRadius.circular(24),
          ),
          child: Column(
            children: [
              Icon(
                Icons.center_focus_strong_rounded,
                size: 42,
                color: Theme.of(context).colorScheme.primary,
              ),
              const SizedBox(height: 12),
              Text(
                'Uma coisa por vez.',
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 6),
              Text(
                'Escolha uma tarefa e proteja este bloco de tempo.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ),
        const SizedBox(height: 18),
        DropdownButtonFormField<int>(
          initialValue: selectedTask?.id ?? 0,
          decoration: const InputDecoration(
            labelText: 'Tarefa em foco',
            prefixIcon: Icon(Icons.task_alt_rounded),
          ),
          items: [
            const DropdownMenuItem(value: 0, child: Text('Foco livre')),
            ...openTasks.map(
              (t) => DropdownMenuItem(
                value: t.id,
                child: Text(t.title, overflow: TextOverflow.ellipsis),
              ),
            ),
          ],
          onChanged: (id) {
            if (id == null || id == 0) {
              onTaskChanged(null);
            } else {
              onTaskChanged(openTasks.firstWhere((e) => e.id == id));
            }
          },
        ),
        const SizedBox(height: 18),
        Text('Duração', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 10),
        SegmentedButton<int>(
          segments: [
            const ButtonSegment(value: 25, label: Text('25 min')),
            const ButtonSegment(value: 50, label: Text('50 min')),
            if (selectedTask != null &&
                selectedTask!.minutes > 0 &&
                selectedTask!.minutes != 25 &&
                selectedTask!.minutes != 50)
              ButtonSegment(
                value: selectedTask!.minutes,
                label: Text('${selectedTask!.minutes} min'),
              ),
          ],
          selected: {
            if (minutes == 25 ||
                minutes == 50 ||
                (selectedTask != null && minutes == selectedTask!.minutes))
              minutes
            else
              25,
          },
          onSelectionChanged: (values) {
            final value = values.first;
            final label = value == 25
                ? 'Pomodoro 25'
                : value == 50
                    ? 'Foco 50'
                    : 'Duração da tarefa';
            onPreset(value, label);
          },
        ),
        const SizedBox(height: 28),
        FilledButton.icon(
          onPressed: onStart,
          icon: const Icon(Icons.play_arrow_rounded),
          label: const Text('Iniciar foco'),
        ),
      ],
    );
  }
}

class _ActiveFocus extends StatelessWidget {
  final String title;
  final String mode;
  final int remaining;
  final double progress;
  final bool running;
  final bool completeTask;
  final bool hasLinkedTask;
  final bool reduceMotion;
  final ValueChanged<bool> onCompleteTaskChanged;
  final VoidCallback onPauseResume;
  final VoidCallback onFinish;

  const _ActiveFocus({
    super.key,
    required this.title,
    required this.mode,
    required this.remaining,
    required this.progress,
    required this.running,
    required this.completeTask,
    required this.hasLinkedTask,
    required this.reduceMotion,
    required this.onCompleteTaskChanged,
    required this.onPauseResume,
    required this.onFinish,
  });

  String _format(int seconds) =>
      '${(seconds ~/ 60).toString().padLeft(2, '0')}:${(seconds % 60).toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 30),
      children: [
        Text(
          mode.toUpperCase(),
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                letterSpacing: 1.2,
                color: primary,
                fontWeight: FontWeight.w800,
              ),
        ),
        const SizedBox(height: 7),
        Text(
          title,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.headlineMedium,
        ),
        const SizedBox(height: 32),
        Center(
          child: TweenAnimationBuilder<double>(
            tween: Tween(begin: 0, end: progress),
            duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 240),
            curve: Curves.easeOutCubic,
            builder: (context, value, _) {
              return SizedBox(
                width: 250,
                height: 250,
                child: CustomPaint(
                  painter: _FocusRingPainter(
                    value: value,
                    color: primary,
                    track: primary.withValues(alpha: .10),
                  ),
                  child: Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          _format(remaining),
                          style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                                fontSize: 48,
                                fontFeatures: const [FontFeature.tabularFigures()],
                              ),
                        ),
                        const SizedBox(height: 5),
                        Text(
                          running ? 'Foco ativo' : 'Pausado',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        ),
        const SizedBox(height: 30),
        FilledButton.icon(
          onPressed: onPauseResume,
          icon: Icon(running ? Icons.pause_rounded : Icons.play_arrow_rounded),
          label: Text(running ? 'Pausar sessão' : 'Continuar sessão'),
        ),
        const SizedBox(height: 10),
        OutlinedButton.icon(
          onPressed: onFinish,
          icon: const Icon(Icons.stop_rounded),
          label: const Text('Finalizar e registrar'),
        ),
        if (hasLinkedTask) ...[
          const SizedBox(height: 8),
          CheckboxListTile(
            value: completeTask,
            onChanged: (v) => onCompleteTaskChanged(v ?? false),
            title: const Text('Concluir tarefa ao finalizar'),
            contentPadding: EdgeInsets.zero,
            controlAffinity: ListTileControlAffinity.leading,
          ),
        ],
      ],
    );
  }
}

class _FocusRingPainter extends CustomPainter {
  final double value;
  final Color color;
  final Color track;
  const _FocusRingPainter({
    required this.value,
    required this.color,
    required this.track,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final rect = const Offset(10, 10) & Size(size.width - 20, size.height - 20);
    final base = Paint()
      ..color = track
      ..style = PaintingStyle.stroke
      ..strokeWidth = 14
      ..strokeCap = StrokeCap.round;
    final active = Paint()
      ..shader = SweepGradient(
        colors: [color.withValues(alpha: .55), color],
      ).createShader(rect)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 14
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(rect, 0, math.pi * 2, false, base);
    canvas.drawArc(rect, -math.pi / 2, math.pi * 2 * value, false, active);
  }

  @override
  bool shouldRepaint(covariant _FocusRingPainter oldDelegate) =>
      oldDelegate.value != value ||
      oldDelegate.color != color ||
      oldDelegate.track != track;
}
