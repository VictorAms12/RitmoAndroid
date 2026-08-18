from pathlib import Path


def rd(path):
    return Path(path).read_text(encoding='utf-8')


def wr(path, text):
    Path(path).write_text(text, encoding='utf-8', newline='\n')

# Calendar occupancy only needs counts; remove completion-score work that no
# longer affects rendering after the habit-only indicator fix.
p = 'lib/screens/calendar_page.dart'
s = rd(p)
s = s.replace("    final taskDone = <String, int>{};\n", "")
s = s.replace("      if (task.status == 'done') taskDone[date] = (taskDone[date] ?? 0) + 1;\n", "")
s = s.replace("    final routineDone = <String, int>{};\n", "")
s = s.replace("        if (routine.doneOn(date)) {\n          routineDone[date] = (routineDone[date] ?? 0) + 1;\n        }\n", "")
old = """        final done = taskDone[date] ?? 0;
        final routineCount = routineTotals[date] ?? 0;
        final routineCompleted = routineDone[date] ?? 0;
        final taskScore = count == 0 ? 100 : (done * 100 / count).round();
        final routineScore = routineCount == 0
            ? 100
            : (routineCompleted * 100 / routineCount).round();
        final score = count == 0 && routineCount == 0
            ? 0
            : count == 0
                ? routineScore
                : routineCount == 0
                    ? taskScore
                    : (taskScore * .65 + routineScore * .35).round();
"""
new = """        final routineCount = routineTotals[date] ?? 0;
"""
if old in s:
    s = s.replace(old, new, 1)
wr(p, s)

# Use the normalized focus mode consistently in both Flutter and native state.
p = 'lib/core/app_state.dart'
s = rd(p)
s = s.replace("      mode: mode,\n      plannedMinutes: safeMinutes,", "      mode: focusMode,\n      plannedMinutes: safeMinutes,", 1)
wr(p, s)

# Keep release history accurate while current-version references remain 3.4.3.
p = 'README.md'
s = rd(p)
s = s.replace("Na v3.4.3, a camada nativa Android foi alinhada ao **schema 8**", "Na v3.4.0, a camada nativa Android foi alinhada ao **schema 8**")
s = s.replace("A partir da versão 3.4.3, Android e Windows compartilham a mesma base Flutter.", "A partir da versão 3.4.0, Android e Windows compartilham a mesma base Flutter.")
wr(p, s)

print('Ritmo 3.4.3 post-polish applied')
