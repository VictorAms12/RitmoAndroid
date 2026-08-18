import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/app_state.dart';
import '../models/models.dart';
import '../sheets/editors.dart';
import 'calendar_page.dart';
import 'focus_page.dart';
import 'organizer_page.dart';
import 'progress_page.dart';
import 'settings_page.dart';
import 'today_page.dart';

class RitmoShell extends StatefulWidget {
  final AppState state;
  const RitmoShell({super.key, required this.state});

  @override
  State<RitmoShell> createState() => _RitmoShellState();
}

class _RitmoShellState extends State<RitmoShell> {
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

  Future<void> _select(int value) async {
    if (value == _index || _tabTransitioning || !_pages.hasClients) return;

    final current = (_pages.page ?? _index.toDouble()).round();

    if (_reduceMotion) {
      setState(() => _index = value);
      _pages.jumpToPage(value);
      return;
    }

    setState(() {
      _index = value;
      _tabTransitioning = true;
    });

    try {
      final distance = (value - current).abs();

      if (distance > 1) {
        final bridgePage = value > current ? value - 1 : value + 1;
        _pages.jumpToPage(bridgePage);
        await WidgetsBinding.instance.endOfFrame;
        if (!mounted || !_pages.hasClients) return;
      }

      await _pages.animateToPage(
        value,
        duration: const Duration(milliseconds: 240),
        curve: Curves.easeOutCubic,
      );
    } finally {
      if (mounted) {
        setState(() => _tabTransitioning = false);
      }
    }
  }

  Future<void> _openOrganizer({int tab = 0}) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => OrganizerPage(state: widget.state, initialTab: tab),
      ),
    );
  }

  Future<void> _showAddMenu() async {
    widget.state.feedback();
    await showModalBottomSheet<void>(
      context: context,
      useSafeArea: true,
      isScrollControlled: true,
      builder: (sheetContext) => _AddMenu(
        state: widget.state,
        onTask: () {
          Navigator.pop(sheetContext);
          showTaskEditor(context, widget.state);
        },
        onCommitment: () {
          Navigator.pop(sheetContext);
          showTaskEditor(context, widget.state, commitment: true);
        },
        onRoutine: () {
          Navigator.pop(sheetContext);
          showRoutineEditor(context, widget.state);
        },
        onProject: () {
          Navigator.pop(sheetContext);
          showProjectEditor(context, widget.state);
        },
        onGoal: () {
          Navigator.pop(sheetContext);
          showGoalEditor(context, widget.state);
        },
        onFocus: () {
          Navigator.pop(sheetContext);
          openFocusPage(context, widget.state);
        },
        onPlanner: () {
          Navigator.pop(sheetContext);
          _openOrganizer(tab: 0);
        },
        onKanban: () {
          Navigator.pop(sheetContext);
          _openOrganizer(tab: 1);
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final pages = [
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

    final dark = Theme.of(context).brightness == Brightness.dark;
    final overlayStyle =
        (dark ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.dark)
            .copyWith(
              statusBarColor: Colors.transparent,
              statusBarIconBrightness: dark
                  ? Brightness.light
                  : Brightness.dark,
              statusBarBrightness: dark ? Brightness.dark : Brightness.light,
              systemNavigationBarColor: dark
                  ? const Color(0xFF111317)
                  : Colors.white,
              systemNavigationBarIconBrightness: dark
                  ? Brightness.light
                  : Brightness.dark,
            );

    final pageView = PageView(
      controller: _pages,
      onPageChanged: (value) {
        if (_tabTransitioning || value == _index) return;
        setState(() => _index = value);
      },
      children: pages,
    );
    final width = MediaQuery.sizeOf(context).width;
    final desktop = width >= 900;

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: overlayStyle,
      child: desktop
          ? Scaffold(
              body: SafeArea(
                child: Row(
                  children: [
                    NavigationRail(
                      selectedIndex: _index,
                      onDestinationSelected: _select,
                      extended: width >= 1180,
                      minWidth: 78,
                      minExtendedWidth: 210,
                      groupAlignment: -.72,
                      leading: Padding(
                        padding: const EdgeInsets.fromLTRB(10, 10, 10, 18),
                        child: FloatingActionButton.small(
                          tooltip: 'Adicionar',
                          onPressed: _showAddMenu,
                          child: const Icon(Icons.add_rounded),
                        ),
                      ),
                      destinations: const [
                        NavigationRailDestination(
                          icon: Icon(Icons.home_outlined),
                          selectedIcon: Icon(Icons.home_rounded),
                          label: Text('Hoje'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.calendar_month_outlined),
                          selectedIcon: Icon(Icons.calendar_month_rounded),
                          label: Text('Agenda'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.insights_outlined),
                          selectedIcon: Icon(Icons.insights_rounded),
                          label: Text('Progresso'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.tune_outlined),
                          selectedIcon: Icon(Icons.tune_rounded),
                          label: Text('Ajustes'),
                        ),
                      ],
                    ),
                    VerticalDivider(
                      width: 1,
                      thickness: 1,
                      color: Theme.of(context).dividerColor,
                    ),
                    Expanded(
                      child: Center(
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 1500),
                          child: pageView,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            )
          : Scaffold(
              extendBody: true,
              body: SafeArea(top: true, bottom: false, child: pageView),
              floatingActionButtonLocation:
                  FloatingActionButtonLocation.centerDocked,
              floatingActionButton: Semantics(
                button: true,
                label: 'Adicionar',
                child: FloatingActionButton(
                  elevation: 7,
                  onPressed: _showAddMenu,
                  child: const Icon(Icons.add_rounded, size: 30),
                ),
              ),
              bottomNavigationBar: _RitmoBottomBar(
                index: _index,
                onSelected: _select,
                reduceMotion: _reduceMotion,
              ),
            ),
    );
  }
}

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

class _RitmoBottomBar extends StatelessWidget {
  final int index;
  final ValueChanged<int> onSelected;
  final bool reduceMotion;
  const _RitmoBottomBar({
    required this.index,
    required this.onSelected,
    required this.reduceMotion,
  });

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return BottomAppBar(
      height: 78,
      elevation: 18,
      color: dark ? const Color(0xFF111317) : Colors.white,
      shadowColor: Colors.black.withValues(alpha: .20),
      shape: const CircularNotchedRectangle(),
      notchMargin: 9,
      padding: const EdgeInsets.fromLTRB(8, 5, 8, 7),
      child: Row(
        children: [
          Expanded(
            child: _NavButton(
              selected: index == 0,
              icon: Icons.home_outlined,
              activeIcon: Icons.home_rounded,
              label: 'Hoje',
              onTap: () => onSelected(0),
              reduceMotion: reduceMotion,
            ),
          ),
          Expanded(
            child: _NavButton(
              selected: index == 1,
              icon: Icons.calendar_month_outlined,
              activeIcon: Icons.calendar_month_rounded,
              label: 'Agenda',
              onTap: () => onSelected(1),
              reduceMotion: reduceMotion,
            ),
          ),
          const SizedBox(width: 64),
          Expanded(
            child: _NavButton(
              selected: index == 2,
              icon: Icons.insights_outlined,
              activeIcon: Icons.insights_rounded,
              label: 'Progresso',
              onTap: () => onSelected(2),
              reduceMotion: reduceMotion,
            ),
          ),
          Expanded(
            child: _NavButton(
              selected: index == 3,
              icon: Icons.tune_outlined,
              activeIcon: Icons.tune_rounded,
              label: 'Ajustes',
              onTap: () => onSelected(3),
              reduceMotion: reduceMotion,
            ),
          ),
        ],
      ),
    );
  }
}

class _NavButton extends StatelessWidget {
  final bool selected;
  final IconData icon;
  final IconData activeIcon;
  final String label;
  final VoidCallback onTap;
  final bool reduceMotion;
  const _NavButton({
    required this.selected,
    required this.icon,
    required this.activeIcon,
    required this.label,
    required this.onTap,
    required this.reduceMotion,
  });

  @override
  Widget build(BuildContext context) {
    final color = selected
        ? Theme.of(context).colorScheme.primary
        : Theme.of(context).colorScheme.onSurface.withValues(alpha: .55);
    return InkResponse(
      onTap: onTap,
      radius: 32,
      child: AnimatedContainer(
        duration: reduceMotion
            ? Duration.zero
            : const Duration(milliseconds: 220),
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            AnimatedScale(
              scale: selected ? 1.08 : 1,
              duration: reduceMotion
                  ? Duration.zero
                  : const Duration(milliseconds: 220),
              child: Icon(selected ? activeIcon : icon, color: color, size: 23),
            ),
            const SizedBox(height: 3),
            Text(
              label,
              style: TextStyle(
                color: color,
                fontSize: 10.5,
                fontWeight: selected ? FontWeight.w800 : FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AddMenu extends StatelessWidget {
  final AppState state;
  final VoidCallback onTask;
  final VoidCallback onCommitment;
  final VoidCallback onRoutine;
  final VoidCallback onProject;
  final VoidCallback onGoal;
  final VoidCallback onFocus;
  final VoidCallback onPlanner;
  final VoidCallback onKanban;

  const _AddMenu({
    required this.state,
    required this.onTask,
    required this.onCommitment,
    required this.onRoutine,
    required this.onProject,
    required this.onGoal,
    required this.onFocus,
    required this.onPlanner,
    required this.onKanban,
  });

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'O que você quer fazer?',
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                    const SizedBox(height: 3),
                    Text(
                      'Crie algo ou entre direto em uma ação rápida.',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: primary.withValues(alpha: .10),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(Icons.bolt_rounded, color: primary),
              ),
            ],
          ),
          const SizedBox(height: 18),
          _PrimaryCreateTile(
            icon: Icons.task_alt_rounded,
            title: 'Nova tarefa',
            subtitle: 'Prazo, duração, projeto, subtarefas e lembrete',
            onTap: onTask,
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: _ActionCard(
                  icon: Icons.event_rounded,
                  title: 'Compromisso',
                  subtitle: 'Horário fixo',
                  onTap: onCommitment,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _ActionCard(
                  icon: Icons.repeat_rounded,
                  title: 'Hábito',
                  subtitle: 'Crie recorrência',
                  onTap: onRoutine,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: _ActionCard(
                  icon: Icons.folder_rounded,
                  title: 'Projeto',
                  subtitle: 'Agrupe tarefas',
                  onTap: onProject,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _ActionCard(
                  icon: Icons.flag_rounded,
                  title: 'Meta',
                  subtitle: 'Acompanhe avanço',
                  onTap: onGoal,
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Text('Ações rápidas', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 9),
          Row(
            children: [
              Expanded(
                child: _QuickButton(
                  icon: Icons.center_focus_strong_rounded,
                  label: state.focusActive ? 'Retomar foco' : 'Modo foco',
                  onTap: onFocus,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _QuickButton(
                  icon: Icons.auto_awesome_rounded,
                  label: 'Planejador',
                  onTap: onPlanner,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _QuickButton(
                  icon: Icons.view_kanban_rounded,
                  label: 'Kanban',
                  onTap: onKanban,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _PrimaryCreateTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  const _PrimaryCreateTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) => Material(
    color: Theme.of(context).colorScheme.primary,
    borderRadius: BorderRadius.circular(20),
    child: InkWell(
      borderRadius: BorderRadius.circular(20),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.all(15),
        child: Row(
          children: [
            Container(
              width: 46,
              height: 46,
              decoration: BoxDecoration(
                color: Theme.of(
                  context,
                ).colorScheme.onPrimary.withValues(alpha: .13),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(icon, color: Theme.of(context).colorScheme.onPrimary),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onPrimary,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Theme.of(
                        context,
                      ).colorScheme.onPrimary.withValues(alpha: .75),
                    ),
                  ),
                ],
              ),
            ),
            Icon(
              Icons.arrow_forward_rounded,
              color: Theme.of(context).colorScheme.onPrimary,
            ),
          ],
        ),
      ),
    ),
  );
}

class _ActionCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  const _ActionCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) => Material(
    color: Theme.of(context).colorScheme.surfaceContainer,
    borderRadius: BorderRadius.circular(18),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(18),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: Theme.of(context).colorScheme.primary),
            const SizedBox(height: 12),
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 2),
            Text(subtitle, style: Theme.of(context).textTheme.bodySmall),
          ],
        ),
      ),
    ),
  );
}

class _QuickButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  const _QuickButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) => OutlinedButton(
    onPressed: onTap,
    style: OutlinedButton.styleFrom(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 11),
    ),
    child: Column(
      children: [
        Icon(icon, size: 20),
        const SizedBox(height: 5),
        Text(
          label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontSize: 11),
        ),
      ],
    ),
  );
}
