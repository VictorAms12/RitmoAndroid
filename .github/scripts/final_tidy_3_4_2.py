from pathlib import Path


def rd(path):
    return Path(path).read_text(encoding='utf-8')


def wr(path, text):
    Path(path).write_text(text, encoding='utf-8', newline='\n')

# Calendar: the activity dot only needs counts; remove obsolete score locals.
p='lib/screens/calendar_page.dart'
s=rd(p)
old="""        final taskScore = count == 0 ? 100 : (done * 100 / count).round();
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
if old in s:
    s=s.replace(old,'',1)
# done/routineCompleted are now obsolete too.
s=s.replace("        final done = taskDone[date] ?? 0;\n", "")
s=s.replace("        final routineCompleted = routineDone[date] ?? 0;\n", "")
wr(p,s)

# Since completion maps are no longer needed for the calendar occupancy dot,
# remove their construction as well.
s=rd(p)
s=s.replace("    final taskDone = <String, int>{};\n", "")
s=s.replace("      if (task.status == 'done') taskDone[date] = (taskDone[date] ?? 0) + 1;\n", "")
s=s.replace("    final routineDone = <String, int>{};\n", "")
s=s.replace("        if (routine.doneOn(date)) {\n          routineDone[date] = (routineDone[date] ?? 0) + 1;\n        }\n", "")
wr(p,s)

# Use the normalized mode value consistently when starting native focus.
p='lib/core/app_state.dart'; s=rd(p)
s=s.replace("      mode: mode,\n      plannedMinutes: safeMinutes,", "      mode: focusMode,\n      plannedMinutes: safeMinutes,", 1)
wr(p,s)

# Restore historical version statements in the README while keeping current
# release references at 3.4.2.
p='README.md'; s=rd(p)
s=s.replace("Na v3.4.2, a camada nativa Android foi alinhada ao **schema 8**", "Na v3.4.0, a camada nativa Android foi alinhada ao **schema 8**")
s=s.replace("A partir da versão 3.4.2, Android e Windows compartilham a mesma base Flutter.", "A partir da versão 3.4.0, Android e Windows compartilham a mesma base Flutter.")
wr(p,s)

print('final tidy applied')
