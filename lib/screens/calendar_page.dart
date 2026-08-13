import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../core/app_state.dart';
import '../models/models.dart';
import '../sheets/editors.dart';
import '../widgets/common.dart';
import 'focus_page.dart';

class CalendarPage extends StatefulWidget {
  final AppState state;
  const CalendarPage({super.key, required this.state});

  @override
  State<CalendarPage> createState() => _CalendarPageState();
}

class _CalendarPageState extends State<CalendarPage> {
  late DateTime _month;
  late String _selected;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _month = DateTime(now.year, now.month);
    _selected = widget.state.today;
  }

  void _moveMonth(int delta) {
    setState(() {
      _month = DateTime(_month.year, _month.month + delta);
    });
  }

  Future<bool> _confirmDelete(TaskItem task) async {
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Excluir tarefa?'),
            content: Text('“${task.title}” será removida.'),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancelar'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Excluir'),
              ),
            ],
          ),
        ) ??
        false;
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final tasks = state.tasksOn(_selected);
    final routines = state.routinesOn(_selected);

    return CustomScrollView(
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 18, 16, 110),
          sliver: SliverList(
            delegate: SliverChildListDelegate([
              Row(
                children: [
                  Expanded(
                    child: Text(
                      'Agenda',
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                  ),
                  IconButton.filledTonal(
                    onPressed: () {
                      final now = DateTime.now();
                      setState(() {
                        _month = DateTime(now.year, now.month);
                        _selected = state.today;
                      });
                    },
                    tooltip: 'Hoje',
                    icon: const Icon(Icons.today_rounded),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Card(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(12, 12, 12, 14),
                  child: Column(
                    children: [
                      Row(
                        children: [
                          IconButton(
                            onPressed: () => _moveMonth(-1),
                            icon: const Icon(Icons.chevron_left_rounded),
                          ),
                          Expanded(
                            child: Text(
                              DateFormat('MMMM yyyy', 'pt_BR')
                                  .format(_month)
                                  .replaceFirstMapped(
                                    RegExp(r'^.'),
                                    (m) => m[0]!.toUpperCase(),
                                  ),
                              textAlign: TextAlign.center,
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                          ),
                          IconButton(
                            onPressed: () => _moveMonth(1),
                            icon: const Icon(Icons.chevron_right_rounded),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      const Row(
                        children: [
                          _WeekLabel('S'),
                          _WeekLabel('T'),
                          _WeekLabel('Q'),
                          _WeekLabel('Q'),
                          _WeekLabel('S'),
                          _WeekLabel('S'),
                          _WeekLabel('D'),
                        ],
                      ),
                      const SizedBox(height: 6),
                      _MonthGrid(
                        month: _month,
                        selected: _selected,
                        state: state,
                        onSelected: (value) => setState(() => _selected = value),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          DateFormat("EEEE, d 'de' MMMM", 'pt_BR')
                              .format(parseIso(_selected))
                              .replaceFirstMapped(
                                RegExp(r'^.'),
                                (m) => m[0]!.toUpperCase(),
                              ),
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        const SizedBox(height: 3),
                        Text(
                          '${tasks.length} tarefa${tasks.length == 1 ? '' : 's'} · '
                          '${routines.length} hábito${routines.length == 1 ? '' : 's'}',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                  IconButton.filled(
                    onPressed: () =>
                        showTaskEditor(context, state, date: _selected),
                    tooltip: 'Nova tarefa neste dia',
                    icon: const Icon(Icons.add_rounded),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              if (tasks.isEmpty && routines.isEmpty)
                EmptyState(
                  icon: Icons.event_available_rounded,
                  title: 'Dia livre',
                  message:
                      'Não há tarefas ou hábitos programados. Você pode manter o espaço livre ou adicionar algo.',
                  actionLabel: 'Adicionar tarefa',
                  onAction: () =>
                      showTaskEditor(context, state, date: _selected),
                )
              else ...[
                if (tasks.isNotEmpty) ...[
                  SectionHeader(title: 'Tarefas'),
                  ...tasks.map(
                    (task) => Padding(
                      padding: const EdgeInsets.only(bottom: 9),
                      child: TaskCard(
                        task: task,
                        today: state.today,
                        reduceMotion: state.reduceMotion,
                        onToggle: () => state.toggleTask(task),
                        onEdit: () =>
                            showTaskEditor(context, state, task: task),
                        confirmDelete: () => _confirmDelete(task),
                        onDelete: () => state.deleteTask(task),
                        onFocus: task.status == 'done'
                            ? null
                            : () =>
                                openFocusPage(context, state, task: task),
                      ),
                    ),
                  ),
                ],
                if (routines.isNotEmpty) ...[
                  SectionHeader(title: 'Hábitos'),
                  ...routines.map(
                    (r) => Padding(
                      padding: const EdgeInsets.only(bottom: 9),
                      child: RoutineCard(
                        routine: r,
                        date: _selected,
                        reduceMotion: state.reduceMotion,
                        onToggle: () => state.toggleRoutine(r, _selected),
                        onEdit: () =>
                            showRoutineEditor(context, state, routine: r),
                      ),
                    ),
                  ),
                ],
              ],
            ]),
          ),
        ),
      ],
    );
  }
}

class _WeekLabel extends StatelessWidget {
  final String label;
  const _WeekLabel(this.label);

  @override
  Widget build(BuildContext context) => Expanded(
        child: Text(
          label,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                fontWeight: FontWeight.w800,
              ),
        ),
      );
}

class _MonthGrid extends StatelessWidget {
  final DateTime month;
  final String selected;
  final AppState state;
  final ValueChanged<String> onSelected;
  const _MonthGrid({
    required this.month,
    required this.selected,
    required this.state,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final first = DateTime(month.year, month.month, 1);
    final days = DateTime(month.year, month.month + 1, 0).day;
    final offset = first.weekday - 1;
    final totalCells = ((offset + days) / 7).ceil() * 7;
    final today = state.today;

    return GridView.builder(
      itemCount: totalCells,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 7,
        mainAxisExtent: 48,
      ),
      itemBuilder: (context, index) {
        final day = index - offset + 1;
        if (day < 1 || day > days) return const SizedBox.shrink();
        final date = isoDate(DateTime(month.year, month.month, day));
        final selectedDay = date == selected;
        final isToday = date == today;
        final count = state.taskCountOn(date);
        final score = state.combinedDayScore(date);

        return Padding(
          padding: const EdgeInsets.all(2),
          child: InkWell(
            borderRadius: BorderRadius.circular(13),
            onTap: () => onSelected(date),
            child: AnimatedContainer(
              duration: state.reduceMotion
                  ? Duration.zero
                  : const Duration(milliseconds: 220),
              decoration: BoxDecoration(
                color: selectedDay
                    ? Theme.of(context).colorScheme.primary
                    : isToday
                        ? Theme.of(context)
                            .colorScheme
                            .primary
                            .withValues(alpha: .10)
                        : Colors.transparent,
                borderRadius: BorderRadius.circular(13),
                border: isToday && !selectedDay
                    ? Border.all(
                        color: Theme.of(context)
                            .colorScheme
                            .primary
                            .withValues(alpha: .35),
                      )
                    : null,
              ),
              child: Stack(
                children: [
                  Center(
                    child: Text(
                      '$day',
                      style: TextStyle(
                        fontWeight: selectedDay || isToday
                            ? FontWeight.w800
                            : FontWeight.w500,
                        color: selectedDay
                            ? Theme.of(context).colorScheme.onPrimary
                            : null,
                      ),
                    ),
                  ),
                  if (count > 0 || score > 0)
                    Positioned(
                      left: 0,
                      right: 0,
                      bottom: 5,
                      child: Center(
                        child: Container(
                          width: 4,
                          height: 4,
                          decoration: BoxDecoration(
                            color: selectedDay
                                ? Theme.of(context).colorScheme.onPrimary
                                : Theme.of(context).colorScheme.primary,
                            shape: BoxShape.circle,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}
