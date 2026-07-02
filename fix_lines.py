with open('app/src/main/java/com/example/ui/TerminalScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip_next = False
for i, line in enumerate(lines):
    if skip_next:
        skip_next = False
        continue
    if "val terminalLines = terminalOutput.split(" in line:
        new_lines.append('    val terminalLines = terminalOutput.split("\\n")\n')
        skip_next = True
    else:
        new_lines.append(line)

with open('app/src/main/java/com/example/ui/TerminalScreen.kt', 'w') as f:
    f.write("".join(new_lines))
