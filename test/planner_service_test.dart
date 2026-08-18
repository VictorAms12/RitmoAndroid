import 'package:flutter_test/flutter_test.dart';
import 'package:ritmo/models/models.dart';
import 'package:ritmo/services/planner_service.dart';

void main() {
  test('Smart Planner ignores Inbox and fixed tasks', () {
    final data = RitmoData(tasks: [
      TaskItem(
        id: 1,
        title: 'Inbox',
        date: '2026-08-18',
        inbox: true,
        flexible: true,
      ),
      TaskItem(
        id: 2,
        title: 'Fixa',
        date: '2026-08-18',
        time: '09:00',
        flexible: false,
        minutes: 60,
      ),
      TaskItem(
        id: 3,
        title: 'Flexível',
        date: '2026-08-18',
        deadline: '2026-08-19',
        flexible: true,
        minutes: 30,
      ),
    ]);

    final result = PlannerService.plan(
      data,
      const PlannerSettings(
        startHour: 8,
        endHour: 18,
        capacityMinutes: 360,
        horizonDays: 2,
      ),
      fromDate: '2026-08-18',
    );

    expect(result.assignments.map((e) => e.taskId), contains(3));
    expect(result.assignments.map((e) => e.taskId), isNot(contains(1)));
    expect(result.assignments.map((e) => e.taskId), isNot(contains(2)));
    expect(result.assignments.single.newTime, isNot('09:00'));
  });

  test('focus history adjusts estimate without scanning semantics changes', () {
    final task = TaskItem(
      id: 10,
      title: 'Estudo',
      date: '2026-08-18',
      deadline: '2026-08-18',
      flexible: true,
      minutes: 30,
    );
    final data = RitmoData(
      tasks: [task],
      focusSessions: List.generate(
        5,
        (i) => FocusSession(
          id: i,
          taskId: 10,
          title: 'Estudo',
          date: '2026-08-${(10 + i).toString().padLeft(2, '0')}',
          plannedMinutes: 30,
          actualMinutes: 50,
          startedAt: i,
        ),
      ),
    );
    final result = PlannerService.plan(
      data,
      const PlannerSettings(horizonDays: 1),
      fromDate: '2026-08-18',
    );
    expect(result.assignments.single.estimatedMinutes, greaterThan(30));
  });
}
