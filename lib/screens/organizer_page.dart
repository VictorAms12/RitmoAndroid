import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../core/app_state.dart';
import '../core/theme.dart';
import '../models/models.dart';
import '../services/planner_service.dart';
import '../sheets/editors.dart';
import '../widgets/common.dart';

class OrganizerPage extends StatefulWidget {
  final AppState state;
  final int initialTab;
  const OrganizerPage({super.key, required this.state, this.initialTab = 0});

  @override
  State<OrganizerPage> createState() => _OrganizerPageState();
}

class _OrganizerPageState extends State<OrganizerPage>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 4, vsync: this, initialIndex: widget.initialTab);
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Organizar'),
        bottom: TabBar(
          controller: _tabs,
          isScrollable: true,
          tabs: const [
            Tab(text: 'Planejador'),
            Tab(text: 'Kanban'),
            Tab(text: 'Projetos'),
            Tab(text: 'Metas'),
          ],
        ),
      ),
      body: AnimatedBuilder(
        animation: widget.state,
        builder: (context, _) => TabBarView(
          controller: _tabs,
          children: [
            _PlannerTab(state: widget.state),
            _KanbanTab(state: widget.state),
            _ProjectsTab(state: widget.state),
            _GoalsTab(state: widget.state),
          ],
        ),
      ),
    );
  }
}

class _PlannerTab extends StatelessWidget {
  final AppState state;
  const _PlannerTab({required this.state});

  String _fmt(int minutes) {
    final h = minutes ~/ 60;
    final m = minutes % 60;
    if (h == 0) return '${m}m';
    return m == 0 ? '${h}h' : '${h}h ${m}m';
  }

  Future<void> _preview(BuildContext context) async {
    final result = state.previewPlanner();
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
            e.status != 'done' && e.flexible && e.recurrence == 'none')
        .length;
    final dates = List.generate(7, (i) => addDaysIso(state.today, i));
    final load = <String, int>{
      for (final d in dates) d: state.plannedMinutesOn(d),
    };

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
      children: [
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                Theme.of(context).colorScheme.primary.withValues(alpha: .17),
                Theme.of(context).colorScheme.primary.withValues(alpha: .045),
              ],
            ),
            borderRadius: BorderRadius.circular(24),
          ),
          child: Row(
            children: [
              Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.primary.withValues(alpha: .14),
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
                    Text('Planejador inteligente',
                        style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 4),
                    Text(
                      '$flexible tarefa${flexible == 1 ? '' : 's'} flexíve${flexible == 1 ? 'l' : 'is'} · '
                      '${_fmt(state.plannerCapacityMinutes)} de capacidade/dia',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 18),
        SectionHeader(title: 'Carga dos próximos 7 dias'),
        ...dates.map((date) {
          final value = load[date] ?? 0;
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
                            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                  fontWeight: FontWeight.w900,
                                ),
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
                                child: Text(
                                  label,
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                              ),
                              Text(
                                _fmt(value),
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
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
        const SizedBox(height: 10),
        FilledButton.icon(
          onPressed: flexible == 0 ? null : () => _preview(context),
          icon: const Icon(Icons.auto_awesome_rounded),
          label: const Text('Distribuir semana automaticamente'),
        ),
        const SizedBox(height: 10),
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
        const SizedBox(height: 14),
        Text(
          'O Ritmo move apenas tarefas marcadas como flexíveis e sem recorrência. Compromissos fixos não são alterados.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ],
    );
  }
}

class _PlannerPreview extends StatelessWidget {
  final AppState state;
  final PlannerResult result;
  const _PlannerPreview({required this.state, required this.result});

  @override
  Widget build(BuildContext context) {
    final moved = result.assignments.where((e) => e.moved).toList();
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
      child: ListView(
        shrinkWrap: true,
        children: [
          Text('Prévia do planejamento',
              style: Theme.of(context).textTheme.headlineMedium),
          const SizedBox(height: 5),
          Text(
            '${result.movedTasks} mudança${result.movedTasks == 1 ? '' : 's'} · '
            '${result.overloadedDays} dia${result.overloadedDays == 1 ? '' : 's'} acima da capacidade',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          if (moved.isEmpty)
            const EmptyState(
              icon: Icons.check_circle_outline_rounded,
              title: 'Sua semana já está equilibrada',
              message: 'Nenhuma tarefa precisa ser movida com as regras atuais.',
            )
          else
            ...moved.map((a) {
              final task = state.taskById(a.taskId);
              if (task == null) return const SizedBox.shrink();
              return Padding(
                padding: const EdgeInsets.only(bottom: 9),
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Row(
                      children: [
                        Icon(
                          a.overCapacity
                              ? Icons.warning_amber_rounded
                              : Icons.auto_awesome_rounded,
                          color: a.overCapacity
                              ? RitmoColors.amber
                              : Theme.of(context).colorScheme.primary,
                        ),
                        const SizedBox(width: 11),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(task.title,
                                  style: Theme.of(context).textTheme.titleMedium),
                              const SizedBox(height: 3),
                              Text(
                                '${a.oldDate}${a.oldTime.isEmpty ? '' : ' · ${a.oldTime}'} → '
                                '${a.newDate}${a.newTime.isEmpty ? '' : ' · ${a.newTime}'}',
                                style: Theme.of(context).textTheme.bodySmall,
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
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: moved.isEmpty
                ? null
                : () async {
                    await state.applyPlanner(result);
                    if (context.mounted) Navigator.pop(context);
                  },
            icon: const Icon(Icons.check_rounded),
            label: const Text('Aplicar planejamento'),
          ),
        ],
      ),
    );
  }
}

class _KanbanTab extends StatelessWidget {
  final AppState state;
  const _KanbanTab({required this.state});

  @override
  Widget build(BuildContext context) {
    const columns = [
      ('todo', 'A fazer', Icons.radio_button_unchecked_rounded),
      ('doing', 'Em andamento', Icons.timelapse_rounded),
      ('done', 'Concluído', Icons.check_circle_rounded),
    ];

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(12, 16, 12, 28),
      scrollDirection: Axis.horizontal,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: columns.map((column) {
          final tasks = state.data.tasks.where((e) => e.status == column.$1).toList();
          return Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: DragTarget<TaskItem>(
              onAcceptWithDetails: (details) =>
                  state.setTaskStatus(details.data, column.$1),
              builder: (context, candidates, rejected) => AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                width: 280,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: candidates.isNotEmpty
                      ? Theme.of(context)
                          .colorScheme
                          .primary
                          .withValues(alpha: .08)
                      : Theme.of(context).colorScheme.surfaceContainerLow,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: candidates.isNotEmpty
                        ? Theme.of(context).colorScheme.primary.withValues(alpha: .35)
                        : Theme.of(context).dividerColor,
                  ),
                ),
                child: Column(
                  children: [
                    Row(
                      children: [
                        Icon(column.$3, size: 18),
                        const SizedBox(width: 7),
                        Expanded(
                          child: Text(column.$2,
                              style: Theme.of(context).textTheme.titleMedium),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: Theme.of(context)
                                .colorScheme
                                .onSurface
                                .withValues(alpha: .06),
                            borderRadius: BorderRadius.circular(99),
                          ),
                          child: Text('${tasks.length}'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    if (tasks.isEmpty)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 24),
                        child: Text(
                          'Solte uma tarefa aqui',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      )
                    else
                      ...tasks.map(
                        (task) => Padding(
                          padding: const EdgeInsets.only(bottom: 9),
                          child: LongPressDraggable<TaskItem>(
                            data: task,
                            feedback: Material(
                              color: Colors.transparent,
                              child: SizedBox(
                                width: 260,
                                child: _KanbanCard(task: task, state: state),
                              ),
                            ),
                            childWhenDragging: Opacity(
                              opacity: .35,
                              child: _KanbanCard(task: task, state: state),
                            ),
                            child: _KanbanCard(task: task, state: state),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}

class _KanbanCard extends StatelessWidget {
  final TaskItem task;
  final AppState state;
  const _KanbanCard({required this.task, required this.state});

  @override
  Widget build(BuildContext context) => Card(
        child: InkWell(
          borderRadius: BorderRadius.circular(20),
          onTap: () => showTaskEditor(context, state, task: task),
          child: Padding(
            padding: const EdgeInsets.all(13),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(task.title, style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 7),
                Wrap(
                  spacing: 6,
                  children: [
                    Chip(
                      visualDensity: VisualDensity.compact,
                      label: Text(task.category),
                    ),
                    if (task.flexible)
                      const Chip(
                        visualDensity: VisualDensity.compact,
                        avatar: Icon(Icons.auto_awesome_rounded, size: 14),
                        label: Text('Flexível'),
                      ),
                  ],
                ),
                if (task.time.isNotEmpty || task.minutes > 0) ...[
                  const SizedBox(height: 5),
                  Text(
                    [
                      if (task.time.isNotEmpty) task.time,
                      if (task.minutes > 0) '${task.minutes} min',
                    ].join(' · '),
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ],
            ),
          ),
        ),
      );
}

class _ProjectsTab extends StatelessWidget {
  final AppState state;
  const _ProjectsTab({required this.state});

  @override
  Widget build(BuildContext context) {
    final projects = state.data.projects;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
      children: [
        Row(
          children: [
            Expanded(
              child: Text('Projetos', style: Theme.of(context).textTheme.titleLarge),
            ),
            FilledButton.tonalIcon(
              onPressed: () => showProjectEditor(context, state),
              icon: const Icon(Icons.add_rounded),
              label: const Text('Novo'),
            ),
          ],
        ),
        const SizedBox(height: 12),
        if (projects.isEmpty)
          EmptyState(
            icon: Icons.folder_open_rounded,
            title: 'Nenhum projeto',
            message:
                'Agrupe tarefas que fazem parte do mesmo objetivo e acompanhe o progresso.',
            actionLabel: 'Criar projeto',
            onAction: () => showProjectEditor(context, state),
          )
        else
          ...projects.map((project) {
            final progress = state.projectProgress(project);
            final tasks = state.data.tasks.where((e) => e.projectId == project.id).length;
            return Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Card(
                child: InkWell(
                  borderRadius: BorderRadius.circular(20),
                  onTap: () => showProjectEditor(context, state, project: project),
                  child: Padding(
                    padding: const EdgeInsets.all(15),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              width: 42,
                              height: 42,
                              decoration: BoxDecoration(
                                color: Theme.of(context)
                                    .colorScheme
                                    .primary
                                    .withValues(alpha: .10),
                                borderRadius: BorderRadius.circular(13),
                              ),
                              child: Icon(Icons.folder_rounded,
                                  color: Theme.of(context).colorScheme.primary),
                            ),
                            const SizedBox(width: 11),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(project.title,
                                      style:
                                          Theme.of(context).textTheme.titleMedium),
                                  if (project.description.isNotEmpty)
                                    Text(
                                      project.description,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: Theme.of(context).textTheme.bodySmall,
                                    ),
                                ],
                              ),
                            ),
                            Text('$progress%',
                                style: Theme.of(context).textTheme.titleMedium),
                          ],
                        ),
                        const SizedBox(height: 12),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(99),
                          child: LinearProgressIndicator(
                            value: progress / 100,
                            minHeight: 7,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '$tasks tarefa${tasks == 1 ? '' : 's'} · prazo ${project.targetDate.isEmpty ? 'livre' : project.targetDate}',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          }),
      ],
    );
  }
}

class _GoalsTab extends StatelessWidget {
  final AppState state;
  const _GoalsTab({required this.state});

  @override
  Widget build(BuildContext context) {
    final goals = state.data.goals;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
      children: [
        Row(
          children: [
            Expanded(
              child: Text('Metas', style: Theme.of(context).textTheme.titleLarge),
            ),
            FilledButton.tonalIcon(
              onPressed: () => showGoalEditor(context, state),
              icon: const Icon(Icons.add_rounded),
              label: const Text('Nova'),
            ),
          ],
        ),
        const SizedBox(height: 12),
        if (goals.isEmpty)
          EmptyState(
            icon: Icons.flag_outlined,
            title: 'Nenhuma meta',
            message: 'Defina um resultado e acompanhe o progresso sem complicação.',
            actionLabel: 'Criar meta',
            onAction: () => showGoalEditor(context, state),
          )
        else
          ...goals.map((goal) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Card(
                  child: InkWell(
                    borderRadius: BorderRadius.circular(20),
                    onTap: () => showGoalEditor(context, state, goal: goal),
                    child: Padding(
                      padding: const EdgeInsets.all(15),
                      child: Column(
                        children: [
                          Row(
                            children: [
                              Expanded(
                                child: Text(goal.title,
                                    style:
                                        Theme.of(context).textTheme.titleMedium),
                              ),
                              Text('${goal.progress}%',
                                  style:
                                      Theme.of(context).textTheme.titleMedium),
                            ],
                          ),
                          const SizedBox(height: 10),
                          ClipRRect(
                            borderRadius: BorderRadius.circular(99),
                            child: LinearProgressIndicator(
                              value: goal.progress / 100,
                              minHeight: 8,
                            ),
                          ),
                          if (goal.targetDate.isNotEmpty) ...[
                            const SizedBox(height: 8),
                            Align(
                              alignment: Alignment.centerLeft,
                              child: Text(
                                'Prazo ${goal.targetDate}',
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                ),
              )),
      ],
    );
  }
}
