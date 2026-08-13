import 'dart:math';

String isoDate(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

DateTime parseIso(String? value) {
  if (value == null || value.length != 10) return DateTime.now();
  return DateTime.tryParse(value) ?? DateTime.now();
}

String addDaysIso(String iso, int days) =>
    isoDate(parseIso(iso).add(Duration(days: days)));

int daysBetween(String from, String to) =>
    parseIso(to).difference(parseIso(from)).inDays;

class Subtask {
  final int id;
  String title;
  bool done;
  Subtask({required this.id, required this.title, this.done = false});
  factory Subtask.fromJson(Map<String, dynamic> j) => Subtask(
        id: (j['id'] as num?)?.toInt() ?? DateTime.now().microsecondsSinceEpoch,
        title: (j['title'] ?? 'Subtarefa').toString(),
        done: j['done'] == true,
      );
  Map<String, dynamic> toJson() => {'id': id, 'title': title, 'done': done};
}

class TaskItem {
  final int id;
  int projectId;
  String title;
  String description;
  String date;
  String time;
  String deadline;
  String priority;
  int minutes;
  String category;
  String status;
  String recurrence;
  int reminderMinutes;
  bool flexible;
  List<Subtask> subtasks;

  TaskItem({
    required this.id,
    this.projectId = 0,
    required this.title,
    this.description = '',
    required this.date,
    this.time = '',
    String? deadline,
    this.priority = 'low',
    this.minutes = 30,
    this.category = 'Pessoal',
    this.status = 'todo',
    this.recurrence = 'none',
    this.reminderMinutes = -1,
    this.flexible = false,
    List<Subtask>? subtasks,
  })  : deadline = deadline ?? date,
        subtasks = subtasks ?? [];

  String effectivePriority(String today) {
    if (priority != 'auto') return priority;
    if (status == 'done') return 'low';
    final days = daysBetween(today, deadline);
    if (days <= 1) return 'high';
    if (days <= 3) return 'medium';
    return 'low';
  }

  factory TaskItem.fromJson(Map<String, dynamic> j) {
    final date = (j['date'] ?? isoDate(DateTime.now())).toString();
    return TaskItem(
      id: (j['id'] as num?)?.toInt() ?? DateTime.now().microsecondsSinceEpoch,
      projectId: (j['projectId'] as num?)?.toInt() ?? 0,
      title: (j['title'] ?? 'Tarefa').toString(),
      description: (j['description'] ?? '').toString(),
      date: date,
      time: (j['time'] ?? '').toString(),
      deadline: (j['deadline'] ?? date).toString(),
      priority: (j['priority'] ?? 'low').toString(),
      minutes: max(0, (j['minutes'] as num?)?.toInt() ?? 30),
      category: (j['category'] ?? 'Pessoal').toString(),
      status: (j['status'] ?? 'todo').toString(),
      recurrence: (j['recurrence'] ?? 'none').toString(),
      reminderMinutes: (j['reminderMinutes'] as num?)?.toInt() ?? -1,
      flexible: j['flexible'] == true,
      subtasks: ((j['subtasks'] as List?) ?? [])
          .whereType<Map>()
          .map((e) => Subtask.fromJson(Map<String, dynamic>.from(e)))
          .toList(),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'projectId': projectId,
        'title': title,
        'description': description,
        'date': date,
        'time': time,
        'deadline': deadline,
        'priority': priority,
        'minutes': minutes,
        'category': category,
        'status': status,
        'recurrence': recurrence,
        'reminderMinutes': reminderMinutes,
        'flexible': flexible,
        'subtasks': subtasks.map((e) => e.toJson()).toList(),
      };
}

class ProjectItem {
  final int id;
  String title;
  String description;
  String targetDate;
  ProjectItem({required this.id, required this.title, this.description = '', this.targetDate = ''});
  factory ProjectItem.fromJson(Map<String, dynamic> j) => ProjectItem(
        id: (j['id'] as num?)?.toInt() ?? DateTime.now().microsecondsSinceEpoch,
        title: (j['title'] ?? 'Projeto').toString(),
        description: (j['description'] ?? '').toString(),
        targetDate: (j['targetDate'] ?? '').toString(),
      );
  Map<String, dynamic> toJson() => {'id': id, 'title': title, 'description': description, 'targetDate': targetDate};
}

class GoalItem {
  final int id;
  String title;
  int progress;
  String targetDate;
  GoalItem({required this.id, required this.title, this.progress = 0, this.targetDate = ''});
  factory GoalItem.fromJson(Map<String, dynamic> j) => GoalItem(
        id: (j['id'] as num?)?.toInt() ?? DateTime.now().microsecondsSinceEpoch,
        title: (j['title'] ?? 'Meta').toString(),
        progress: ((j['progress'] as num?)?.toInt() ?? 0).clamp(0, 100).toInt(),
        targetDate: (j['targetDate'] ?? '').toString(),
      );
  Map<String, dynamic> toJson() => {'id': id, 'title': title, 'progress': progress, 'targetDate': targetDate};
}

class RoutineItem {
  final int id;
  String title;
  String detail;
  String frequency;
  int minutes;
  String startDate;
  String time;
  String category;
  String accent;
  int reminderMinutes;
  int daysMask;
  List<String> doneDates;

  RoutineItem({
    required this.id,
    required this.title,
    this.detail = '',
    this.frequency = 'daily',
    this.minutes = 15,
    required this.startDate,
    this.time = '',
    this.category = 'Pessoal',
    this.accent = 'green',
    this.reminderMinutes = -1,
    this.daysMask = 0,
    List<String>? doneDates,
  }) : doneDates = doneDates ?? [];

  bool doneOn(String date) => doneDates.contains(date);

  bool dueOn(String date) {
    final d = parseIso(date);
    if (frequency == 'custom') {
      final javaDow = d.weekday == DateTime.sunday ? 1 : d.weekday + 1;
      final bit = 1 << (javaDow - 1);
      return daysMask != 0 && (daysMask & bit) != 0;
    }
    if (frequency == 'weekdays') {
      return d.weekday != DateTime.saturday && d.weekday != DateTime.sunday;
    }
    if (frequency == 'weekly') return parseIso(startDate).weekday == d.weekday;
    return true;
  }

  int streak(String referenceDate) {
    var cursor = referenceDate;
    if (dueOn(cursor) && !doneOn(cursor)) cursor = addDaysIso(cursor, -1);
    var value = 0;
    for (var guard = 0; guard < 370; guard++) {
      if (!dueOn(cursor)) {
        cursor = addDaysIso(cursor, -1);
        continue;
      }
      if (!doneOn(cursor)) break;
      value++;
      cursor = addDaysIso(cursor, -1);
    }
    return value;
  }

  factory RoutineItem.fromJson(Map<String, dynamic> j) => RoutineItem(
        id: (j['id'] as num?)?.toInt() ?? DateTime.now().microsecondsSinceEpoch,
        title: (j['title'] ?? 'Hábito').toString(),
        detail: (j['detail'] ?? '').toString(),
        frequency: (j['frequency'] ?? 'daily').toString(),
        minutes: max(0, (j['minutes'] as num?)?.toInt() ?? 15),
        startDate: (j['startDate'] ?? isoDate(DateTime.now())).toString(),
        time: (j['time'] ?? '').toString(),
        category: (j['category'] ?? 'Pessoal').toString(),
        accent: (j['accent'] ?? 'green').toString(),
        reminderMinutes: (j['reminderMinutes'] as num?)?.toInt() ?? -1,
        daysMask: (j['daysMask'] as num?)?.toInt() ?? 0,
        doneDates: ((j['doneDates'] as List?) ?? []).map((e) => e.toString()).toList(),
      );
  Map<String, dynamic> toJson() => {
        'id': id, 'title': title, 'detail': detail, 'frequency': frequency,
        'minutes': minutes, 'startDate': startDate, 'time': time,
        'category': category, 'accent': accent, 'reminderMinutes': reminderMinutes,
        'daysMask': daysMask, 'doneDates': doneDates,
      };
}

class CompletionItem {
  int taskId;
  String title;
  String date;
  String category;
  int minutes;
  CompletionItem({required this.taskId, required this.title, required this.date, required this.category, required this.minutes});
  factory CompletionItem.fromJson(Map<String, dynamic> j) => CompletionItem(
        taskId: (j['taskId'] as num?)?.toInt() ?? 0,
        title: (j['title'] ?? '').toString(),
        date: (j['date'] ?? isoDate(DateTime.now())).toString(),
        category: (j['category'] ?? 'Pessoal').toString(),
        minutes: max(0, (j['minutes'] as num?)?.toInt() ?? 0),
      );
  Map<String, dynamic> toJson() => {'taskId': taskId, 'title': title, 'date': date, 'category': category, 'minutes': minutes};
}

class FocusSession {
  final int id;
  int taskId;
  String title;
  String date;
  String mode;
  int plannedMinutes;
  int actualMinutes;
  int startedAt;
  FocusSession({
    required this.id, this.taskId = 0, required this.title, required this.date,
    this.mode = 'Pomodoro 25', this.plannedMinutes = 25, this.actualMinutes = 0,
    required this.startedAt,
  });
  factory FocusSession.fromJson(Map<String, dynamic> j) => FocusSession(
        id: (j['id'] as num?)?.toInt() ?? DateTime.now().microsecondsSinceEpoch,
        taskId: (j['taskId'] as num?)?.toInt() ?? 0,
        title: (j['title'] ?? 'Sessão de foco').toString(),
        date: (j['date'] ?? isoDate(DateTime.now())).toString(),
        mode: (j['mode'] ?? 'Pomodoro 25').toString(),
        plannedMinutes: max(1, (j['plannedMinutes'] as num?)?.toInt() ?? 25),
        actualMinutes: max(0, (j['actualMinutes'] as num?)?.toInt() ?? 0),
        startedAt: (j['startedAt'] as num?)?.toInt() ?? DateTime.now().millisecondsSinceEpoch,
      );
  Map<String, dynamic> toJson() => {
        'id': id, 'taskId': taskId, 'title': title, 'date': date, 'mode': mode,
        'plannedMinutes': plannedMinutes, 'actualMinutes': actualMinutes, 'startedAt': startedAt,
      };
}

class DayReview {
  String date;
  int mood;
  String note;
  int doneCount;
  int pendingCount;
  int focusMinutes;
  int createdAt;
  DayReview({
    required this.date, this.mood = 3, this.note = '', this.doneCount = 0,
    this.pendingCount = 0, this.focusMinutes = 0, required this.createdAt,
  });
  factory DayReview.fromJson(Map<String, dynamic> j) => DayReview(
        date: (j['date'] ?? isoDate(DateTime.now())).toString(),
        mood: ((j['mood'] as num?)?.toInt() ?? 3).clamp(1, 5).toInt(),
        note: (j['note'] ?? '').toString(),
        doneCount: max(0, (j['doneCount'] as num?)?.toInt() ?? 0),
        pendingCount: max(0, (j['pendingCount'] as num?)?.toInt() ?? 0),
        focusMinutes: max(0, (j['focusMinutes'] as num?)?.toInt() ?? 0),
        createdAt: (j['createdAt'] as num?)?.toInt() ?? DateTime.now().millisecondsSinceEpoch,
      );
  Map<String, dynamic> toJson() => {
        'date': date, 'mood': mood, 'note': note, 'doneCount': doneCount,
        'pendingCount': pendingCount, 'focusMinutes': focusMinutes, 'createdAt': createdAt,
      };
}

class RitmoData {
  List<TaskItem> tasks;
  List<GoalItem> goals;
  List<RoutineItem> routines;
  List<CompletionItem> completions;
  List<ProjectItem> projects;
  List<FocusSession> focusSessions;
  List<DayReview> dayReviews;

  RitmoData({
    List<TaskItem>? tasks, List<GoalItem>? goals, List<RoutineItem>? routines,
    List<CompletionItem>? completions, List<ProjectItem>? projects,
    List<FocusSession>? focusSessions, List<DayReview>? dayReviews,
  })  : tasks = tasks ?? [], goals = goals ?? [], routines = routines ?? [],
        completions = completions ?? [], projects = projects ?? [],
        focusSessions = focusSessions ?? [], dayReviews = dayReviews ?? [];

  factory RitmoData.fromJson(Map<String, dynamic> j) => RitmoData(
        tasks: ((j['tasks'] as List?) ?? []).whereType<Map>().map((e) => TaskItem.fromJson(Map<String, dynamic>.from(e))).toList(),
        goals: ((j['goals'] as List?) ?? []).whereType<Map>().map((e) => GoalItem.fromJson(Map<String, dynamic>.from(e))).toList(),
        routines: ((j['routines'] as List?) ?? []).whereType<Map>().map((e) => RoutineItem.fromJson(Map<String, dynamic>.from(e))).toList(),
        completions: ((j['completions'] as List?) ?? []).whereType<Map>().map((e) => CompletionItem.fromJson(Map<String, dynamic>.from(e))).toList(),
        projects: ((j['projects'] as List?) ?? []).whereType<Map>().map((e) => ProjectItem.fromJson(Map<String, dynamic>.from(e))).toList(),
        focusSessions: ((j['focusSessions'] as List?) ?? []).whereType<Map>().map((e) => FocusSession.fromJson(Map<String, dynamic>.from(e))).toList(),
        dayReviews: ((j['dayReviews'] as List?) ?? []).whereType<Map>().map((e) => DayReview.fromJson(Map<String, dynamic>.from(e))).toList(),
      );

  Map<String, dynamic> toJson() => {
        'tasks': tasks.map((e) => e.toJson()).toList(),
        'goals': goals.map((e) => e.toJson()).toList(),
        'routines': routines.map((e) => e.toJson()).toList(),
        'completions': completions.map((e) => e.toJson()).toList(),
        'projects': projects.map((e) => e.toJson()).toList(),
        'focusSessions': focusSessions.map((e) => e.toJson()).toList(),
        'dayReviews': dayReviews.map((e) => e.toJson()).toList(),
        'schemaVersion': 7,
      };
}
