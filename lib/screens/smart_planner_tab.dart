import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../core/app_state.dart';
import '../core/theme.dart';
import '../models/models.dart';
import '../services/planner_service.dart';
import '../widgets/common.dart';

class SmartPlannerTab extends StatelessWidget {
  final AppState state;
  const SmartPlannerTab({super.key, required this.state});

  String _fmt(int minutes) {
    final h = minutes ~/ 60;
    final m = minutes % 60;
    if (h == 0) return '${m}m';
    return m == 0 ? '${h}h' : '${h}h ${m}m';
  }

  PlannerSettings _settings(int days) => PlannerSettings(
        startHour: state.plannerStartHour,
        endHour: state.plannerEndHour,
        capacityMinutes: state.plannerCapacityMinutes,
        includeWeekend: state.plannerIncludeWeekend,
        horizonDays: days,
        breakMinutes: 10,
        useHistory: true,
      );

  Future<void> _preview(BuildContext context, int days) async {
    final result = PlannerService.plan(state.data, _settings(days));
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (context) => _PlannerPreview(state: state, result: result),
    );
  }

  @override
  Widget build(BuildContext context) {
    final flexible = state.data.tasks
        .where((e) =>
            e.status != 'done' &&
            !e.inbox &&
            e.flexible &&
            e.recurrence == 'none')
        .length;
    final dates = List.generate(7, (i) => addDaysIso(state.today, i));

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
      children: [
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                Theme.of(context).colorScheme.primary.withValues(alpha: .18),
                Theme.of(context).colorScheme.primary.withValues(alpha: .04),
              ],
            ),
            borderRadius: BorderRadius.circular(24),
            border: Border.all(
              color: Theme.of(context).colorScheme.primary.withValues(alpha: .12),
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      color: Theme.of(context)
                          .colorScheme
                          .primary
                          .withValues(alpha: .14),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Icon(
                      Icons.auto_awesome_rounded,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ),
                  const SizedBox(width: 13),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Smart Planner 2.0',
                            style: Theme.of(context).textTheme.titleLarge),
                        const SizedBox(height: 4),
                        Text(
                          '$flexible flexíve${flexible == 1 ? 'l' : 'is'} · ${_fmt(state.plannerCapacityMinutes)}/dia',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Text(
                'Prioridade, prazo, duração, energia, período preferido, carga diária, compromissos fixos e histórico de foco entram na decisão.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 14),
              const Wrap(
                spacing: 7,
                runSpacing: 7,
                children: [
                  _PlannerChip(icon: Icons.flag_outlined, label: 'Prazo'),
                  _PlannerChip(
                      icon: Icons.priority_high_rounded, label: 'Prioridade'),
                  _PlannerChip(icon: Icons.timelapse_rounded, label: 'Duração'),
                  _PlannerChip(icon: Icons.bolt_outlined, label: 'Energia'),
                  _PlannerChip(icon: Icons.history_rounded, label: 'Histórico'),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: FilledButton.icon(
                onPressed: flexible == 0 ? null : () => _preview(context, 1),
                icon: const Icon(Icons.today_rounded),
                label: const Text('Planejar meu dia'),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: FilledButton.tonalIcon(
                onPressed: flexible == 0 ? null : () => _preview(context, 7),
                icon: const Icon(Icons.date_range_rounded),
                label: const Text('Planejar 7 dias'),
              ),
            ),
          ],
        ),
        const SizedBox(height: 18),
        const SectionHeader(title: 'Carga dos próximos 7 dias'),
        ...dates.map((date) {
          final value = state.plannedMinutesOn(date);
          final ratio = value / state.plannerCapacityMinutes.clamp(1, 100000);
          final color = ratio > 1
              ? RitmoColors.danger
              : ratio > .8
                  ? RitmoColors.amber
                  : Theme.of(context).colorScheme.primary;
          final label = ratio > 1
              ? 'Sobrecarga'
              : ratio > .8
                  ? 'Cheio'
                  : ratio > .45
                      ? 'Equilibrado'
                      : 'Leve';
          return Padding(
            padding: const EdgeInsets.only(bottom: 9),
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(13),
                child: Row(
                  children: [
                    SizedBox(
                      width: 64,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            DateFormat('EEE', 'pt_BR')
                                .format(parseIso(date))
                                .replaceAll('.', '')
                                .toUpperCase(),
                            style: Theme.of(context)
                                .textTheme
                                .bodySmall
                                ?.copyWith(fontWeight: FontWeight.w900),
                          ),
                          Text(
                            DateFormat('dd/MM').format(parseIso(date)),
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                        ],
                      ),
                    ),
                    Expanded(
                      child: Column(
                        children: [
                          Row(
                            children: [
                              Expanded(
                                child: Text(label,
                                    style:
                                        Theme.of(context).textTheme.bodySmall),
                              ),
                              Text(_fmt(value),
                                  style:
                                      Theme.of(context).textTheme.titleMedium),
                            ],
                          ),
                          const SizedBox(height: 7),
                          ClipRRect(
                            borderRadius: BorderRadius.circular(99),
                            child: LinearProgressIndicator(
                              value: ratio.clamp(0, 1).toDouble(),
                              minHeight: 7,
                              color: color,
                              backgroundColor: color.withValues(alpha: .10),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        }),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: () async {
            final restored = await state.undoPlanner();
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(
                    restored == 0
                        ? 'Não há planejamento para desfazer.'
                        : '$restored tarefa${restored == 1 ? '' : 's'} restaurada${restored == 1 ? '' : 's'}.',
                  ),
                  behavior: SnackBarBehavior.floating,
                ),
              );
            }
          },
          icon: const Icon(Icons.undo_rounded),
          label: const Text('Desfazer último planejamento'),
        ),
        const SizedBox(height: 12),
        Text(
          'Compromissos fixos, recorrências e itens da Inbox não são movidos. Você escolhe quais sugestões quer aplicar.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ],
    );
  }
}

class _PlannerChip extends StatelessWidget {
  final IconData icon;
  final String label;
  const _PlannerChip({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 7),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surfaceContainer,
          borderRadius: BorderRadius.circular(99),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 15),
            const SizedBox(width: 5),
            Text(label, style: Theme.of(context).textTheme.bodySmall),
          ],
        ),
      );
}

class _PlannerPreview extends StatefulWidget {
  final AppState state;
  final PlannerResult result;
  const _PlannerPreview({required this.state, required this.result});

  @override
  State<_PlannerPreview> createState() => _PlannerPreviewState();
}

class _PlannerPreviewState extends State<_PlannerPreview> {
  late final Set<int> _selected = widget.result.assignments
      .where((e) => e.moved)
      .map((e) => e.taskId)
      .toSet();

  @override
  Widget build(BuildContext context) {
    final result = widget.result;
    final moved = result.assignments.where((e) => e.moved).toList();

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
      child: ListView(
        shrinkWrap: true,
        children: [
          Text(
            result.horizonDays == 1
                ? 'Plano inteligente de hoje'
                : 'Prévia inteligente de 7 dias',
            style: Theme.of(context).textTheme.headlineMedium,
          ),
          const SizedBox(height: 5),
          Text(
            '${result.movedTasks} sugestão${result.movedTasks == 1 ? '' : 'ões'} · '
            '${result.warningTasks} alerta${result.warningTasks == 1 ? '' : 's'} · '
            '${result.historyAdjustedTasks} ajuste${result.historyAdjustedTasks == 1 ? '' : 's'} por histórico',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          if (result.insights.isNotEmpty) ...[
            const SizedBox(height: 14),
            ...result.insights.map(
              (text) => Padding(
                padding: const EdgeInsets.only(bottom: 7),
                child: Container(
                  padding: const EdgeInsets.all(11),
                  decoration: BoxDecoration(
                    color: Theme.of(context)
                        .colorScheme
                        .primary
                        .withValues(alpha: .07),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        Icons.lightbulb_outline_rounded,
                        size: 17,
                        color: Theme.of(context).colorScheme.primary,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(text,
                            style: Theme.of(context).textTheme.bodySmall),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
          const SizedBox(height: 10),
          if (moved.isEmpty)
            const EmptyState(
              icon: Icons.check_circle_outline_rounded,
              title: 'Tudo já está bem distribuído',
              message:
                  'O Smart Planner não encontrou mudanças úteis com as regras atuais.',
            )
          else
            ...moved.map((a) {
              final task = widget.state.taskById(a.taskId);
              if (task == null) return const SizedBox.shrink();
              final checked = _selected.contains(a.taskId);
              return Padding(
                padding: const EdgeInsets.only(bottom: 9),
                child: Card(
                  child: CheckboxListTile(
                    value: checked,
                    onChanged: (v) => setState(() {
                      if (v == true) {
                        _selected.add(a.taskId);
                      } else {
                        _selected.remove(a.taskId);
                      }
                    }),
                    controlAffinity: ListTileControlAffinity.leading,
                    secondary: Icon(
                      a.overCapacity
                          ? Icons.warning_amber_rounded
                          : Icons.auto_awesome_rounded,
                      color: a.overCapacity
                          ? RitmoColors.amber
                          : Theme.of(context).colorScheme.primary,
                    ),
                    title: Text(task.title),
                    subtitle: Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${a.oldDate}${a.oldTime.isEmpty ? '' : ' · ${a.oldTime}'} → '
                            '${a.newDate}${a.newTime.isEmpty ? '' : ' · ${a.newTime}'}',
                          ),
                          const SizedBox(height: 4),
                          Text(a.reason),
                          if (a.estimatedMinutes > 0)
                            Text(
                              'Estimativa usada: ${a.estimatedMinutes} min',
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
              );
            }),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: _selected.isEmpty
                ? null
                : () async {
                    final selectedAssignments = result.assignments
                        .where((e) => _selected.contains(e.taskId))
                        .toList();
                    final filtered = PlannerResult(
                      assignments: selectedAssignments,
                      loadMinutes: result.loadMinutes,
                      overloadedDays: result.overloadedDays,
                      insights: result.insights,
                      historyAdjustedTasks: result.historyAdjustedTasks,
                      horizonDays: result.horizonDays,
                    );
                    await widget.state.applyPlanner(filtered);
                    if (context.mounted) Navigator.pop(context);
                  },
            icon: const Icon(Icons.check_rounded),
            label: Text(
              'Aplicar ${_selected.length} selecionada${_selected.length == 1 ? '' : 's'}',
            ),
          ),
        ],
      ),
    );
  }
}
