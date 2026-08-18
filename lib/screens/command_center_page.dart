import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../core/app_state.dart';
import '../models/models.dart';
import '../sheets/editors.dart';
import '../widgets/common.dart';

class CommandCenterPage extends StatefulWidget {
  final AppState state;
  const CommandCenterPage({super.key, required this.state});

  @override
  State<CommandCenterPage> createState() => _CommandCenterPageState();
}

class _CommandCenterPageState extends State<CommandCenterPage>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  final _capture = TextEditingController();
  final _search = TextEditingController();
  String _query = '';

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabs.dispose();
    _capture.dispose();
    _search.dispose();
    super.dispose();
  }

  Future<void> _captureInbox() async {
    final title = _capture.text.trim();
    if (title.isEmpty) return;
    final id = DateTime.now().microsecondsSinceEpoch;
    await widget.state.addOrUpdateTask(
      TaskItem(
        id: id,
        title: title,
        date: '2999-12-31',
        deadline: '2999-12-31',
        priority: 'auto',
        minutes: 30,
        category: 'Pessoal',
        flexible: true,
        inbox: true,
        energy: 'medium',
        preferredPeriod: 'any',
      ),
    );
    _capture.clear();
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Adicionado à Caixa de entrada.'),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  Future<void> _schedule(TaskItem task, String date) async {
    final wasInbox = task.inbox;
    final previousDeadline = task.deadline;
    task.inbox = false;
    task.date = date;
    task.deadline =
        wasInbox ||
            !isValidIsoDate(previousDeadline) ||
            previousDeadline.compareTo(date) < 0
        ? date
        : previousDeadline;
    task.time = '';
    await widget.state.addOrUpdateTask(task);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Central'),
        bottom: TabBar(
          controller: _tabs,
          tabs: const [
            Tab(text: 'Inbox', icon: Icon(Icons.inbox_outlined)),
            Tab(text: 'Buscar', icon: Icon(Icons.search_rounded)),
            Tab(text: 'Timeline', icon: Icon(Icons.view_timeline_outlined)),
          ],
        ),
      ),
      body: AnimatedBuilder(
        animation: widget.state,
        builder: (context, _) => TabBarView(
          controller: _tabs,
          children: [
            _InboxTab(
              state: widget.state,
              capture: _capture,
              onCapture: _captureInbox,
              onSchedule: _schedule,
            ),
            _SearchTab(
              state: widget.state,
              controller: _search,
              query: _query,
              onChanged: (value) => setState(() => _query = value),
            ),
            _TimelineTab(state: widget.state),
          ],
        ),
      ),
    );
  }
}

class _InboxTab extends StatelessWidget {
  final AppState state;
  final TextEditingController capture;
  final Future<void> Function() onCapture;
  final Future<void> Function(TaskItem, String) onSchedule;

  const _InboxTab({
    required this.state,
    required this.capture,
    required this.onCapture,
    required this.onSchedule,
  });

  @override
  Widget build(BuildContext context) {
    final inbox =
        state.data.tasks.where((e) => e.inbox && e.status != 'done').toList()
          ..sort((a, b) => b.id.compareTo(a.id));
    final overdue = state.data.tasks
        .where(
          (e) =>
              !e.inbox &&
              e.status != 'done' &&
              e.date.compareTo(state.today) < 0,
        )
        .toList();
    final todayPending = state
        .tasksOn(state.today)
        .where((e) => e.status != 'done')
        .toList();
    final pendingRoutines = state
        .routinesOn(state.today)
        .where((e) => !e.doneOn(state.today))
        .toList();

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
      children: [
        Text('Captura rápida', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        TextField(
          controller: capture,
          autofocus: false,
          textInputAction: TextInputAction.done,
          onSubmitted: (_) => onCapture(),
          decoration: InputDecoration(
            hintText: 'Digite agora, organize depois…',
            prefixIcon: const Icon(Icons.bolt_rounded),
            suffixIcon: IconButton(
              tooltip: 'Salvar na Inbox',
              onPressed: onCapture,
              icon: const Icon(Icons.add_circle_rounded),
            ),
          ),
        ),
        const SizedBox(height: 18),
        SectionHeader(title: 'Central de atenção'),
        Row(
          children: [
            Expanded(
              child: _AttentionCard(
                icon: Icons.warning_amber_rounded,
                value: '${overdue.length}',
                label: 'atrasadas',
                tone: Theme.of(context).colorScheme.error,
              ),
            ),
            const SizedBox(width: 9),
            Expanded(
              child: _AttentionCard(
                icon: Icons.today_rounded,
                value: '${todayPending.length}',
                label: 'hoje',
                tone: Theme.of(context).colorScheme.primary,
              ),
            ),
            const SizedBox(width: 9),
            Expanded(
              child: _AttentionCard(
                icon: Icons.repeat_rounded,
                value: '${pendingRoutines.length}',
                label: 'hábitos',
                tone: Theme.of(context).colorScheme.tertiary,
              ),
            ),
          ],
        ),
        if (overdue.isNotEmpty) ...[
          const SizedBox(height: 14),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Precisa de decisão',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 8),
                  ...overdue
                      .take(3)
                      .map(
                        (task) => ListTile(
                          contentPadding: EdgeInsets.zero,
                          dense: true,
                          leading: const Icon(Icons.schedule_rounded),
                          title: Text(task.title),
                          subtitle: Text('Estava em ${_dateLabel(task.date)}'),
                          trailing: TextButton(
                            onPressed: () => onSchedule(task, state.today),
                            child: const Text('Hoje'),
                          ),
                        ),
                      ),
                ],
              ),
            ),
          ),
        ],
        const SizedBox(height: 18),
        Padding(
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
        ),
        if (inbox.isEmpty)
          const EmptyState(
            icon: Icons.inbox_rounded,
            title: 'Inbox zerada',
            message:
                'Use a captura rápida para registrar algo sem precisar escolher data, prioridade ou projeto.',
          )
        else
          ...inbox.map(
            (task) => Padding(
              padding: const EdgeInsets.only(bottom: 9),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(12, 10, 8, 10),
                  child: Row(
                    children: [
                      Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          color: Theme.of(
                            context,
                          ).colorScheme.primary.withValues(alpha: .10),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Icon(
                          Icons.inbox_outlined,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                      ),
                      const SizedBox(width: 11),
                      Expanded(
                        child: InkWell(
                          onTap: () =>
                              showTaskEditor(context, state, task: task),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                task.title,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              const SizedBox(height: 2),
                              Text(
                                '${task.minutes} min · ${_priorityLabel(task.effectivePriority(state.today))}',
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                            ],
                          ),
                        ),
                      ),
                      PopupMenuButton<String>(
                        tooltip: 'Organizar',
                        onSelected: (value) async {
                          if (value == 'today')
                            await onSchedule(task, state.today);
                          if (value == 'tomorrow') {
                            await onSchedule(task, addDaysIso(state.today, 1));
                          }
                          if (value == 'edit' && context.mounted) {
                            await showTaskEditor(context, state, task: task);
                          }
                          if (value == 'delete') await state.deleteTask(task);
                        },
                        itemBuilder: (_) => const [
                          PopupMenuItem(
                            value: 'today',
                            child: Text('Planejar para hoje'),
                          ),
                          PopupMenuItem(
                            value: 'tomorrow',
                            child: Text('Planejar para amanhã'),
                          ),
                          PopupMenuItem(
                            value: 'edit',
                            child: Text('Editar detalhes'),
                          ),
                          PopupMenuDivider(),
                          PopupMenuItem(
                            value: 'delete',
                            child: Text('Excluir'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _SearchTab extends StatelessWidget {
  final AppState state;
  final TextEditingController controller;
  final String query;
  final ValueChanged<String> onChanged;

  const _SearchTab({
    required this.state,
    required this.controller,
    required this.query,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final q = query.trim().toLowerCase();
    final projectTitles = {for (final p in state.data.projects) p.id: p.title};
    final tasks = q.isEmpty
        ? <TaskItem>[]
        : state.data.tasks.where((e) {
            final project = projectTitles[e.projectId] ?? 'Sem projeto';
            return '${e.title} ${e.description} ${e.category} $project'
                .toLowerCase()
                .contains(q);
          }).toList();
    final routines = q.isEmpty
        ? <RoutineItem>[]
        : state.data.routines
              .where(
                (e) => '${e.title} ${e.detail} ${e.category}'
                    .toLowerCase()
                    .contains(q),
              )
              .toList();
    final projects = q.isEmpty
        ? <ProjectItem>[]
        : state.data.projects
              .where(
                (e) => '${e.title} ${e.description}'.toLowerCase().contains(q),
              )
              .toList();
    final goals = q.isEmpty
        ? <GoalItem>[]
        : state.data.goals
              .where((e) => e.title.toLowerCase().contains(q))
              .toList();

    final total =
        tasks.length + routines.length + projects.length + goals.length;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
      children: [
        TextField(
          controller: controller,
          autofocus: true,
          onChanged: onChanged,
          decoration: InputDecoration(
            hintText: 'Tarefa, projeto, hábito, meta…',
            prefixIcon: const Icon(Icons.search_rounded),
            suffixIcon: query.isEmpty
                ? null
                : IconButton(
                    onPressed: () {
                      controller.clear();
                      onChanged('');
                    },
                    icon: const Icon(Icons.close_rounded),
                  ),
          ),
        ),
        const SizedBox(height: 16),
        if (q.isEmpty)
          const EmptyState(
            icon: Icons.manage_search_rounded,
            title: 'Busca global',
            message: 'Encontre rapidamente conteúdo de qualquer área do Ritmo.',
          )
        else if (total == 0)
          EmptyState(
            icon: Icons.search_off_rounded,
            title: 'Nada encontrado',
            message: 'Não encontrei resultados para “$query”.',
          )
        else ...[
          Text(
            '$total resultado${total == 1 ? '' : 's'}',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 8),
          ...tasks.map(
            (task) => _SearchTile(
              icon: task.inbox ? Icons.inbox_outlined : Icons.task_alt_rounded,
              title: task.title,
              subtitle: task.inbox
                  ? 'Inbox · ${task.minutes} min'
                  : '${_dateLabel(task.date)}${task.time.isEmpty ? '' : ' · ${task.time}'} · ${task.category}',
              onTap: () => showTaskEditor(context, state, task: task),
            ),
          ),
          ...routines.map(
            (routine) => _SearchTile(
              icon: Icons.repeat_rounded,
              title: routine.title,
              subtitle:
                  'Hábito · ${routine.category}${routine.time.isEmpty ? '' : ' · ${routine.time}'}',
              onTap: () => showRoutineEditor(context, state, routine: routine),
            ),
          ),
          ...projects.map(
            (project) => _SearchTile(
              icon: Icons.folder_outlined,
              title: project.title,
              subtitle:
                  'Projeto · ${state.projectProgress(project)}% concluído',
              onTap: () => showProjectEditor(context, state, project: project),
            ),
          ),
          ...goals.map(
            (goal) => _SearchTile(
              icon: Icons.flag_outlined,
              title: goal.title,
              subtitle: 'Meta · ${goal.progress}%',
              onTap: () => showGoalEditor(context, state, goal: goal),
            ),
          ),
        ],
      ],
    );
  }
}

class _TimelineTab extends StatelessWidget {
  final AppState state;
  const _TimelineTab({required this.state});

  @override
  Widget build(BuildContext context) {
    final items = <_TimelineItem>[];
    for (final task in state.tasksOn(state.today)) {
      if (task.inbox) continue;
      items.add(
        _TimelineItem(
          time: task.time,
          title: task.title,
          detail: task.status == 'done'
              ? 'Concluída · ${task.minutes} min'
              : '${task.category} · ${task.minutes} min',
          icon: task.status == 'done'
              ? Icons.check_rounded
              : Icons.task_alt_rounded,
          done: task.status == 'done',
          onTap: () => showTaskEditor(context, state, task: task),
        ),
      );
    }
    for (final routine in state.routinesOn(state.today)) {
      items.add(
        _TimelineItem(
          time: routine.time,
          title: routine.title,
          detail: routine.doneOn(state.today)
              ? 'Hábito concluído'
              : 'Hábito pendente · ${routine.minutes} min',
          icon: Icons.repeat_rounded,
          done: routine.doneOn(state.today),
          onTap: () => showRoutineEditor(context, state, routine: routine),
        ),
      );
    }
    items.sort((a, b) {
      if (a.time.isEmpty && b.time.isNotEmpty) return 1;
      if (a.time.isNotEmpty && b.time.isEmpty) return -1;
      return a.time.compareTo(b.time);
    });

    final now = TimeOfDay.now();
    final nowMinutes = now.hour * 60 + now.minute;
    final next = items
        .where(
          (e) =>
              !e.done &&
              e.time.isNotEmpty &&
              _timeMinutes(e.time) >= nowMinutes,
        )
        .cast<_TimelineItem?>()
        .firstOrNull;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
      children: [
        Text(
          'Linha do tempo de hoje',
          style: Theme.of(context).textTheme.headlineMedium,
        ),
        const SizedBox(height: 4),
        Text(
          next == null
              ? 'Sem próximo horário definido.'
              : 'Próxima ação: ${next.title} · ${next.time}',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 18),
        if (items.isEmpty)
          const EmptyState(
            icon: Icons.event_available_rounded,
            title: 'Dia livre',
            message: 'Nenhuma tarefa ou hábito está planejado para hoje.',
          )
        else
          ...List.generate(items.length, (index) {
            final item = items[index];
            return _TimelineTile(item: item, last: index == items.length - 1);
          }),
      ],
    );
  }
}

class _AttentionCard extends StatelessWidget {
  final IconData icon;
  final String value;
  final String label;
  final Color tone;
  const _AttentionCard({
    required this.icon,
    required this.value,
    required this.label,
    required this.tone,
  });

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(
      color: tone.withValues(alpha: .08),
      borderRadius: BorderRadius.circular(16),
      border: Border.all(color: tone.withValues(alpha: .14)),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 18, color: tone),
        const SizedBox(height: 8),
        Text(value, style: Theme.of(context).textTheme.titleLarge),
        Text(label, style: Theme.of(context).textTheme.bodySmall),
      ],
    ),
  );
}

class _SearchTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  const _SearchTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) => Card(
    margin: const EdgeInsets.only(bottom: 8),
    child: ListTile(
      leading: Icon(icon, color: Theme.of(context).colorScheme.primary),
      title: Text(title),
      subtitle: Text(subtitle),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: onTap,
    ),
  );
}

class _TimelineItem {
  final String time;
  final String title;
  final String detail;
  final IconData icon;
  final bool done;
  final VoidCallback onTap;

  const _TimelineItem({
    required this.time,
    required this.title,
    required this.detail,
    required this.icon,
    required this.done,
    required this.onTap,
  });
}

class _TimelineTile extends StatelessWidget {
  final _TimelineItem item;
  final bool last;
  const _TimelineTile({required this.item, required this.last});

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 52,
            child: Text(
              item.time.isEmpty ? '—' : item.time,
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w800),
            ),
          ),
          SizedBox(
            width: 24,
            child: Column(
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(
                    color: item.done ? primary.withValues(alpha: .45) : primary,
                    shape: BoxShape.circle,
                  ),
                ),
                if (!last)
                  Expanded(
                    child: Container(
                      width: 2,
                      margin: const EdgeInsets.symmetric(vertical: 3),
                      color: primary.withValues(alpha: .14),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 6),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Card(
                child: ListTile(
                  leading: Icon(item.icon),
                  title: Text(
                    item.title,
                    style: TextStyle(
                      decoration: item.done ? TextDecoration.lineThrough : null,
                    ),
                  ),
                  subtitle: Text(item.detail),
                  onTap: item.onTap,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

int _timeMinutes(String value) {
  final parts = value.split(':');
  if (parts.length != 2) return -1;
  final hour = int.tryParse(parts[0]);
  final minute = int.tryParse(parts[1]);
  if (hour == null || minute == null) return -1;
  return hour * 60 + minute;
}

String _dateLabel(String iso) {
  if (iso == '2999-12-31') return 'Inbox';
  return DateFormat('dd/MM').format(parseIso(iso));
}

String _priorityLabel(String value) => switch (value) {
  'high' => 'prioridade alta',
  'medium' => 'prioridade média',
  _ => 'prioridade baixa',
};

extension _FirstOrNull<E> on Iterable<E> {
  E? get firstOrNull => isEmpty ? null : first;
}
