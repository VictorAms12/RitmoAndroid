import '../models/models.dart';

class PlannerSettings {
  final int startHour;
  final int endHour;
  final int capacityMinutes;
  final bool includeWeekend;
  const PlannerSettings({
    this.startHour = 8,
    this.endHour = 22,
    this.capacityMinutes = 360,
    this.includeWeekend = true,
  });
}

class PlannerAssignment {
  final int taskId;
  final String oldDate;
  final String oldTime;
  final String newDate;
  final String newTime;
  final bool overCapacity;
  const PlannerAssignment({
    required this.taskId,
    required this.oldDate,
    required this.oldTime,
    required this.newDate,
    required this.newTime,
    required this.overCapacity,
  });

  bool get moved => oldDate != newDate || oldTime != newTime;
}

class PlannerResult {
  final List<PlannerAssignment> assignments;
  final Map<String, int> loadMinutes;
  final int overloadedDays;
  const PlannerResult({
    required this.assignments,
    required this.loadMinutes,
    required this.overloadedDays,
  });

  int get movedTasks => assignments.where((e) => e.moved).length;
}

class _Interval {
  final int start;
  final int end;
  const _Interval(this.start, this.end);
}

class PlannerService {
  static PlannerResult plan(
    RitmoData data,
    PlannerSettings settings, {
    String? fromDate,
  }) {
    final start = fromDate ?? isoDate(DateTime.now());
    final horizon = addDaysIso(start, 6);
    final load = <String, int>{};
    final occupied = <String, List<_Interval>>{};

    for (var i = 0; i < 7; i++) {
      final date = addDaysIso(start, i);
      var routineMinutes = 0;
      for (final routine in data.routines) {
        if (routine.dueOn(date) && !routine.doneOn(date)) {
          routineMinutes += routine.minutes;
        }
      }
      load[date] = routineMinutes;
      occupied[date] = [];
    }

    final eligible = <TaskItem>[];
    for (final task in data.tasks) {
      if (task.status == 'done') continue;
      final canMove = task.flexible && task.recurrence == 'none';
      final due = task.deadline.length == 10 ? task.deadline : task.date;
      final relevant = task.date.compareTo(horizon) <= 0 || due.compareTo(horizon) <= 0;
      if (canMove && relevant) {
        eligible.add(task);
        continue;
      }
      if (task.date.compareTo(start) < 0 || task.date.compareTo(horizon) > 0) {
        continue;
      }
      load[task.date] = (load[task.date] ?? 0) + task.minutes;
      _addOccupied(occupied[task.date]!, task.time, task.minutes);
    }

    int score(String p) => p == 'high' ? 3 : p == 'medium' ? 2 : 1;
    eligible.sort((a, b) {
      final byPriority = score(b.effectivePriority(start)).compareTo(score(a.effectivePriority(start)));
      if (byPriority != 0) return byPriority;
      final byDue = a.deadline.compareTo(b.deadline);
      if (byDue != 0) return byDue;
      return b.minutes.compareTo(a.minutes);
    });

    final assignments = <PlannerAssignment>[];

    for (final task in eligible) {
      var deadline = task.deadline.length == 10 ? task.deadline : task.date;
      if (deadline.compareTo(start) < 0) deadline = start;
      if (deadline.compareTo(horizon) > 0) deadline = horizon;

      String? bestDate;
      var bestTime = '';
      var bestLoad = 1 << 30;
      var bestFits = false;

      for (var i = 0; i < 7; i++) {
        final date = addDaysIso(start, i);
        if (date.compareTo(deadline) > 0) break;
        if (!settings.includeWeekend && _isWeekend(date)) continue;

        final current = load[date] ?? 0;
        final mins = task.minutes < 15 ? 15 : task.minutes;
        final slot = _findSlot(
          occupied[date]!,
          settings.startHour * 60,
          settings.endHour * 60,
          mins,
        );
        final fits = current + mins <= settings.capacityMinutes && slot.isNotEmpty;

        if (bestDate == null ||
            (fits && !bestFits) ||
            (fits == bestFits && current < bestLoad)) {
          bestDate = date;
          bestTime = slot;
          bestLoad = current;
          bestFits = fits;
        }
      }

      if (bestDate == null) {
        for (var i = 0; i < 7; i++) {
          final date = addDaysIso(start, i);
          if (!settings.includeWeekend && _isWeekend(date)) continue;
          final current = load[date] ?? 0;
          if (bestDate == null || current < bestLoad) {
            bestDate = date;
            bestLoad = current;
            bestTime = _findSlot(
              occupied[date]!,
              settings.startHour * 60,
              settings.endHour * 60,
              task.minutes < 15 ? 15 : task.minutes,
            );
          }
        }
      }

      bestDate ??= start;
      final mins = task.minutes < 15 ? 15 : task.minutes;
      final over = (load[bestDate] ?? 0) + mins > settings.capacityMinutes ||
          bestTime.isEmpty;
      assignments.add(PlannerAssignment(
        taskId: task.id,
        oldDate: task.date,
        oldTime: task.time,
        newDate: bestDate,
        newTime: bestTime,
        overCapacity: over,
      ));
      load[bestDate] = (load[bestDate] ?? 0) + mins;
      if (bestTime.isNotEmpty) {
        _addOccupied(occupied[bestDate]!, bestTime, mins);
      }
    }

    return PlannerResult(
      assignments: assignments,
      loadMinutes: load,
      overloadedDays: load.values.where((e) => e > settings.capacityMinutes).length,
    );
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

  static void _addOccupied(List<_Interval> list, String time, int minutes) {
    final start = _parseMinutes(time);
    if (start < 0 || minutes <= 0) return;
    list.add(_Interval(start, start + minutes));
    list.sort((a, b) => a.start.compareTo(b.start));
  }

  static String _findSlot(
    List<_Interval> list,
    int dayStart,
    int dayEnd,
    int minutes,
  ) {
    var cursor = dayStart;
    final sorted = [...list]..sort((a, b) => a.start.compareTo(b.start));
    for (final interval in sorted) {
      if (cursor + minutes <= interval.start) return _formatMinutes(cursor);
      if (interval.end > cursor) cursor = interval.end;
      if (cursor >= dayEnd) return '';
    }
    return cursor + minutes <= dayEnd ? _formatMinutes(cursor) : '';
  }
}
