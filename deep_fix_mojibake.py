"""
deep_fix_mojibake.py  — Definitive mojibake repair for VeilHomeScreen.kt
--------------------------------------------------------------------------
Run from project root:  python deep_fix_mojibake.py

Strategy:
  1. Long comment lines (>300 chars) that contain a readable English label word
     → replaced with a clean ASCII separator:  // ─────── Label ───────
  2. Inline mojibake runs (5+ consecutive non-ASCII chars) within strings/comments
     → stripped out (they were emoji decorators / arrow symbols that got corrupted)
  3. Specific known corrupted strings → fixed with correct ASCII content
  4. Saves as clean UTF-8, keeps a backup
"""

import os, re

TARGET = r'app\src\main\java\com\ns\findyourintroverfriend\home\VeilHomeScreen.kt'

# ── Read ──────────────────────────────────────────────────────────────────────
with open(TARGET, 'rb') as f:
    raw = f.read()

if raw[:2] == b'\xff\xfe':
    content = raw[2:].decode('utf-16-le')
elif raw[:2] == b'\xfe\xff':
    content = raw[2:].decode('utf-16-be')
elif raw[:3] == b'\xef\xbb\xbf':
    content = raw[3:].decode('utf-8')
else:
    content = raw.decode('utf-8')

lines = content.split('\n')
print(f'[INFO] Read {len(lines)} lines')

# ── Pass 1: Fix long corrupted comment lines ──────────────────────────────────
# Pattern: line starts with // and is very long with lots of non-ASCII
# Extract the English label word(s) in the middle and rebuild the separator

SECTION_LABELS = [
    'Data model', 'ViewModel', 'Colors', 'BackgroundQueueBanner',
    'VeilHomeScreen', 'UCrop', 'Gallery picker', 'gallery picker',
    'Profile tab', 'ProfileTab', 'Stats', 'stat', 'cookie', 'Cookie',
    '30-cookie', 'welcome gift', 'Welcome', 'sign out', 'Sign out',
    'Sign Out', 'Logout', 'logout', 'Delete', 'delete', 'Account',
    'account', 'features', 'Features', 'Settings', 'settings',
    'Feed tab', 'Swipe tab', 'Games tab', 'Tab 0', 'Tab 1', 'Tab 2', 'Tab 3',
    'UCrop result', 'result receiver', 'cropper', 'photo', 'Photo',
    'queue', 'Queue', 'Room', 'room', 'timer', 'Timer',
    'dialog', 'Dialog', 'sheet', 'Sheet', 'banner', 'Banner',
]

fixed_comment_lines = 0
new_lines = []
for line in lines:
    stripped = line.strip()
    # Detect: starts with // and is extremely long (>250 chars content after //)
    if stripped.startswith('//') and len(stripped) > 250:
        # Count non-ASCII chars
        non_ascii = sum(1 for c in stripped if ord(c) > 0x7F)
        ascii_count = len(stripped) - non_ascii
        # If >60% non-ASCII, it's a corrupted separator
        if non_ascii > len(stripped) * 0.4:
            # Try to find a readable label in the middle
            label = None
            for candidate in SECTION_LABELS:
                if candidate.lower() in stripped.lower():
                    # Find where the label appears
                    idx = stripped.lower().index(candidate.lower())
                    # Get the original casing
                    label = stripped[idx:idx+len(candidate)]
                    break
            if label:
                indent = line[:len(line) - len(line.lstrip())]
                new_lines.append(f'{indent}// ─────────────────────────────────────── {label} ───────────────────────────────────────')
            else:
                # No recognizable label — just make a clean separator
                indent = line[:len(line) - len(line.lstrip())]
                new_lines.append(f'{indent}// ─────────────────────────────────────────────────────────────────────────────────────────')
            fixed_comment_lines += 1
            continue
    new_lines.append(line)

content = '\n'.join(new_lines)
print(f'[INFO] Fixed {fixed_comment_lines} corrupted comment separator lines')

# ── Pass 2: Inline replacements — known specific corrupted strings ─────────────
# These are direct string-level fixes for known occurrences

DIRECT_REPLACEMENTS = [
    # Emoji default field values (corrupted moon/star emoji)
    ('"├â╞Æ├é┬░├âΓÇª├é┬╕├âΓÇª├éΓÇÖ├â┬ó├éΓÇ₧├é┬ó"', '"\\uD83C\\uDF19"'),   # 🌙 moon
    # Arrow in error message
    ('configured ├â╞Æ├é┬ó├â┬ó├éΓÇÜ├é┬¼├â┬ó├éΓé¼├é┬¥ check', 'configured - check'),
    # Ellipsis after "circle"
    ('circle├â╞Æ├é┬ó├â┬ó├éΓÇÜ├é┬¼├âΓÇÜ├é┬ª', 'circle...'),
    # Middle dot separator in queue text
    ('queue ├é┬╖ ', 'queue · '),
    # Bell/notification emoji at end of queue text
    ('notified ├â╞Æ├é┬░├âΓÇª├é┬╕├â┬ó├éΓé¼├é┬¥├â┬ó├éΓé¼├é┬¥"', 'notified"'),
    # Inline arrows in short comments  (Tab 0 → Feed etc.)
    ('Tab 0 ├â╞Æ├é┬ó├â┬ó├éΓÇÜ├é┬¼├â┬ó├éΓé¼├é┬¥ Feed',     'Tab 0 — Feed'),
    ('Tab 1 ├â╞Æ├é┬ó├â┬ó├éΓÇÜ├é┬¼├â┬ó├éΓé¼├é┬¥ Swipe',    'Tab 1 — Swipe'),
    ('Tab 2 ├â╞Æ├é┬ó├â┬ó├éΓÇÜ├é┬¼├â┬ó├éΓé¼├é┬¥ Games',    'Tab 2 — Games'),
    ('Tab 3 ├â╞Æ├é┬ó├â┬ó├éΓÇÜ├é┬¼├â┬ó├éΓé¼├é┬¥ Profile',  'Tab 3 — Profile'),
    # Arrow after "Gallery picker"
    ('Gallery picker ├â╞Æ├é┬ó├â┬ó├éΓé¼├é┬á├â┬ó├éΓé¼├éΓäó launches UCrop', 'Gallery picker — launches UCrop'),
    # Arrow in 30-cookie comment
    ('30-cookie welcome gift (shows once per account via Firestore flag) ├â╞Æ├é┬ó├â┬ó├éΓé¼├é┬¥├â┬ó├éΓÇÜ├é┬¼├â╞Æ├é┬ó├â┬ó├éΓé¼├é┬¥├â┬ó├éΓÇÜ├é┬¼├â╞Æ├é┬ó├â┬ó├éΓé¼├é┬¥├â┬ó├éΓÇÜ├é┬¼├â╞Æ├é┬ó├â┬ó├éΓé¼├é┬¥├â┬ó├éΓÇÜ├é┬¼',
     '30-cookie welcome gift (shows once per account via Firestore flag)'),
]

direct_count = 0
for old, new in DIRECT_REPLACEMENTS:
    if old in content:
        content = content.replace(old, new)
        direct_count += 1
        print(f'  [FIX] Direct replacement: {repr(old[:50])}...')

print(f'[INFO] Applied {direct_count} direct string replacements')

# ── Pass 3: Generic regex — strip remaining runs of 5+ consecutive non-ASCII ──
# These are in-string emoji decorators that got corrupted.
# ASCII Kotlin keywords/identifiers are unaffected (they're all 0x00-0x7F).

MOJIBAKE_PATTERN = re.compile(r'[^\x00-\x7F]{5,}')

def clean_run(m):
    """Replace a long non-ASCII run with nothing (was a decorative emoji/arrow)."""
    return ''

before_len = len(content)
content = MOJIBAKE_PATTERN.sub(clean_run, content)
after_len = len(content)
removed = before_len - after_len
print(f'[INFO] Regex pass: removed {removed} bytes of mojibake (5+ char runs)')

# ── Pass 4: Strip remaining isolated 2-4 char non-ASCII clusters that look like fragments ──
# Only strip if surrounded by ASCII (not inside legitimate Unicode strings)
FRAG_PATTERN = re.compile(r'(?<=[^\x80-\xFF])([^\x00-\x7F]{2,4})(?=[^\x80-\xFF])')

before_len = len(content)
content = FRAG_PATTERN.sub('', content)
after_len = len(content)
print(f'[INFO] Fragment pass: removed {before_len - after_len} bytes of short fragments')

# ── Pass 5: Clean up double spaces left by removed fragments ──────────────────
content = re.sub(r'  +', ' ', content)          # 2+ spaces → 1
content = re.sub(r'\n{4,}', '\n\n\n', content)  # 4+ blank lines → 2

# ── Save ──────────────────────────────────────────────────────────────────────
backup = TARGET + '.deep_backup'
with open(backup, 'wb') as f:
    f.write(raw)
print(f'[INFO] Backed up original to: {backup}')

with open(TARGET, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)

final_lines = content.count('\n')
print(f'[OK]  Written: {TARGET}  ({final_lines} lines)')
print()
print('Next steps in Android Studio:')
print('  1. File > Invalidate Caches > Invalidate and Restart')
print('  2. Run app (Shift+F10)')
print('[DONE]')
