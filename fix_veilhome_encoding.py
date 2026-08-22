"""
fix_veilhome_encoding.py
Run this from the project root:  python fix_veilhome_encoding.py

Detects whether VeilHomeScreen.kt is UTF-16LE / UTF-16BE / UTF-8-BOM,
converts it cleanly to UTF-8 (no BOM), which is what the Kotlin compiler expects.
"""

import os

path = r'app\src\main\java\com\ns\findyourintroverfriend\home\VeilHomeScreen.kt'

with open(path, 'rb') as f:
    raw = f.read()

# ── Detect & decode ───────────────────────────────────────────────────────────
if raw[:2] == b'\xff\xfe':
    encoding = 'utf-16-le'
    content  = raw[2:].decode('utf-16-le')
    print(f'[INFO] Detected UTF-16LE (BOM). Converting to UTF-8...')
elif raw[:2] == b'\xfe\xff':
    encoding = 'utf-16-be'
    content  = raw[2:].decode('utf-16-be')
    print(f'[INFO] Detected UTF-16BE (BOM). Converting to UTF-8...')
elif raw[:3] == b'\xef\xbb\xbf':
    encoding = 'utf-8-sig'
    content  = raw[3:].decode('utf-8')
    print(f'[INFO] Detected UTF-8 with BOM. Stripping BOM...')
else:
    try:
        content  = raw.decode('utf-8')
        encoding = 'utf-8'
        print(f'[INFO] File is already clean UTF-8. Checking for corrupt lines...')
    except UnicodeDecodeError:
        content  = raw.decode('latin-1')
        encoding = 'latin-1'
        print(f'[WARN] Detected Latin-1 / binary garbage. Attempting recovery...')

# ── Report line 5875 ─────────────────────────────────────────────────────────
lines = content.splitlines(keepends=True)
print(f'[INFO] Total lines: {len(lines)}')
if len(lines) >= 5875:
    l = lines[5874]
    print(f'[INFO] Line 5875 length: {len(l)} chars')
    print(f'[INFO] Line 5875 preview: {repr(l[:120])}')

# ── Write back as clean UTF-8 (no BOM) ───────────────────────────────────────
backup = path + '.bak'
os.replace(path, backup)
print(f'[INFO] Original backed up to: {backup}')

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)

print(f'[OK]  Written as UTF-8: {path}')
print('[DONE] Now rebuild the project in Android Studio.')
