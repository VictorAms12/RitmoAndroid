import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../core/app_state.dart';
import '../core/theme.dart';
import '../models/models.dart';
import '../widgets/common.dart';

class ProgressPage extends StatelessWidget {
  final AppState state;
  const ProgressPage({super.key, required this.state});

  String _duration(int minutes) {
    if (minutes < 60) return '${minutes}m';
    final h = minutes ~/ 60;
    final m = minutes % 60;
    return m == 0 ? '${h}h' : '${h}h ${m}m';
  }

  @override
  Widget build(BuildContext context) {
    final done7 = state.last7Done();
    final focus7 = state.last7Focus();
    final labels = List.generate(7, (i) {
      final d = parseIso(addDaysIso(state.today, i - 6));
      const values = ['SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SÁB', 'DOM'];
      return values[d.weekday - 1];
    });
    final totalDone = state.totalCompletedLast7();
    final focus = state.focusMinutesLast7();
    final overdue = state.overdueCount();
    final streak = state.bestRoutineStreak();
    final categories = state.categoryMinutesLast7().entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    final sessions = [...state.data.focusSessions]
      ..sort((a, b) => b.startedAt.compareTo(a.startedAt));

    return CustomScrollView(
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 18, 16, 110),
          sliver: SliverList(
            delegate: SliverChildListDelegate([
              Text('Progresso', style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 4),
              Text(
                'Veja consistência, execução e tempo realmente focado.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 18),
              GridView.count(
                crossAxisCount: 2,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                mainAxisSpacing: 10,
                crossAxisSpacing: 10,
                childAspectRatio: 1.45,
                children: [
                  MetricCard(
                    icon: Icons.task_alt_rounded,
                    value: '$totalDone',
                    label: 'concluídas · 7 dias',
                  ),
                  MetricCard(
                    icon: Icons.center_focus_strong_rounded,
                    value: _duration(focus),
                    label: 'foco real · 7 dias',
                    accent: RitmoColors.info,
                  ),
                  MetricCard(
                    icon: Icons.local_fire_department_rounded,
                    value: '$streak dias',
                    label: 'melhor sequência',
                    accent: RitmoColors.amber,
                  ),
                  MetricCard(
                    icon: Icons.warning_amber_rounded,
                    value: '$overdue',
                    label: 'tarefas atrasadas',
                    accent: RitmoColors.danger,
                  ),
                ],
              ),
              const SizedBox(height: 20),
              SectionHeader(title: 'Conclusões na semana'),
              Card(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(14, 18, 14, 14),
                  child: WeeklyBars(values: done7, labels: labels),
                ),
              ),
              const SizedBox(height: 18),
              SectionHeader(title: 'Foco na semana'),
              Card(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(14, 18, 14, 14),
                  child: WeeklyBars(
                    values: focus7,
                    labels: labels,
                    color: RitmoColors.info,
                  ),
                ),
              ),
              const SizedBox(height: 18),
              SectionHeader(title: 'Consistência · 30 dias'),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Heatmap30(values: state.last30Scores()),
                      const SizedBox(height: 12),
                      Text(
                        'Quanto mais intenso o verde, maior foi a combinação de tarefas e hábitos concluídos naquele dia.',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 18),
              SectionHeader(title: 'Distribuição por categoria'),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: categories.isEmpty
                      ? Text(
                          'Conclua tarefas para começar a visualizar a distribuição.',
                          style: Theme.of(context).textTheme.bodySmall,
                        )
                      : Column(
                          children: categories.map((entry) {
                            final maxValue = categories.first.value;
                            final factor =
                                maxValue == 0 ? 0.0 : entry.value / maxValue;
                            return Padding(
                              padding: const EdgeInsets.only(bottom: 13),
                              child: Column(
                                children: [
                                  Row(
                                    children: [
                                      Expanded(
                                        child: Text(
                                          entry.key,
                                          style: Theme.of(context)
                                              .textTheme
                                              .titleMedium,
                                        ),
                                      ),
                                      Text(
                                        _duration(entry.value),
                                        style:
                                            Theme.of(context).textTheme.bodySmall,
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 6),
                                  ClipRRect(
                                    borderRadius: BorderRadius.circular(99),
                                    child: LinearProgressIndicator(
                                      value: factor,
                                      minHeight: 7,
                                    ),
                                  ),
                                ],
                              ),
                            );
                          }).toList(),
                        ),
                ),
              ),
              const SizedBox(height: 18),
              SectionHeader(title: 'Histórico de foco'),
              if (sessions.isEmpty)
                const EmptyState(
                  icon: Icons.center_focus_weak_rounded,
                  title: 'Nenhuma sessão registrada',
                  message:
                      'Use o modo foco para comparar tempo planejado e execução real.',
                )
              else
                ...sessions.take(12).map((session) {
                  final date = DateTime.fromMillisecondsSinceEpoch(session.startedAt);
                  final pct = (session.actualMinutes * 100 /
                          session.plannedMinutes.clamp(1, 100000))
                      .round();
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 9),
                    child: Card(
                      child: Padding(
                        padding: const EdgeInsets.all(14),
                        child: Row(
                          children: [
                            Container(
                              width: 42,
                              height: 42,
                              decoration: BoxDecoration(
                                color: RitmoColors.info.withValues(alpha: .12),
                                borderRadius: BorderRadius.circular(13),
                              ),
                              child: const Icon(
                                Icons.center_focus_strong_rounded,
                                color: RitmoColors.info,
                                size: 20,
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    session.title,
                                    style:
                                        Theme.of(context).textTheme.titleMedium,
                                  ),
                                  const SizedBox(height: 3),
                                  Text(
                                    '${DateFormat('dd/MM · HH:mm').format(date)} · ${session.mode}',
                                    style: Theme.of(context).textTheme.bodySmall,
                                  ),
                                ],
                              ),
                            ),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.end,
                              children: [
                                Text(
                                  '${session.actualMinutes} min',
                                  style:
                                      Theme.of(context).textTheme.titleMedium,
                                ),
                                Text(
                                  '$pct% da meta',
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                }),
            ]),
          ),
        ),
      ],
    );
  }
}
