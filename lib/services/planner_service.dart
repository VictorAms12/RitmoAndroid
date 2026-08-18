import 'dart:math';

import '../models/models.dart';

class PlannerSettings {
  final int startHour;
  final int endHour;
  final int capacityMinutes;
  final bool includeWeekend;
  final int horizonDays;
  final int breakMinutes;
  final bool useHistory;

  const PlannerSettings({
    this.startHour = 8,
    this.endHour = 22,
    this.capacityMinutes = 360,
    this.includeWeekend = true,
    this.horizonDays = 7,
    this.breakMinutes = 10,
    this.useHistory = true,
  });
}

class PlannerAssignment {
  final int taskId;
  final String oldDate;
  final String oldTime;
  final String newDate;
  final String newTime;
  final bool overCapacity;
  final String reason;
  final int estimatedMinutes;
  final double score;

  const PlannerAssignment({
    required this.taskId,
    required this.oldDate,
    required this.oldTime,
    required this.newDate,
    required this.newTime,
    required this.overCapacity,
    this.reason = '',
    this.estimatedMinutes = 0,
    this.score = 0,
  });

  bool get moved => oldDate != newDate || oldTime != newTime;
}

class PlannerResult {
  final List<PlannerAssignment> assignments;
  final Map<String, int> loadMinutes;
  final int overloadedDays;
  final List<String> insights;
  final int historyAdjustedTasks;
  final int horizonDays;

  const PlannerResult({
    required this.assignments,
    required this.loadMinutes,
    required this.overloadedDays,
    this.insights = const [],
    this.historyAdjustedTasks = 0,
    this.horizonDays = 7,
  });

  int get movedTasks => assignments.where((e) => e.moved).length;
  int get warningTasks => assignments.where((e) => e.overCapacity).length;
}

class _Interval {
  final int start;
  final int end;
  const _Interval(this.start, this.end);
}

class _Candidate {
  final String date;
  final String time;
  final double score;
  final bool fits;
  final String reason;

  const _Candidate({
    required this.date,
    required this.time,
    required this.score,
    required this.fits,
    required this.reason,
  });
}

class PlannerService {
  static PlannerResult plan(
    RitmoData data,
    PlannerSettings settings, {
    String? fromDate,
    DateTime? now,
  }) {
    final clock = now ?? DateTime.now();
    final currentDate = isoDate(clock);
    final currentMinute = ((clock.hour * 60 + clock.minute + 4) ~/ 5) * 5;
    final start = fromDate ?? currentDate;
    final days = settings.horizonDays.clamp(1, 14).toInt();
    final horizon = addDaysIso(start, days - 1);
    final load = <String, int>{};
    final occupied = <String, List<_Interval>>{};
    final focusHistory = settings.useHistory
        ? _recentFocusAverages(data.focusSessions)
        : const <int, double>{};

    for (var i = 0; i < days; i++) {
      final date = addDaysIso(start, i);
      load[date] = 0;
      occupied[date] = [];

      for (final routine in data.routines) {
        if (!routine.dueOn(date)) continue;
        // A habit that was already completed still consumed part of that day's
        // available capacity and must remain represented in the plan.
        load[date] = (load[date] ?? 0) + max(0, routine.minutes);
        if (routine.time.isNotEmpty) {
          _addOccupied(
            occupied[date]!,
            routine.time,
            max(15, routine.minutes),
            settings.breakMinutes,
          );
        }
      }
    }

    final eligible = <TaskItem>[];
    for (final task in data.tasks) {
      if (task.inbox) continue;

      if (task.status == 'done') {
        if (task.date.compareTo(start) >= 0 &&
            task.date.compareTo(horizon) <= 0) {
          final mins = max(0, task.minutes);
          load[task.date] = (load[task.date] ?? 0) + mins;
          if (task.time.isNotEmpty) {
            _addOccupied(
              occupied[task.date]!,
              task.time,
              max(15, mins),
              settings.breakMinutes,
            );
          }
        }
        continue;
      }

      final canMove = task.flexible && task.recurrence == 'none';
      final due = task.deadline.length == 10 ? task.deadline : task.date;
      final relevant =
          task.date.compareTo(horizon) <= 0 || due.compareTo(horizon) <= 0;

      if (canMove && relevant) {
        eligible.add(task);
        continue;
      }

      if (task.date.compareTo(start) < 0 || task.date.compareTo(horizon) > 0) {
        continue;
      }
      final mins = max(0, task.minutes);
      load[task.date] = (load[task.date] ?? 0) + mins;
      if (task.time.isNotEmpty) {
        _addOccupied(
          occupied[task.date]!,
          task.time,
          max(15, mins),
          settings.breakMinutes,
        );
      }
    }

    int priorityWeight(TaskItem task) =>
        switch (task.effectivePriority(start)) {
          'high' => 3,
          'medium' => 2,
          _ => 1,
        };

    eligible.sort((a, b) {
      final byPriority = priorityWeight(b).compareTo(priorityWeight(a));
      if (byPriority != 0) return byPriority;
      final byDue = a.deadline.compareTo(b.deadline);
      if (byDue != 0) return byDue;
      return b.minutes.compareTo(a.minutes);
    });

    final assignments = <PlannerAssignment>[];
    var historyAdjusted = 0;

    for (final task in eligible) {
      final estimate = _estimateMinutes(task, focusHistory);
      if (estimate != max(15, task.minutes)) historyAdjusted++;

      var deadline = task.deadline.length == 10 ? task.deadline : task.date;
      if (deadline.compareTo(start) < 0) deadline = start;
      if (deadline.compareTo(horizon) > 0) deadline = horizon;

      _Candidate? best;
      for (var i = 0; i < days; i++) {
        final date = addDaysIso(start, i);
        if (date.compareTo(deadline) > 0) break;
        if (!settings.includeWeekend && _isWeekend(date)) continue;

        final candidate = _candidateFor(
          task: task,
          date: date,
          dayIndex: i,
          estimatedMinutes: estimate,
          currentLoad: load[date] ?? 0,
          occupied: occupied[date]!,
          settings: settings,
          startDate: start,
          minimumStartMinute: date == currentDate
              ? currentMinute
              : settings.startHour * 60,
        );

        if (best == null ||
            (candidate.fits && !best.fits) ||
            (candidate.fits == best.fits && candidate.score < best.score)) {
          best = candidate;
        }
      }

      if (best == null) {
        for (var i = 0; i < days; i++) {
          final date = addDaysIso(start, i);
          if (!settings.includeWeekend && _isWeekend(date)) continue;
          final candidate = _candidateFor(
            task: task,
            date: date,
            dayIndex: i,
            estimatedMinutes: estimate,
            currentLoad: load[date] ?? 0,
            occupied: occupied[date]!,
            settings: settings,
            startDate: start,
            minimumStartMinute: date == currentDate
                ? currentMinute
                : settings.startHour * 60,
          );
          if (best == null || candidate.score < best.score) best = candidate;
        }
      }

      best ??= _Candidate(
        date: start,
        time: '',
        score: 99999,
        fits: false,
        reason:
            'Não encontrei uma janela livre dentro das preferências atuais.',
      );

      final originalDeadline = task.deadline.length == 10
          ? task.deadline
          : task.date;
      final missesDeadline = best.date.compareTo(originalDeadline) > 0;
      final over =
          missesDeadline ||
          !best.fits ||
          (load[best.date] ?? 0) + estimate > settings.capacityMinutes ||
          best.time.isEmpty;
      final assignmentReason = missesDeadline
          ? '${best.reason} Prazo ultrapassado; revise o prazo ou a carga.'
          : best.reason;

      assignments.add(
        PlannerAssignment(
          taskId: task.id,
          oldDate: task.date,
          oldTime: task.time,
          newDate: best.date,
          newTime: best.time,
          overCapacity: over,
          reason: assignmentReason,
          estimatedMinutes: estimate,
          score: best.score,
        ),
      );

      load[best.date] = (load[best.date] ?? 0) + estimate;
      if (best.time.isNotEmpty) {
        _addOccupied(
          occupied[best.date]!,
          best.time,
          estimate,
          settings.breakMinutes,
        );
      }
    }

    final overloaded = load.values
        .where((e) => e > settings.capacityMinutes)
        .length;
    final insights = <String>[];
    if (assignments.isEmpty) {
      insights.add(
        'Nenhuma tarefa flexível precisa ser redistribuída neste período.',
      );
    } else {
      final warnings = assignments.where((e) => e.overCapacity).length;
      if (warnings == 0) {
        insights.add(
          'Todas as tarefas flexíveis encontraram uma janela dentro da capacidade configurada.',
        );
      } else {
        insights.add(
          '$warnings tarefa${warnings == 1 ? '' : 's'} ainda exige${warnings == 1 ? '' : 'm'} atenção por falta de espaço livre ou prazo.',
        );
      }
      if (historyAdjusted > 0) {
        insights.add(
          '$historyAdjusted estimativa${historyAdjusted == 1 ? '' : 's'} de duração ajustada${historyAdjusted == 1 ? '' : 's'} usando sessões de foco anteriores.',
        );
      }
      if (overloaded > 0) {
        insights.add(
          '$overloaded dia${overloaded == 1 ? '' : 's'} ultrapassa${overloaded == 1 ? '' : 'm'} sua capacidade diária configurada.',
        );
      }
    }

    return PlannerResult(
      assignments: assignments,
      loadMinutes: load,
      overloadedDays: overloaded,
      insights: insights,
      historyAdjustedTasks: historyAdjusted,
      horizonDays: days,
    );
  }

  static _Candidate _candidateFor({
    required TaskItem task,
    required String date,
    required int dayIndex,
    required int estimatedMinutes,
    required int currentLoad,
    required List<_Interval> occupied,
    required PlannerSettings settings,
    required String startDate,
    required int minimumStartMinute,
  }) {
    final period = _preferredPeriod(task);
    final usableStart = max(settings.startHour * 60, minimumStartMinute);
    final bounds = _periodBounds(period, usableStart, settings.endHour * 60);
    var slot = _findSlot(
      occupied,
      bounds.$1,
      bounds.$2,
      estimatedMinutes,
      settings.breakMinutes,
    );
    var periodMatched = slot.isNotEmpty;

    if (slot.isEmpty && period != 'any') {
      slot = _findSlot(
        occupied,
        usableStart,
        settings.endHour * 60,
        estimatedMinutes,
        settings.breakMinutes,
      );
    }

    final projected = currentLoad + estimatedMinutes;
    final fits = projected <= settings.capacityMinutes && slot.isNotEmpty;
    final capacityRatio = projected / max(1, settings.capacityMinutes);
    final priority = task.effectivePriority(startDate);
    final dueIn = max(0, daysBetween(date, task.deadline));

    var score = capacityRatio * 100;
    score +=
        dayIndex *
        switch (priority) {
          'high' => 16,
          'medium' => 8,
          _ => 3,
        };
    if (!fits) score += 250;
    if (!periodMatched) score += 24;
    if (dueIn == 0) score -= 10;
    if (task.date == date) score -= 4;

    final reasonParts = <String>[];
    reasonParts.add(switch (priority) {
      'high' => 'prioridade alta',
      'medium' => 'prioridade média',
      _ => 'prioridade baixa',
    });
    if (task.deadline.length == 10) {
      final d = daysBetween(startDate, task.deadline);
      reasonParts.add(
        d <= 0 ? 'prazo imediato' : 'prazo em $d dia${d == 1 ? '' : 's'}',
      );
    }
    if (period != 'any') {
      reasonParts.add(
        periodMatched
            ? '${_periodLabel(period)} respeitada'
            : '${_periodLabel(period)} sem espaço; usada melhor janela disponível',
      );
    }
    reasonParts.add(
      capacityRatio <= .65
          ? 'carga leve'
          : capacityRatio <= .9
          ? 'carga equilibrada'
          : 'carga alta',
    );

    return _Candidate(
      date: date,
      time: slot,
      score: score,
      fits: fits,
      reason: _sentence(reasonParts.join(' · ')),
    );
  }

  static Map<int, double> _recentFocusAverages(List<FocusSession> sessions) {
    final recent = <int, List<int>>{};
    final ordered = [...sessions]
      ..sort((a, b) => a.startedAt.compareTo(b.startedAt));
    for (final session in ordered) {
      if (session.taskId == 0 || session.actualMinutes <= 0) continue;
      final values = recent.putIfAbsent(session.taskId, () => <int>[]);
      values.add(session.actualMinutes);
      if (values.length > 5) values.removeAt(0);
    }
    return {
      for (final entry in recent.entries)
        entry.key:
            entry.value.fold<int>(0, (sum, value) => sum + value) /
            entry.value.length,
    };
  }

  static int _estimateMinutes(TaskItem task, Map<int, double> history) {
    final base = max(15, task.minutes);
    final avg = history[task.id];
    if (avg == null) return base;
    final blended = (base * .55 + avg * .45).round();
    return blended.clamp(15, max(30, base * 2)).toInt();
  }

  static String _preferredPeriod(TaskItem task) {
    if (task.preferredPeriod != 'any') return task.preferredPeriod;
    return switch (task.energy) {
      'high' => 'morning',
      'low' => 'evening',
      _ => 'any',
    };
  }

  static (int, int) _periodBounds(String period, int dayStart, int dayEnd) {
    return switch (period) {
      'morning' => (max(dayStart, 6 * 60), min(dayEnd, 12 * 60)),
      'afternoon' => (max(dayStart, 12 * 60), min(dayEnd, 18 * 60)),
      'evening' => (max(dayStart, 18 * 60), dayEnd),
      _ => (dayStart, dayEnd),
    };
  }

  static String _periodLabel(String period) => switch (period) {
    'morning' => 'manhã preferida',
    'afternoon' => 'tarde preferida',
    'evening' => 'noite preferida',
    _ => 'horário livre',
  };

  static String _sentence(String value) {
    if (value.isEmpty) return value;
    return '${value[0].toUpperCase()}${value.substring(1)}.';
  }

  static bool _isWeekend(String iso) {
    final d = parseIso(iso);
    return d.weekday == DateTime.saturday || d.weekday == DateTime.sunday;
  }

  static int _parseMinutes(String time) {
    final parts = time.split(':');
    if (parts.length != 2) return -1;
    final h = int.tryParse(parts[0]);
    final m = int.tryParse(parts[1]);
    if (h == null || m == null) return -1;
    return h * 60 + m;
  }

  static String _formatMinutes(int value) =>
      '${(value ~/ 60).toString().padLeft(2, '0')}:${(value % 60).toString().padLeft(2, '0')}';

  static void _addOccupied(
    List<_Interval> list,
    String time,
    int minutes,
    int breakMinutes,
  ) {
    final start = _parseMinutes(time);
    if (start < 0 || minutes <= 0) return;
    list.add(_Interval(start, start + minutes + max(0, breakMinutes)));
    list.sort((a, b) => a.start.compareTo(b.start));
  }

  static String _findSlot(
    List<_Interval> list,
    int dayStart,
    int dayEnd,
    int minutes,
    int breakMinutes,
  ) {
    if (dayEnd <= dayStart || minutes <= 0) return '';
    final gap = max(0, breakMinutes);
    var cursor = dayStart;
    for (final interval in list) {
      if (interval.end <= dayStart || interval.start >= dayEnd) continue;
      if (cursor + minutes + gap <= interval.start) {
        return _formatMinutes(cursor);
      }
      if (interval.end > cursor) cursor = interval.end;
      if (cursor >= dayEnd) return '';
    }
    // If no block follows, only the task itself must fit before the day ends.
    return cursor + minutes <= dayEnd ? _formatMinutes(cursor) : '';
  }
}
