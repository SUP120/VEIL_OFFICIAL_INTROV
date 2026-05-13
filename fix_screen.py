import sys

path = r'app\src\main\java\com\ns\findyourintroverfriend\swipe\ConnectionRoomScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Show lines 346-357 (1-indexed 347-358) with repr
for i in range(346, 358):
    print(f'[{i+1}] {repr(lines[i])}')

print('\n--- Total lines:', len(lines))

# Fix: replace lines 346-353 (0-indexed) = lines 347-354 (1-indexed)
# These are the broken IconButton lines
replacement = [
    '                // Gesture toggle icon\n',
    '                IconButton(onClick = { showGesturePanel = !showGesturePanel; keyboard?.hide() }) {\n',
    '                    Icon(\n',
    '                        Icons.Rounded.AutoAwesome,\n',
    '                        contentDescription = "Love Gestures",\n',
    '                        tint = if (showGesturePanel) Color(0xFFE91E63) else IGSecondary,\n',
    '                        modifier = Modifier.size(22.dp)\n',
    '                    )\n',
    '                }\n',
    '                IconButton(onClick = { showRulesInfo = true }) {\n',
    '                    Icon(Icons.Rounded.Info, null, tint = IGSecondary, modifier = Modifier.size(22.dp))\n',
    '                }\n',
    '                IconButton(onClick = { showReportDialog = true }) {\n',
    '                    Icon(Icons.Rounded.Flag, null, tint = IGRed, modifier = Modifier.size(22.dp))\n',
    '                }\n',
    '            }\n',
    '\n',
]

# Replace lines 346-353 (0-indexed), i.e. 8 lines, with replacement
new_lines = lines[:346] + replacement + lines[354:]

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

print('Done! Written', len(new_lines), 'lines.')
