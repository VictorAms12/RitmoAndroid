import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../core/app_state.dart';
import '../models/models.dart';

const _categories = [
  'Pessoal',
  'Estudos',
  'Trabalho',
  'Projeto',
  'Saúde',
  'Financeiro',
];

String _formatDateLabel(String iso) {
  final d = parseIso(iso);
  return DateFormat('dd/MM/yyyy').format(d);
}

Future<String?> _pickDate(BuildContext context, String current) async {
  final picked = await showDatePicker(
    context: context,
    initialDate: parseIso(current),
    firstDate: DateTime(2024),
    lastDate: DateTime(2035),
  );
  return picked == null ? null : isoDate(picked);
}

Future<String?> _pickTime(BuildContext context, String current) async {
  final parts = current.split(':');
  final initial = parts.length == 2
      ? TimeOfDay(
          hour: int.tryParse(parts[0]) ?? 9,
          minute: int.tryParse(parts[1]) ?? 0,
        )
      : TimeOfDay.now();
  final picked = await showTimePicker(context: context, initialTime: initial);
  if (picked == null) return null;
  return '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
}

Future<void> showTaskEditor(
  BuildContext context,
  AppState state, {
  TaskItem? task,
  String? date,
  bool commitment = false,
}) async {
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => TaskEditorSheet(
      state: state,
      original: task,
      initialDate: date,
      commitment: commitment,
    ),
  );
}

class TaskEditorSheet extends StatefulWidget {
  final AppState state;
  final TaskItem? original;
  final String? initialDate;
  final bool commitment;
  const TaskEditorSheet({
    super.key,
    required this.state,
    this.original,
    this.initialDate,
    this.commitment = false,
  });

  @override
  State<TaskEditorSheet> createState() => _TaskEditorSheetState();
}

class _TaskEditorSheetState extends State<TaskEditorSheet> {
  late final TextEditingController _title;
  late final TextEditingController _description;
  late final TextEditingController _minutes;
  late String _date;
  late String _deadline;
  late String _time;
  late String _priority;
  late String _category;
  late String _recurrence;
  late int _reminder;
  late bool _flexible;
  late int _projectId;
  final List<TextEditingController> _subtasks = [];

  @override
  void initState() {
    super.initState();
    final t = widget.original;
    _title = TextEditingController(text: t?.title ?? '');
    _description = TextEditingController(text: t?.description ?? '');
    _minutes = TextEditingController(text: '${t?.minutes ?? (widget.commitment ? 60 : 30)}');
    _date = t?.date ?? widget.initialDate ?? widget.state.today;
    _deadline = t?.deadline ?? _date;
    _time = t?.time ?? '';
    _priority = t?.priority ?? (widget.commitment ? 'medium' : 'auto');
    _category = t?.category ?? (widget.commitment ? 'Pessoal' : 'Pessoal');
    _recurrence = t?.recurrence ?? 'none';
    _reminder = t?.reminderMinutes ?? -1;
    _flexible = t?.flexible ?? !widget.commitment;
    _projectId = t?.projectId ?? 0;
    for (final s in t?.subtasks ?? <Subtask>[]) {
      _subtasks.add(TextEditingController(text: s.title));
    }
  }

  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    _minutes.dispose();
    for (final c in _subtasks) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _save() async {
    final title = _title.text.trim();
    if (title.isEmpty) return;
    final original = widget.original;
    final result = TaskItem(
      id: original?.id ?? DateTime.now().microsecondsSinceEpoch,
      projectId: _projectId,
      title: title,
      description: _description.text.trim(),
      date: _date,
      time: _time,
      deadline: _deadline.compareTo(_date) < 0 ? _date : _deadline,
      priority: _priority,
      minutes: int.tryParse(_minutes.text.trim()) ?? 30,
      category: _category,
      status: original?.status ?? 'todo',
      recurrence: _recurrence,
      reminderMinutes: _reminder,
      flexible: _flexible && _recurrence == 'none',
      subtasks: List.generate(_subtasks.length, (index) {
        final old = original != null && index < original.subtasks.length
            ? original.subtasks[index]
            : null;
        return Subtask(
          id: old?.id ?? DateTime.now().microsecondsSinceEpoch + index,
          title: _subtasks[index].text.trim().isEmpty
              ? 'Subtarefa ${index + 1}'
              : _subtasks[index].text.trim(),
          done: old?.done ?? false,
        );
      }),
    );
    await widget.state.addOrUpdateTask(result);
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.viewInsetsOf(context).bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(16, 0, 16, 16 + bottom),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    widget.original == null
                        ? (widget.commitment ? 'Novo compromisso' : 'Nova tarefa')
                        : 'Editar tarefa',
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                ),
                if (widget.original != null)
                  IconButton(
                    tooltip: 'Excluir tarefa',
                    color: Theme.of(context).colorScheme.error,
                    onPressed: () async {
                      final ok = await showDialog<bool>(
                            context: context,
                            builder: (context) => AlertDialog(
                              title: const Text('Excluir tarefa?'),
                              content: Text(
                                '“${widget.original!.title}” será removida definitivamente.',
                              ),
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
                      if (ok) {
                        await widget.state.deleteTask(widget.original!);
                        if (context.mounted) Navigator.pop(context);
                      }
                    },
                    icon: const Icon(Icons.delete_outline_rounded),
                  ),
              ],
            ),
            const SizedBox(height: 14),
            TextField(
              controller: _title,
              autofocus: widget.original == null,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                labelText: 'Título',
                hintText: 'O que precisa ser feito?',
                prefixIcon: Icon(Icons.check_circle_outline_rounded),
              ),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: _description,
              maxLines: 3,
              minLines: 2,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                labelText: 'Descrição',
                hintText: 'Contexto, observações ou detalhes',
                prefixIcon: Icon(Icons.notes_rounded),
              ),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: _PickerTile(
                    icon: Icons.calendar_today_rounded,
                    label: 'Data',
                    value: _formatDateLabel(_date),
                    onTap: () async {
                      final value = await _pickDate(context, _date);
                      if (value != null) setState(() => _date = value);
                    },
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _PickerTile(
                    icon: Icons.flag_outlined,
                    label: 'Prazo',
                    value: _formatDateLabel(_deadline),
                    onTap: () async {
                      final value = await _pickDate(context, _deadline);
                      if (value != null) setState(() => _deadline = value);
                    },
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: _PickerTile(
                    icon: Icons.schedule_rounded,
                    label: 'Horário',
                    value: _time.isEmpty ? 'Sem horário' : _time,
                    onTap: () async {
                      final value = await _pickTime(context, _time);
                      if (value != null) setState(() => _time = value);
                    },
                    onClear: _time.isEmpty ? null : () => setState(() => _time = ''),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: TextField(
                    controller: _minutes,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Duração (min)',
                      prefixIcon: Icon(Icons.timelapse_rounded),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              initialValue: _category,
              decoration: const InputDecoration(
                labelText: 'Categoria',
                prefixIcon: Icon(Icons.category_outlined),
              ),
              items: _categories
                  .map((e) => DropdownMenuItem(value: e, child: Text(e)))
                  .toList(),
              onChanged: (v) => setState(() => _category = v ?? _category),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              initialValue: _priority,
              decoration: const InputDecoration(
                labelText: 'Prioridade',
                prefixIcon: Icon(Icons.priority_high_rounded),
              ),
              items: const [
                DropdownMenuItem(value: 'auto', child: Text('Automática')),
                DropdownMenuItem(value: 'high', child: Text('Alta')),
                DropdownMenuItem(value: 'medium', child: Text('Média')),
                DropdownMenuItem(value: 'low', child: Text('Baixa')),
              ],
              onChanged: (v) => setState(() => _priority = v ?? _priority),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              initialValue: _recurrence,
              decoration: const InputDecoration(
                labelText: 'Recorrência',
                prefixIcon: Icon(Icons.repeat_rounded),
              ),
              items: const [
                DropdownMenuItem(value: 'none', child: Text('Não repetir')),
                DropdownMenuItem(value: 'daily', child: Text('Todos os dias')),
                DropdownMenuItem(value: 'weekdays', child: Text('Segunda a sexta')),
                DropdownMenuItem(value: 'weekly', child: Text('Semanal')),
                DropdownMenuItem(value: 'monthly', child: Text('Mensal')),
              ],
              onChanged: (v) {
                setState(() {
                  _recurrence = v ?? _recurrence;
                  if (_recurrence != 'none') _flexible = false;
                });
              },
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<int>(
              initialValue: _reminder,
              decoration: const InputDecoration(
                labelText: 'Lembrete',
                prefixIcon: Icon(Icons.notifications_none_rounded),
              ),
              items: const [
                DropdownMenuItem(value: -1, child: Text('Sem lembrete')),
                DropdownMenuItem(value: 0, child: Text('Na hora')),
                DropdownMenuItem(value: 10, child: Text('10 min antes')),
                DropdownMenuItem(value: 30, child: Text('30 min antes')),
                DropdownMenuItem(value: 60, child: Text('1 hora antes')),
                DropdownMenuItem(value: 1440, child: Text('1 dia antes')),
              ],
              onChanged: (v) => setState(() => _reminder = v ?? -1),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<int>(
              initialValue: widget.state.data.projects.any((p) => p.id == _projectId)
                  ? _projectId
                  : 0,
              decoration: const InputDecoration(
                labelText: 'Projeto',
                prefixIcon: Icon(Icons.folder_outlined),
              ),
              items: [
                const DropdownMenuItem(value: 0, child: Text('Sem projeto')),
                ...widget.state.data.projects
                    .map((p) => DropdownMenuItem(value: p.id, child: Text(p.title))),
              ],
              onChanged: (v) => setState(() => _projectId = v ?? 0),
            ),
            const SizedBox(height: 10),
            SwitchListTile.adaptive(
              value: _flexible,
              onChanged: _recurrence == 'none'
                  ? (v) => setState(() => _flexible = v)
                  : null,
              contentPadding: const EdgeInsets.symmetric(horizontal: 8),
              title: const Text('Planejamento flexível'),
              subtitle: const Text(
                'Permite que o Ritmo redistribua esta tarefa automaticamente.',
              ),
              secondary: const Icon(Icons.auto_awesome_rounded),
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Subtarefas',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                TextButton.icon(
                  onPressed: () => setState(
                    () => _subtasks.add(TextEditingController()),
                  ),
                  icon: const Icon(Icons.add_rounded),
                  label: const Text('Adicionar'),
                ),
              ],
            ),
            ...List.generate(_subtasks.length, (index) {
              return Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: TextField(
                  controller: _subtasks[index],
                  decoration: InputDecoration(
                    hintText: 'Subtarefa ${index + 1}',
                    prefixIcon: const Icon(Icons.subdirectory_arrow_right_rounded),
                    suffixIcon: IconButton(
                      onPressed: () {
                        final c = _subtasks.removeAt(index);
                        c.dispose();
                        setState(() {});
                      },
                      icon: const Icon(Icons.close_rounded),
                    ),
                  ),
                ),
              );
            }),
            const SizedBox(height: 8),
            FilledButton.icon(
              onPressed: _save,
              icon: const Icon(Icons.check_rounded),
              label: Text(widget.original == null ? 'Criar' : 'Salvar alterações'),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}

Future<void> showRoutineEditor(
  BuildContext context,
  AppState state, {
  RoutineItem? routine,
}) async {
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => RoutineEditorSheet(state: state, original: routine),
  );
}

class RoutineEditorSheet extends StatefulWidget {
  final AppState state;
  final RoutineItem? original;
  const RoutineEditorSheet({super.key, required this.state, this.original});

  @override
  State<RoutineEditorSheet> createState() => _RoutineEditorSheetState();
}

class _RoutineEditorSheetState extends State<RoutineEditorSheet> {
  late final TextEditingController _title;
  late final TextEditingController _detail;
  late final TextEditingController _minutes;
  late String _frequency;
  late String _time;
  late String _category;
  late int _reminder;
  late int _daysMask;
  final Set<int> _selectedWeekdays = {};

  @override
  void initState() {
    super.initState();
    final r = widget.original;
    _title = TextEditingController(text: r?.title ?? '');
    _detail = TextEditingController(text: r?.detail ?? '');
    _minutes = TextEditingController(text: '${r?.minutes ?? 15}');
    _frequency = r?.frequency ?? 'daily';
    _time = r?.time ?? '';
    _category = r?.category ?? 'Pessoal';
    _reminder = r?.reminderMinutes ?? -1;
    _daysMask = r?.daysMask ?? 0;
    for (var weekday = 1; weekday <= 7; weekday++) {
      final javaDow = weekday == DateTime.sunday ? 1 : weekday + 1;
      final bit = 1 << (javaDow - 1);
      if ((_daysMask & bit) != 0) _selectedWeekdays.add(weekday);
    }
  }

  @override
  void dispose() {
    _title.dispose();
    _detail.dispose();
    _minutes.dispose();
    super.dispose();
  }

  int _buildDaysMask() {
    var mask = 0;
    for (final weekday in _selectedWeekdays) {
      final javaDow = weekday == DateTime.sunday ? 1 : weekday + 1;
      mask |= 1 << (javaDow - 1);
    }
    return mask;
  }

  Future<void> _save() async {
    if (_title.text.trim().isEmpty) return;
    final old = widget.original;
    final item = RoutineItem(
      id: old?.id ?? DateTime.now().microsecondsSinceEpoch,
      title: _title.text.trim(),
      detail: _detail.text.trim(),
      frequency: _frequency,
      minutes: int.tryParse(_minutes.text) ?? 15,
      startDate: old?.startDate ?? widget.state.today,
      time: _time,
      category: _category,
      accent: old?.accent ?? 'green',
      reminderMinutes: _reminder,
      daysMask: _frequency == 'custom' ? _buildDaysMask() : 0,
      doneDates: [...(old?.doneDates ?? const <String>[])],
    );
    await widget.state.addOrUpdateRoutine(item);
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.viewInsetsOf(context).bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(16, 0, 16, 16 + bottom),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    widget.original == null ? 'Novo hábito' : 'Editar hábito',
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                ),
                if (widget.original != null)
                  IconButton(
                    color: Theme.of(context).colorScheme.error,
                    onPressed: () async {
                      final ok = await showDialog<bool>(
                            context: context,
                            builder: (context) => AlertDialog(
                              title: const Text('Excluir hábito?'),
                              content: Text('“${widget.original!.title}” será removido.'),
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
                      if (ok) {
                        await widget.state.deleteRoutine(widget.original!);
                        if (context.mounted) Navigator.pop(context);
                      }
                    },
                    icon: const Icon(Icons.delete_outline_rounded),
                  ),
              ],
            ),
            const SizedBox(height: 14),
            TextField(
              controller: _title,
              autofocus: widget.original == null,
              decoration: const InputDecoration(
                labelText: 'Nome',
                hintText: 'Ex.: Caminhar, ler, revisar agenda',
                prefixIcon: Icon(Icons.repeat_rounded),
              ),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: _detail,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: 'Descrição',
                prefixIcon: Icon(Icons.notes_rounded),
              ),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              initialValue: _frequency,
              decoration: const InputDecoration(
                labelText: 'Frequência',
                prefixIcon: Icon(Icons.event_repeat_rounded),
              ),
              items: const [
                DropdownMenuItem(value: 'daily', child: Text('Todos os dias')),
                DropdownMenuItem(value: 'weekdays', child: Text('Segunda a sexta')),
                DropdownMenuItem(value: 'weekly', child: Text('Semanal')),
                DropdownMenuItem(value: 'custom', child: Text('Dias específicos')),
              ],
              onChanged: (v) => setState(() => _frequency = v ?? 'daily'),
            ),
            if (_frequency == 'custom') ...[
              const SizedBox(height: 10),
              Wrap(
                spacing: 6,
                children: List.generate(7, (index) {
                  final weekday = index + 1;
                  const labels = ['S', 'T', 'Q', 'Q', 'S', 'S', 'D'];
                  return FilterChip(
                    selected: _selectedWeekdays.contains(weekday),
                    label: Text(labels[index]),
                    onSelected: (selected) {
                      setState(() {
                        if (selected) {
                          _selectedWeekdays.add(weekday);
                        } else {
                          _selectedWeekdays.remove(weekday);
                        }
                      });
                    },
                  );
                }),
              ),
            ],
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: _PickerTile(
                    icon: Icons.schedule_rounded,
                    label: 'Horário',
                    value: _time.isEmpty ? 'Sem horário' : _time,
                    onTap: () async {
                      final value = await _pickTime(context, _time);
                      if (value != null) setState(() => _time = value);
                    },
                    onClear: _time.isEmpty ? null : () => setState(() => _time = ''),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: TextField(
                    controller: _minutes,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Duração (min)',
                      prefixIcon: Icon(Icons.timelapse_rounded),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              initialValue: _category,
              decoration: const InputDecoration(
                labelText: 'Categoria',
                prefixIcon: Icon(Icons.category_outlined),
              ),
              items: _categories
                  .map((e) => DropdownMenuItem(value: e, child: Text(e)))
                  .toList(),
              onChanged: (v) => setState(() => _category = v ?? _category),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<int>(
              initialValue: _reminder,
              decoration: const InputDecoration(
                labelText: 'Lembrete',
                prefixIcon: Icon(Icons.notifications_none_rounded),
              ),
              items: const [
                DropdownMenuItem(value: -1, child: Text('Sem lembrete')),
                DropdownMenuItem(value: 0, child: Text('Na hora')),
                DropdownMenuItem(value: 10, child: Text('10 min antes')),
                DropdownMenuItem(value: 30, child: Text('30 min antes')),
                DropdownMenuItem(value: 60, child: Text('1 hora antes')),
              ],
              onChanged: (v) => setState(() => _reminder = v ?? -1),
            ),
            const SizedBox(height: 14),
            FilledButton.icon(
              onPressed: _save,
              icon: const Icon(Icons.check_rounded),
              label: Text(widget.original == null ? 'Criar hábito' : 'Salvar alterações'),
            ),
          ],
        ),
      ),
    );
  }
}

Future<void> showGoalEditor(
  BuildContext context,
  AppState state, {
  GoalItem? goal,
}) async {
  final title = TextEditingController(text: goal?.title ?? '');
  final progress = TextEditingController(text: '${goal?.progress ?? 0}');
  var targetDate = goal?.targetDate ?? addDaysIso(state.today, 30);

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
          16 + MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                goal == null ? 'Nova meta' : 'Editar meta',
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 14),
              TextField(
                controller: title,
                decoration: const InputDecoration(
                  labelText: 'Meta',
                  prefixIcon: Icon(Icons.flag_outlined),
                ),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: progress,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Progresso (%)',
                  prefixIcon: Icon(Icons.trending_up_rounded),
                ),
              ),
              const SizedBox(height: 10),
              _PickerTile(
                icon: Icons.event_rounded,
                label: 'Prazo',
                value: _formatDateLabel(targetDate),
                onTap: () async {
                  final picked = await _pickDate(context, targetDate);
                  if (picked != null) setLocal(() => targetDate = picked);
                },
              ),
              const SizedBox(height: 14),
              FilledButton(
                onPressed: () async {
                  if (title.text.trim().isEmpty) return;
                  await state.addOrUpdateGoal(GoalItem(
                    id: goal?.id ?? DateTime.now().microsecondsSinceEpoch,
                    title: title.text.trim(),
                    progress: (int.tryParse(progress.text) ?? 0).clamp(0, 100).toInt(),
                    targetDate: targetDate,
                  ));
                  if (context.mounted) Navigator.pop(context);
                },
                child: const Text('Salvar meta'),
              ),
              if (goal != null) ...[
                const SizedBox(height: 8),
                TextButton.icon(
                  style: TextButton.styleFrom(
                    foregroundColor: Theme.of(context).colorScheme.error,
                  ),
                  onPressed: () async {
                    await state.deleteGoal(goal);
                    if (context.mounted) Navigator.pop(context);
                  },
                  icon: const Icon(Icons.delete_outline_rounded),
                  label: const Text('Excluir meta'),
                ),
              ],
            ],
          ),
        ),
      ),
    ),
  );
  title.dispose();
  progress.dispose();
}

Future<void> showProjectEditor(
  BuildContext context,
  AppState state, {
  ProjectItem? project,
}) async {
  final title = TextEditingController(text: project?.title ?? '');
  final description = TextEditingController(text: project?.description ?? '');
  var targetDate = project?.targetDate ?? addDaysIso(state.today, 30);

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
          16 + MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                project == null ? 'Novo projeto' : 'Editar projeto',
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 14),
              TextField(
                controller: title,
                decoration: const InputDecoration(
                  labelText: 'Projeto',
                  prefixIcon: Icon(Icons.folder_outlined),
                ),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: description,
                maxLines: 3,
                decoration: const InputDecoration(
                  labelText: 'Descrição',
                  prefixIcon: Icon(Icons.notes_rounded),
                ),
              ),
              const SizedBox(height: 10),
              _PickerTile(
                icon: Icons.event_rounded,
                label: 'Prazo',
                value: _formatDateLabel(targetDate),
                onTap: () async {
                  final picked = await _pickDate(context, targetDate);
                  if (picked != null) setLocal(() => targetDate = picked);
                },
              ),
              const SizedBox(height: 14),
              FilledButton(
                onPressed: () async {
                  if (title.text.trim().isEmpty) return;
                  await state.addOrUpdateProject(ProjectItem(
                    id: project?.id ?? DateTime.now().microsecondsSinceEpoch,
                    title: title.text.trim(),
                    description: description.text.trim(),
                    targetDate: targetDate,
                  ));
                  if (context.mounted) Navigator.pop(context);
                },
                child: const Text('Salvar projeto'),
              ),
              if (project != null) ...[
                const SizedBox(height: 8),
                TextButton.icon(
                  style: TextButton.styleFrom(
                    foregroundColor: Theme.of(context).colorScheme.error,
                  ),
                  onPressed: () async {
                    await state.deleteProject(project);
                    if (context.mounted) Navigator.pop(context);
                  },
                  icon: const Icon(Icons.delete_outline_rounded),
                  label: const Text('Excluir projeto'),
                ),
              ],
            ],
          ),
        ),
      ),
    ),
  );
  title.dispose();
  description.dispose();
}

class _PickerTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final VoidCallback onTap;
  final VoidCallback? onClear;
  const _PickerTile({
    required this.icon,
    required this.label,
    required this.value,
    required this.onTap,
    this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Theme.of(context).inputDecorationTheme.fillColor,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 9, 8, 9),
          child: Row(
            children: [
              Icon(icon, size: 20, color: Theme.of(context).colorScheme.primary),
              const SizedBox(width: 9),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(label, style: Theme.of(context).textTheme.bodySmall),
                    const SizedBox(height: 2),
                    Text(value, maxLines: 1, overflow: TextOverflow.ellipsis),
                  ],
                ),
              ),
              if (onClear != null)
                IconButton(
                  visualDensity: VisualDensity.compact,
                  onPressed: onClear,
                  icon: const Icon(Icons.close_rounded, size: 18),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
