import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/theme.dart';
import '../models/models.dart';

class SectionHeader extends StatelessWidget {
  final String title;
  final String? actionLabel;
  final VoidCallback? onAction;
  const SectionHeader({
    super.key,
    required this.title,
    this.actionLabel,
    this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(2, 8, 2, 10),
      child: Row(
        children: [
          Expanded(child: Text(title, style: Theme.of(context).textTheme.titleLarge)),
          if (actionLabel != null && onAction != null)
            TextButton(onPressed: onAction, child: Text(actionLabel!)),
        ],
      ),
    );
  }
}

class ProgressRing extends StatelessWidget {
  final int percent;
  final double size;
  final double stroke;
  final String? centerLabel;
  final Color? color;
  final Color? trackColor;
  final Color? textColor;
  const ProgressRing({
    super.key,
    required this.percent,
    this.size = 96,
    this.stroke = 9,
    this.centerLabel,
    this.color,
    this.trackColor,
    this.textColor,
  });

  @override
  Widget build(BuildContext context) {
    final value = percent.clamp(0, 100);
    final scheme = Theme.of(context).colorScheme;
    final ringColor = color ?? scheme.primary;
    final ringTrack = trackColor ?? ringColor.withValues(alpha: .14);
    final foreground = textColor ?? scheme.onSurface;
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: value / 100),
      duration: const Duration(milliseconds: 650),
      curve: Curves.easeOutCubic,
      builder: (context, progress, _) => SizedBox(
        width: size,
        height: size,
        child: CustomPaint(
          painter: _RingPainter(
            progress: progress,
            color: ringColor,
            track: ringTrack,
            stroke: stroke,
          ),
          child: Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  '${(progress * 100).round()}%',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        fontSize: size * .22,
                        color: foreground,
                        fontWeight: FontWeight.w900,
                      ),
                ),
                if (centerLabel != null)
                  Text(
                    centerLabel!,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: foreground.withValues(alpha: .82),
                          fontWeight: FontWeight.w700,
                        ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _RingPainter extends CustomPainter {
  final double progress;
  final Color color;
  final Color track;
  final double stroke;
  _RingPainter({
    required this.progress,
    required this.color,
    required this.track,
    required this.stroke,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset(stroke / 2, stroke / 2) &
        Size(size.width - stroke, size.height - stroke);
    final base = Paint()
      ..color = track
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round;
    final active = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(rect, 0, math.pi * 2, false, base);
    canvas.drawArc(
      rect,
      -math.pi / 2,
      math.pi * 2 * progress,
      false,
      active,
    );
  }

  @override
  bool shouldRepaint(covariant _RingPainter oldDelegate) =>
      oldDelegate.progress != progress ||
      oldDelegate.color != color ||
      oldDelegate.track != track;
}

class MetricCard extends StatelessWidget {
  final IconData icon;
  final String value;
  final String label;
  final Color? accent;
  const MetricCard({
    super.key,
    required this.icon,
    required this.value,
    required this.label,
    this.accent,
  });

  @override
  Widget build(BuildContext context) {
    final color = accent ?? Theme.of(context).colorScheme.primary;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: color.withValues(alpha: .12),
                borderRadius: BorderRadius.circular(11),
              ),
              child: Icon(icon, size: 18, color: color),
            ),
            const SizedBox(height: 12),
            Text(value, style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 2),
            Text(label, style: Theme.of(context).textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}

class EmptyState extends StatelessWidget {
  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 30),
        child: Column(
          children: [
            Container(
              width: 62,
              height: 62,
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.primary.withValues(alpha: .10),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Icon(
                icon,
                color: Theme.of(context).colorScheme.primary,
                size: 30,
              ),
            ),
            const SizedBox(height: 16),
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 6),
            Text(
              message,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall,
            ),
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(height: 16),
              FilledButton.tonal(onPressed: onAction, child: Text(actionLabel!)),
            ],
          ],
        ),
      ),
    );
  }
}

class SkeletonCard extends StatefulWidget {
  final double height;
  const SkeletonCard({super.key, this.height = 84});

  @override
  State<SkeletonCard> createState() => _SkeletonCardState();
}

class _SkeletonCardState extends State<SkeletonCard>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 1200))
        ..repeat();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final base = Theme.of(context).colorScheme.onSurface.withValues(alpha: .05);
    final bright = Theme.of(context).colorScheme.onSurface.withValues(alpha: .10);
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        return Container(
          height: widget.height,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            gradient: LinearGradient(
              begin: Alignment(-1.5 + _controller.value * 3, 0),
              end: Alignment(-.5 + _controller.value * 3, 0),
              colors: [base, bright, base],
            ),
          ),
        );
      },
    );
  }
}

Color priorityColor(String value) => switch (value) {
      'high' => RitmoColors.danger,
      'medium' => RitmoColors.amber,
      _ => RitmoColors.mint,
    };

IconData categoryIcon(String category) {
  final c = category.toLowerCase();
  if (c.contains('estud')) return Icons.school_rounded;
  if (c.contains('trabal')) return Icons.work_rounded;
  if (c.contains('saúde') || c.contains('saude')) return Icons.favorite_rounded;
  if (c.contains('financ')) return Icons.account_balance_wallet_rounded;
  if (c.contains('projet')) return Icons.folder_rounded;
  return Icons.person_rounded;
}

class TaskCard extends StatelessWidget {
  final TaskItem task;
  final String today;
  final VoidCallback onToggle;
  final VoidCallback onEdit;
  final Future<bool> Function()? confirmDelete;
  final VoidCallback? onDelete;
  final VoidCallback? onFocus;
  final bool reduceMotion;

  const TaskCard({
    super.key,
    required this.task,
    required this.today,
    required this.onToggle,
    required this.onEdit,
    this.confirmDelete,
    this.onDelete,
    this.onFocus,
    this.reduceMotion = false,
  });

  @override
  Widget build(BuildContext context) {
    final done = task.status == 'done';
    final effective = task.effectivePriority(today);
    final body = AnimatedContainer(
      duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 260),
      curve: Curves.easeOutCubic,
      decoration: BoxDecoration(
        color: done
            ? Theme.of(context).colorScheme.primary.withValues(alpha: .055)
            : Theme.of(context).cardTheme.color,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: done
              ? Theme.of(context).colorScheme.primary.withValues(alpha: .15)
              : Theme.of(context).dividerColor.withValues(alpha: .6),
        ),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onEdit,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 11, 8, 11),
          child: Row(
            children: [
              _AnimatedCheck(done: done, onTap: onToggle, reduceMotion: reduceMotion),
              const SizedBox(width: 12),
              Expanded(
                child: AnimatedOpacity(
                  opacity: done ? .52 : 1,
                  duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 220),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        task.title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleMedium?.copyWith(
                              decoration: done ? TextDecoration.lineThrough : null,
                            ),
                      ),
                      const SizedBox(height: 5),
                      Wrap(
                        spacing: 7,
                        runSpacing: 4,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          if (task.time.isNotEmpty)
                            _MiniMeta(icon: Icons.schedule_rounded, text: task.time),
                          _MiniMeta(
                            icon: categoryIcon(task.category),
                            text: task.category,
                          ),
                          if (task.minutes > 0)
                            _MiniMeta(
                              icon: Icons.timelapse_rounded,
                              text: '${task.minutes} min',
                            ),
                          if (task.flexible)
                            _MiniMeta(
                              icon: Icons.auto_awesome_rounded,
                              text: 'Flexível',
                            ),
                        ],
                      ),
                      if (task.subtasks.isNotEmpty) ...[
                        const SizedBox(height: 7),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(99),
                          child: LinearProgressIndicator(
                            value: task.subtasks.isEmpty
                                ? 0
                                : task.subtasks.where((e) => e.done).length /
                                    task.subtasks.length,
                            minHeight: 4,
                            backgroundColor: Theme.of(context)
                                .colorScheme
                                .onSurface
                                .withValues(alpha: .06),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 6),
              Column(
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: BoxDecoration(
                      color: priorityColor(effective),
                      shape: BoxShape.circle,
                    ),
                  ),
                  if (onFocus != null) ...[
                    const SizedBox(height: 8),
                    IconButton(
                      visualDensity: VisualDensity.compact,
                      tooltip: 'Iniciar foco',
                      onPressed: onFocus,
                      icon: const Icon(Icons.center_focus_strong_rounded, size: 20),
                    ),
                  ],
                ],
              ),
            ],
          ),
        ),
      ),
    );

    if (onDelete == null) return body;
    return Dismissible(
      key: ValueKey('task-${task.id}'),
      direction: DismissDirection.horizontal,
      confirmDismiss: (direction) async {
        if (direction == DismissDirection.startToEnd) {
          onToggle();
          return false;
        }
        return await confirmDelete?.call() ?? true;
      },
      onDismissed: (_) => onDelete?.call(),
      background: _SwipeBackground(
        alignment: Alignment.centerLeft,
        color: RitmoColors.mint,
        icon: Icons.check_rounded,
        label: done ? 'Reabrir' : 'Concluir',
      ),
      secondaryBackground: const _SwipeBackground(
        alignment: Alignment.centerRight,
        color: RitmoColors.danger,
        icon: Icons.delete_outline_rounded,
        label: 'Excluir',
      ),
      child: body,
    );
  }
}

class _SwipeBackground extends StatelessWidget {
  final Alignment alignment;
  final Color color;
  final IconData icon;
  final String label;
  const _SwipeBackground({
    required this.alignment,
    required this.color,
    required this.icon,
    required this.label,
  });

  @override
  Widget build(BuildContext context) => Container(
        decoration: BoxDecoration(
          color: color.withValues(alpha: .18),
          borderRadius: BorderRadius.circular(18),
        ),
        alignment: alignment,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (alignment == Alignment.centerLeft) ...[
              Icon(icon, color: color),
              const SizedBox(width: 8),
            ],
            Text(label, style: TextStyle(color: color, fontWeight: FontWeight.w800)),
            if (alignment == Alignment.centerRight) ...[
              const SizedBox(width: 8),
              Icon(icon, color: color),
            ],
          ],
        ),
      );
}

class _AnimatedCheck extends StatelessWidget {
  final bool done;
  final VoidCallback onTap;
  final bool reduceMotion;
  const _AnimatedCheck({
    required this.done,
    required this.onTap,
    required this.reduceMotion,
  });

  @override
  Widget build(BuildContext context) {
    return InkResponse(
      radius: 26,
      onTap: onTap,
      child: AnimatedContainer(
        duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 240),
        curve: Curves.easeOutBack,
        width: 30,
        height: 30,
        decoration: BoxDecoration(
          color: done ? Theme.of(context).colorScheme.primary : Colors.transparent,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            width: 2,
            color: done
                ? Theme.of(context).colorScheme.primary
                : Theme.of(context).colorScheme.onSurface.withValues(alpha: .22),
          ),
        ),
        child: AnimatedScale(
          duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 220),
          scale: done ? 1 : .5,
          child: done
              ? Icon(
                  Icons.check_rounded,
                  size: 19,
                  color: Theme.of(context).colorScheme.onPrimary,
                )
              : const SizedBox.shrink(),
        ),
      ),
    );
  }
}

class _MiniMeta extends StatelessWidget {
  final IconData icon;
  final String text;
  const _MiniMeta({required this.icon, required this.text});

  @override
  Widget build(BuildContext context) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            icon,
            size: 13,
            color: Theme.of(context).colorScheme.onSurface.withValues(alpha: .48),
          ),
          const SizedBox(width: 3),
          Text(text, style: Theme.of(context).textTheme.bodySmall),
        ],
      );
}

class RoutineCard extends StatelessWidget {
  final RoutineItem routine;
  final String date;
  final VoidCallback onToggle;
  final VoidCallback onEdit;
  final bool reduceMotion;
  const RoutineCard({
    super.key,
    required this.routine,
    required this.date,
    required this.onToggle,
    required this.onEdit,
    this.reduceMotion = false,
  });

  @override
  Widget build(BuildContext context) {
    final done = routine.doneOn(date);
    return AnimatedContainer(
      duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 250),
      decoration: BoxDecoration(
        color: done
            ? Theme.of(context).colorScheme.primary.withValues(alpha: .055)
            : Theme.of(context).cardTheme.color,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Theme.of(context).dividerColor.withValues(alpha: .6)),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onEdit,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              InkResponse(
                onTap: onToggle,
                child: AnimatedContainer(
                  duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 220),
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(
                    color: done
                        ? Theme.of(context).colorScheme.primary
                        : Theme.of(context).colorScheme.primary.withValues(alpha: .10),
                    borderRadius: BorderRadius.circular(13),
                  ),
                  child: Icon(
                    done ? Icons.check_rounded : categoryIcon(routine.category),
                    color: done
                        ? Theme.of(context).colorScheme.onPrimary
                        : Theme.of(context).colorScheme.primary,
                    size: 20,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: AnimatedOpacity(
                  duration: reduceMotion ? Duration.zero : const Duration(milliseconds: 220),
                  opacity: done ? .55 : 1,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        routine.title,
                        style: Theme.of(context).textTheme.titleMedium?.copyWith(
                              decoration: done ? TextDecoration.lineThrough : null,
                            ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        [
                          if (routine.time.isNotEmpty) routine.time,
                          routine.category,
                          if (routine.minutes > 0) '${routine.minutes} min',
                          '🔥 ${routine.streak(date)}',
                        ].join(' · '),
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ),
              Icon(Icons.chevron_right_rounded,
                  color: Theme.of(context).colorScheme.onSurface.withValues(alpha: .35)),
            ],
          ),
        ),
      ),
    );
  }
}

class WeeklyBars extends StatelessWidget {
  final List<int> values;
  final List<String> labels;
  final Color? color;
  final double height;
  const WeeklyBars({
    super.key,
    required this.values,
    required this.labels,
    this.color,
    this.height = 140,
  });

  @override
  Widget build(BuildContext context) {
    final maxValue = values.fold<int>(1, math.max);
    final active = color ?? Theme.of(context).colorScheme.primary;
    return SizedBox(
      height: height,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: List.generate(values.length, (index) {
          final factor = values[index] / maxValue;
          return Expanded(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  Text('${values[index]}',
                      style: Theme.of(context).textTheme.bodySmall),
                  const SizedBox(height: 5),
                  Expanded(
                    child: Align(
                      alignment: Alignment.bottomCenter,
                      child: AnimatedFractionallySizedBox(
                        duration: const Duration(milliseconds: 500),
                        curve: Curves.easeOutCubic,
                        heightFactor: factor.clamp(.04, 1).toDouble(),
                        child: Container(
                          width: 26,
                          decoration: BoxDecoration(
                            color: active.withValues(alpha: .78),
                            borderRadius: BorderRadius.circular(8),
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 7),
                  Text(
                    labels[index],
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                  ),
                ],
              ),
            ),
          );
        }),
      ),
    );
  }
}

class Heatmap30 extends StatelessWidget {
  final List<int> values;
  const Heatmap30({super.key, required this.values});

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    return Wrap(
      spacing: 6,
      runSpacing: 6,
      children: values.map((v) {
        final alpha = v <= 0 ? .055 : (.14 + (v / 100) * .74).clamp(.14, .88).toDouble();
        return Tooltip(
          message: '$v%',
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            width: 25,
            height: 25,
            decoration: BoxDecoration(
              color: primary.withValues(alpha: alpha),
              borderRadius: BorderRadius.circular(7),
            ),
          ),
        );
      }).toList(),
    );
  }
}
