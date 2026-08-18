import 'package:flutter_test/flutter_test.dart';
import 'package:ritmo/models/models.dart';

void main() {
  group('RoutineItem', () {
    test('does not become due before start date', () {
      final routine = RoutineItem(
        id: 1,
        title: 'Começar depois',
        startDate: '2026-08-20',
        frequency: 'daily',
      );
      expect(routine.dueOn('2026-08-19'), isFalse);
      expect(routine.dueOn('2026-08-20'), isTrue);
    });

    test('custom weekday respects both mask and start date', () {
      // Monday in the Java-compatible mask used by the app = bit 1.
      final routine = RoutineItem(
        id: 2,
        title: 'Segunda',
        startDate: '2026-08-17',
        frequency: 'custom',
        daysMask: 1 << 1,
      );
      expect(routine.dueOn('2026-08-10'), isFalse);
      expect(routine.dueOn('2026-08-17'), isTrue);
      expect(routine.dueOn('2026-08-18'), isFalse);
    });
  });

  test('TaskItem round-trip preserves Smart Planner metadata', () {
    final task = TaskItem(
      id: 7,
      title: 'Auditar app',
      date: '2026-08-18',
      deadline: '2026-08-20',
      inbox: true,
      flexible: true,
      energy: 'high',
      preferredPeriod: 'morning',
      subtasks: [Subtask(id: 8, title: 'Teste', done: true)],
    );
    final restored = TaskItem.fromJson(task.toJson());
    expect(restored.inbox, isTrue);
    expect(restored.energy, 'high');
    expect(restored.preferredPeriod, 'morning');
    expect(restored.subtasks.single.done, isTrue);
  });

  test('date helpers use calendar-day semantics', () {
    expect(addDaysIso('2024-02-28', 1), '2024-02-29');
    expect(addDaysIso('2024-02-29', 1), '2024-03-01');
    expect(addDaysIso('2026-12-31', 1), '2027-01-01');
    expect(daysBetween('2026-08-18', '2026-08-25'), 7);
    expect(daysBetween('2026-08-25', '2026-08-18'), -7);
  });

  test('validates ISO dates strictly', () {
    expect(isValidIsoDate('2026-08-18'), isTrue);
    expect(isValidIsoDate('2026-02-30'), isFalse);
    expect(isValidIsoDate('2026-8-18'), isFalse);
    expect(isValidIsoDate('not-a-date'), isFalse);
    expect(isValidIsoDate(null), isFalse);
  });
}
