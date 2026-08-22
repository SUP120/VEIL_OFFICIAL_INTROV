"""
restore_and_fix_encoding_only.py
---------------------------------
Run from project root:  python restore_and_fix_encoding_only.py

This script:
  1. Restores VeilHomeScreen.kt from the .emoji_backup (undoes the bad emoji replacement)
  2. Re-saves it as clean UTF-8 with NO other changes — emojis stay as emojis
  3. That's it. The emojis will display correctly once the file is valid UTF-8.

The weird characters you saw were ONLY because the file was UTF-16LE.
Emojis themselves are fine and will show correctly once encoding is fixed.
"""

import os, shutil

TARGET = r'app\src\main\java\com\ns\findyourintroverfriend\home\VeilHomeScreen.kt'
BACKUP = TARGET + '.emoji_backup'

# ── Check backup exists ───────────────────────────────────────────────────────
if not os.path.exists(BACKUP):
    print('[ERROR] Backup not found:', BACKUP)
    print('        Looking for any .bak file...')
    bak2 = TARGET + '.bak'
    if os.path.exists(bak2):
        BACKUP = bak2
        print(f'[INFO]  Found: {bak2}')
    else:
        print('[FATAL] No backup found. Cannot restore.')
        exit(1)

# ── Read the backup (original file, whatever encoding it was) ─────────────────
with open(BACKUP, 'rb') as f:
    raw = f.read()

print(f'[INFO] Backup read: {len(raw):,} bytes')

# ── Detect encoding ───────────────────────────────────────────────────────────
if raw[:2] == b'\xff\xfe':
    content = raw[2:].decode('utf-16-le')
    print('[INFO] Original was: UTF-16LE with BOM')
elif raw[:2] == b'\xfe\xff':
    content = raw[2:].decode('utf-16-be')
    print('[INFO] Original was: UTF-16BE with BOM')
elif raw[:3] == b'\xef\xbb\xbf':
    content = raw[3:].decode('utf-8')
    print('[INFO] Original was: UTF-8 with BOM')
else:
    try:
        content = raw.decode('utf-8')
        print('[INFO] Original was: UTF-8 (no BOM) — already correct')
    except UnicodeDecodeError:
        content = raw.decode('latin-1')
        print('[WARN] Original was: Latin-1 / binary — doing best-effort recovery')

print(f'[INFO] Total lines: {len(content.splitlines()):,}')

# ── Write back as clean UTF-8, NO changes to content ─────────────────────────
with open(TARGET, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)

print(f'[OK]  Restored and saved as clean UTF-8: {TARGET}')
print()
print('Emojis are kept exactly as they were.')
print('The weird characters you saw were ONLY the UTF-16 encoding issue.')
print()
print('Next steps in Android Studio:')
print('  1. File -> Invalidate Caches -> Invalidate and Restart')
print('  2. Run app (Shift+F10)')
print()
print('[DONE]')
