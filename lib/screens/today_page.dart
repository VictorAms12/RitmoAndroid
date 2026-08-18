import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../core/app_state.dart';
import '../core/theme.dart';
import '../models/models.dart';
import '../sheets/editors.dart';
import '../widgets/common.dart';
import 'focus_page.dart';
import 'command_center_page.dart';

class TodayPage extends StatefulWidget {
  final AppState state;
  final VoidCallback onAdd;
  final VoidCallback onOpenPlanner;
  const TodayPage({
    super.key,
    required this.state,
    required this.onAdd,
    required this.onOpenPlanner,
  });

  @override
  State<TodayPage> createState() => _TodayPageState();
}

class _TodayPageState extends State<TodayPage> {
  String _filter = 'all';

  String _greeting() {
    final h = DateTime.now().hour;
    if (h < 12) return 'Bom dia';
    if (h < 18) return 'Boa tarde';
    return 'Boa noite';
  }

  String _dayPart(TaskItem task) {
    if (task.time.isEmpty) return 'Sem horário';
    final hour = int.tryParse(task.time.split(':').first) ?? 12;
    if (hour < 12) return 'Manhã';
    if (hour < 18) return 'Tarde';
    return 'Noite';
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

  Future<void> _reviewDay() async {
    var mood = 3;
    final note = TextEditingController();
    var move = false;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setLocal) => Padding(
          padding: EdgeInsets.fromLTRB(
            16,
            0,
            16,
            18 + MediaQuery.viewInsetsOf(context).bottom,
          ),
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('Fechamento do dia',
                    style: Theme.of(context).textTheme.headlineMedium),
                const SizedBox(height: 6),
                Text(
                  'Registre como foi hoje e prepare amanhã sem carregar tudo no automático.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 18),
                Text('Como foi seu dia?',
                    style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 8),
                SegmentedButton<int>(
                  segments: const [
                    ButtonSegment(value: 1, label: Text('😣')),
                    ButtonSegment(value: 2, label: Text('😕')),
                    ButtonSegment(value: 3, label: Text('😐')),
                    ButtonSegment(value: 4, label: Text('🙂')),
                    ButtonSegment(value: 5, label: Text('😄')),
                  ],
                  selected: {mood},
                  onSelectionChanged: (v) =>
                      setLocal(() => mood = v.first),
                ),
                const SizedBox(height: 14),
                TextField(
                  controller: note,
                  maxLines: 4,
                  textCapitalization: TextCapitalization.sentences,
                  decoration: const InputDecoration(
                    labelText: 'Reflexão rápida',
                    hintText: 'O que funcionou? O que merece atenção amanhã?',
                  ),
                ),
                const SizedBox(height: 8),
                SwitchListTile.adaptive(
                  value: move,
                  onChanged: (v) => setLocal(() => move = v),
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Levar pendências flexíveis para amanhã'),
                  subtitle: const Text(
                    'Compromissos fixos e tarefas recorrentes não serão movidos.',
                  ),
                ),
                const SizedBox(height: 10),
                FilledButton.icon(
                  onPressed: () async {
                    await widget.state.saveDayReview(
                      mood: mood,
                      note: note.text,
                      moveFlexibleToTomorrow: move,
                    );
                    if (context.mounted) Navigator.pop(context);
                  },
                  icon: const Icon(Icons.nightlight_round),
                  label: const Text('Encerrar dia'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    note.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final today = state.today;
    final allTasks = state.tasksOn(today);
    final visible = allTasks.where((t) {
      if (_filter == 'todo') return t.status != 'done';
      if (_filter == 'done') return t.status == 'done';
      return true;
    }).toList();
    final routines = state.routinesOn(today);
    final dayScore = state.combinedDayScore(today);
    final done = state.doneCountOn(today);
    final focus = state.focusMinutesOn(today);
    final streak = state.bestRoutineStreak();
    final flexible = allTasks.where((e) => e.flexible && e.status != 'done').length;

    final groups = <String, List<TaskItem>>{
      'Manhã': [],
      'Tarde': [],
      'Noite': [],
      'Sem horário': [],
    };
    for (final t in visible) {
      groups[_dayPart(t)]!.add(t);
    }

    final name = state.userName.isEmpty ? '' : ', ${state.userName.split(' ').first}';
    final dateLabel = DateFormat("EEEE, d 'de' MMMM", 'pt_BR')
        .format(DateTime.now())
        .replaceFirstMapped(RegExp(r'^.'), (m) => m[0]!.toUpperCase());

    return RefreshIndicator(
      onRefresh: state.refreshFromNative,
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 18, 16, 110),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('${_greeting()}$name',
                              style: Theme.of(context).textTheme.headlineMedium),
                          const SizedBox(height: 4),
                          Text(dateLabel, style: Theme.of(context).textTheme.bodySmall),
                        ],
                      ),
                    ),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton.filledTonal(
                          tooltip: 'Central',
                          onPressed: () => Navigator.of(context).push(
                            MaterialPageRoute(builder: (_) => CommandCenterPage(state: state)),
                          ),
                          icon: const Icon(Icons.dashboard_customize_outlined),
                        ),
                        const SizedBox(width: 7),
                        IconButton.filledTonal(
                          tooltip: 'Alternar tema',
                          onPressed: () {
                            final next = state.themeMode == RitmoThemeMode.dark
                                ? RitmoThemeMode.light
                                : RitmoThemeMode.dark;
                            state.setTheme(next);
                          },
                          icon: Icon(
                            Theme.of(context).brightness == Brightness.dark
                                ? Icons.light_mode_rounded
                                : Icons.dark_mode_rounded,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                _HeroSummary(
                  progress: dayScore,
                  done: done,
                  total: allTasks.length,
                  focusMinutes: focus,
                  streak: streak,
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: MetricCard(
                        icon: Icons.auto_awesome_rounded,
                        value: '$flexible',
                        label: 'flexíveis hoje',
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: MetricCard(
                        icon: Icons.schedule_rounded,
                        value: _formatMinutes(state.plannedMinutesOn(today)),
                        label: 'planejado',
                        accent: RitmoColors.amber,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: MetricCard(
                        icon: Icons.warning_amber_rounded,
                        value: '${state.overdueCount()}',
                        label: 'atrasadas',
                        accent: RitmoColors.danger,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                Row(
                  children: [
                    Expanded(
                      child: Text('Seu dia',
                          style: Theme.of(context).textTheme.titleLarge),
                    ),
                    FilledButton.tonalIcon(
                      onPressed: widget.onOpenPlanner,
                      icon: const Icon(Icons.auto_awesome_rounded, size: 18),
                      label: const Text('Organizar'),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      _FilterChip(
                        label: 'Todas',
                        selected: _filter == 'all',
                        onTap: () => setState(() => _filter = 'all'),
                      ),
                      _FilterChip(
                        label: 'Pendentes',
                        selected: _filter == 'todo',
                        onTap: () => setState(() => _filter = 'todo'),
                      ),
                      _FilterChip(
                        label: 'Concluídas',
                        selected: _filter == 'done',
                        onTap: () => setState(() => _filter = 'done'),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                if (visible.isEmpty)
                  EmptyState(
                    icon: Icons.spa_rounded,
                    title: 'Nenhuma tarefa para mostrar',
                    message:
                        'Que tal descansar ou criar uma tarefa pequena para manter o ritmo?',
                    actionLabel: 'Adicionar',
                    onAction: widget.onAdd,
                  )
                else
                  ...groups.entries.expand((entry) sync* {
                    if (entry.value.isEmpty) return;
                    yield Padding(
                      padding: const EdgeInsets.fromLTRB(2, 12, 2, 8),
                      child: Text(
                        entry.key.toUpperCase(),
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              fontWeight: FontWeight.w900,
                              letterSpacing: 1,
                            ),
                      ),
                    );
                    for (final task in entry.value) {
                      yield Padding(
                        padding: const EdgeInsets.only(bottom: 9),
                        child: TaskCard(
                          task: task,
                          today: today,
                          reduceMotion: state.reduceMotion,
                          onToggle: () => state.toggleTask(task),
                          onEdit: () => showTaskEditor(context, state, task: task),
                          confirmDelete: () => _confirmDelete(task),
                          onDelete: () => state.deleteTask(task),
                          onFocus: task.status == 'done'
                              ? null
                              : () => openFocusPage(context, state, task: task),
                        ),
                      );
                    }
                  }),
                const SizedBox(height: 12),
                SectionHeader(
                  title: 'Hábitos de hoje',
                  actionLabel: 'Novo hábito',
                  onAction: () => showRoutineEditor(context, state),
                ),
                if (routines.isEmpty)
                  EmptyState(
                    icon: Icons.repeat_rounded,
                    title: 'Sem hábitos programados',
                    message:
                        'Crie rotinas recorrentes para reduzir a quantidade de decisões no dia.',
                    actionLabel: 'Criar hábito',
                    onAction: () => showRoutineEditor(context, state),
                  )
                else
                  ...routines.map(
                    (r) => Padding(
                      padding: const EdgeInsets.only(bottom: 9),
                      child: RoutineCard(
                        routine: r,
                        date: today,
                        reduceMotion: state.reduceMotion,
                        onToggle: () => state.toggleRoutine(r, today),
                        onEdit: () => showRoutineEditor(context, state, routine: r),
                      ),
                    ),
                  ),
                const SizedBox(height: 12),
                SectionHeader(title: 'Foco'),
                _FocusCard(state: state),
                const SizedBox(height: 18),
                _DayCloseCard(
                  reviewed: state.data.dayReviews.any((e) => e.date == today),
                  onTap: _reviewDay,
                ),
              ]),
            ),
          ),
        ],
      ),
    );
  }

  String _formatMinutes(int value) {
    if (value < 60) return '${value}m';
    final h = value ~/ 60;
    final m = value % 60;
    return m == 0 ? '${h}h' : '${h}h ${m}m';
  }
}

class _HeroSummary extends StatelessWidget {
  final int progress;
  final int done;
  final int total;
  final int focusMinutes;
  final int streak;
  const _HeroSummary({
    required this.progress,
    required this.done,
    required this.total,
    required this.focusMinutes,
    required this.streak,
  });

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: Theme.of(context).brightness == Brightness.dark
              ? const [Color(0xFF181A24), Color(0xFF303764)]
              : const [Color(0xFF5965E8), Color(0xFF7768E8)],
        ),
        borderRadius: BorderRadius.circular(26),
        boxShadow: [
          BoxShadow(
            color: primary.withValues(alpha: .12),
            blurRadius: 28,
            offset: const Offset(0, 12),
          ),
        ],
      ),
      child: Row(
        children: [
          ProgressRing(
            percent: progress,
            size: 104,
            stroke: 10,
            centerLabel: 'do dia',
            color: Colors.white,
            trackColor: Colors.white.withValues(alpha: .20),
            textColor: Colors.white,
          ),
          const SizedBox(width: 18),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'RITMO DE HOJE',
                  style: TextStyle(
                    color: Colors.white70,
                    fontSize: 11,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 1.2,
                  ),
                ),
                const SizedBox(height: 7),
                Text(
                  progress >= 80
                      ? 'Excelente consistência.'
                      : progress >= 50
                          ? 'Você está avançando.'
                          : 'Comece pelo essencial.',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        color: Colors.white,
                      ),
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 7,
                  children: [
                    _HeroPill(icon: Icons.task_alt_rounded, text: '$done/$total tarefas'),
                    _HeroPill(
                      icon: Icons.center_focus_strong_rounded,
                      text: '${focusMinutes}m foco',
                    ),
                    _HeroPill(
                      icon: Icons.local_fire_department_rounded,
                      text: '$streak dias',
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _HeroPill extends StatelessWidget {
  final IconData icon;
  final String text;
  const _HeroPill({required this.icon, required this.text});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 6),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: .11),
          borderRadius: BorderRadius.circular(999),
          border: Border.all(color: Colors.white.withValues(alpha: .08)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 14, color: Colors.white),
            const SizedBox(width: 5),
            Text(
              text,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 11,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
      );
}

class _FilterChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  const _FilterChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(right: 7),
        child: ChoiceChip(
          label: Text(label),
          selected: selected,
          onSelected: (_) => onTap(),
        ),
      );
}

class _FocusCard extends StatelessWidget {
  final AppState state;
  const _FocusCard({required this.state});

  @override
  Widget build(BuildContext context) {
    final active = state.focusActive;
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: () => openFocusPage(context, state),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.primary.withValues(alpha: .12),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Icon(
                  active ? Icons.timer_rounded : Icons.center_focus_strong_rounded,
                  color: Theme.of(context).colorScheme.primary,
                ),
              ),
              const SizedBox(width: 13),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      active ? 'Sessão em andamento' : 'Proteja um bloco de foco',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      active
                          ? '${state.focusTitle} · ${state.focusMode}'
                          : '25, 50 minutos ou a duração da própria tarefa.',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              Icon(
                Icons.arrow_forward_rounded,
                color: Theme.of(context).colorScheme.primary,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DayCloseCard extends StatelessWidget {
  final bool reviewed;
  final VoidCallback onTap;
  const _DayCloseCard({required this.reviewed, required this.onTap});

  @override
  Widget build(BuildContext context) => Card(
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(20),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Icon(
                  reviewed ? Icons.check_circle_rounded : Icons.nightlight_round,
                  color: reviewed
                      ? RitmoColors.mint
                      : Theme.of(context).colorScheme.tertiary,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        reviewed ? 'Dia revisado' : 'Fechamento do dia',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 3),
                      Text(
                        reviewed
                            ? 'Você já registrou sua revisão de hoje.'
                            : 'Revise o que aconteceu e prepare amanhã em poucos segundos.',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
                const Icon(Icons.chevron_right_rounded),
              ],
            ),
          ),
        ),
      );
}
