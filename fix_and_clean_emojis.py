"""
fix_and_clean_emojis.py  (v2 — smarter)
----------------------------------------
Run from project root:  python fix_and_clean_emojis.py

Does three things in one pass:
  1. Re-encodes VeilHomeScreen.kt from UTF-16LE to clean UTF-8
  2. Replaces every emoji used in TEXT labels with a clean plain-text label
  3. Kotlin string-literal emojis that appear as icon decorators are removed entirely

Safe: backs up the original before touching anything.
"""

import os, re

TARGET = r'app\src\main\java\com\ns\findyourintroverfriend\home\VeilHomeScreen.kt'

# ── Step 1: Detect & decode ───────────────────────────────────────────────────
with open(TARGET, 'rb') as f:
    raw = f.read()

if raw[:2] == b'\xff\xfe':
    content = raw[2:].decode('utf-16-le')
    print('[INFO] Detected UTF-16LE BOM — converting to UTF-8')
elif raw[:2] == b'\xfe\xff':
    content = raw[2:].decode('utf-16-be')
    print('[INFO] Detected UTF-16BE BOM — converting to UTF-8')
elif raw[:3] == b'\xef\xbb\xbf':
    content = raw[3:].decode('utf-8')
    print('[INFO] Detected UTF-8 BOM — stripping BOM')
else:
    try:
        content = raw.decode('utf-8')
        print('[INFO] File is UTF-8 — checking for emoji/corrupt lines...')
    except UnicodeDecodeError:
        content = raw.decode('latin-1')
        print('[WARN] Binary garbage detected — using Latin-1 fallback')

# ── Step 2: Exact emoji → clean label replacements ────────────────────────────
# These are the most common emojis found in profile-page Kotlin string literals.
# Each is replaced with a plain ASCII string that looks good as a UI label.
LABEL_REPLACEMENTS = [
    # --- Profile header labels ---
    ('\U0001F464', 'Profile'),       # 👤
    ('\U0001F9D1', 'User'),          # 🧑
    ('\U0001F31F', 'Star'),          # 🌟
    ('\u2B50',     'Star'),          # ⭐
    ('\U0001F4AB', ''),              # 💫 (usually decorative — remove)
    ('\u2728',     ''),              # ✨ (decorative — remove)

    # --- Settings row labels ---
    ('\u2699',     ''),              # ⚙ Settings gear — remove (icon handles it)
    ('\U0001F527', ''),              # 🔧 wrench — remove
    ('\U0001F6E1', ''),              # 🛡 shield — remove
    ('\U0001F512', ''),              # 🔒 lock — remove
    ('\U0001F513', ''),              # 🔓 unlock — remove

    # --- Info / about labels ---
    ('\U0001F4AC', 'Messages'),      # 💬
    ('\U0001F4AD', 'Thoughts'),      # 💭
    ('\U0001F9E0', 'Mind'),          # 🧠
    ('\U0001F4A1', 'Idea'),          # 💡
    ('\U0001F4DA', 'Reads'),         # 📚
    ('\U0001F3B5', 'Music'),         # 🎵
    ('\U0001F3A8', 'Art'),           # 🎨
    ('\U0001F3AE', 'Gaming'),        # 🎮
    ('\U0001F374', 'Food'),          # 🍴
    ('\u2615',     'Coffee'),        # ☕
    ('\U0001F333', 'Nature'),        # 🌳
    ('\U0001F319', 'Night'),         # 🌙
    ('\u2600',     'Sunny'),         # ☀
    ('\U0001F30C', 'Dreams'),        # 🌌

    # --- Social / vibe labels ---
    ('\u2764',     ''),              # ❤ (decorative heart — remove)
    ('\U0001F49C', ''),              # 💜
    ('\U0001F49A', ''),              # 💚
    ('\U0001F499', ''),              # 💙
    ('\U0001F497', ''),              # 💗
    ('\U0001F496', ''),              # 💖
    ('\U0001F495', ''),              # 💕
    ('\U0001F493', ''),              # 💓
    ('\U0001F48C', 'Love'),          # 💌

    # --- Location / identity ---
    ('\U0001F4CD', ''),              # 📍 (remove — location icon is usually svg)
    ('\U0001F3E0', ''),              # 🏠
    ('\U0001F30D', 'Global'),        # 🌍
    ('\U0001F30E', 'Global'),        # 🌎
    ('\U0001F30F', 'Global'),        # 🌏
    ('\U0001F310', 'Website'),       # 🌐
    ('\u2642',     '(M)'),           # ♂ male — replace with text
    ('\u2640',     '(F)'),           # ♀ female — replace with text
    ('\u26A5',     '(NB)'),          # ⚥ non-binary

    # --- Action / badge labels ---
    ('\u270D',     ''),              # ✍ edit pen — remove (button label handles it)
    ('\U0001F4DD', ''),              # 📝
    ('\U0001F5BC', ''),              # 🖼
    ('\U0001F4F8', ''),              # 📸
    ('\U0001F4CE', ''),              # 📎
    ('\U0001F517', ''),              # 🔗
    ('\u2705',     ''),              # ✅ (checkmark — use icon)
    ('\u274C',     ''),              # ❌ (X — use icon)
    ('\u26A0',     'Warning'),       # ⚠
    ('\U0001F6A8', 'Alert'),         # 🚨
    ('\U0001F4E2', 'Notice'),        # 📢
    ('\U0001F5D1', ''),              # 🗑 trash — remove
    ('\U0001F504', ''),              # 🔄 refresh — remove
    ('\U0001F4CA', 'Stats'),         # 📊
    ('\U0001F4C5', 'Date'),          # 📅

    # --- Engagement ---
    ('\U0001F525', ''),              # 🔥 (remove fire — usually decorative)
    ('\U0001F4AA', ''),              # 💪
    ('\U0001F44D', ''),              # 👍
    ('\U0001F44E', ''),              # 👎
    ('\U0001F44F', ''),              # 👏
    ('\U0001F389', ''),              # 🎉
    ('\U0001F381', ''),              # 🎁
    ('\U0001F3C6', 'Trophy'),        # 🏆
    ('\U0001F947', '#1'),            # 🥇
    ('\U0001F948', '#2'),            # 🥈
    ('\U0001F949', '#3'),            # 🥉

    # --- Finance ---
    ('\U0001F4B0', ''),              # 💰 (decorative)
    ('\U0001F4B3', 'Card'),          # 💳
    ('\U0001F6D2', 'Shop'),          # 🛒

    # --- Contact ---
    ('\U0001F4E7', 'Email'),         # 📧
    ('\U0001F4F1', 'Phone'),         # 📱
    ('\U0001F4BB', 'Laptop'),        # 💻

    # --- Skin tone modifiers (strip always) ---
    ('\U0001F3FB', ''),
    ('\U0001F3FC', ''),
    ('\U0001F3FD', ''),
    ('\U0001F3FE', ''),
    ('\U0001F3FF', ''),

    # --- Invisible joiners (strip always) ---
    ('\u200D', ''),                  # zero-width joiner
    ('\uFE0F', ''),                  # variation selector-16
    ('\u200B', ''),                  # zero-width space
]

changed_count = 0
for emoji, label in LABEL_REPLACEMENTS:
    if emoji in content:
        content = content.replace(emoji, label)
        changed_count += 1

print(f'[INFO] Applied {changed_count} emoji replacements')

# ── Step 3: Escape any remaining non-ASCII (>U+007E) as Kotlin \uXXXX ────────
def escape_high_unicode(text: str) -> str:
    """Replace non-ASCII chars not already escaped with \\uXXXX Kotlin escapes."""
    out = []
    i = 0
    while i < len(text):
        ch = text[i]
        cp = ord(ch)
        # Keep ASCII + common Latin range clean
        if cp <= 0x7E:
            out.append(ch)
        elif 0xD800 <= cp <= 0xDBFF:
            # High surrogate — pair with next low surrogate
            if i + 1 < len(text) and 0xDC00 <= ord(text[i+1]) <= 0xDFFF:
                out.append(f'\\u{cp:04X}')
                out.append(f'\\u{ord(text[i+1]):04X}')
                i += 2
                continue
            else:
                out.append(f'\\u{cp:04X}')
        elif cp > 0x7E:
            out.append(f'\\u{cp:04X}')
        i += 1
    return ''.join(out)

content = escape_high_unicode(content)
print('[INFO] Escaped remaining non-ASCII characters as \\uXXXX')

# ── Step 4: Tidy up — remove double-spaces left by stripped emojis ────────────
# e.g. "Edit ✍  Profile" -> "Edit  Profile" -> "Edit Profile"
content = re.sub(r'  +', ' ', content)          # collapse 2+ spaces → 1
content = re.sub(r'" "', '""', content)          # "" "  <- handle in strings too

# ── Step 5: Backup & write ────────────────────────────────────────────────────
backup = TARGET + '.emoji_backup'
with open(backup, 'wb') as f:
    f.write(raw)
print(f'[INFO] Original backed up to: {backup}')

with open(TARGET, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)

print(f'[OK]  Written as clean UTF-8: {TARGET}')
print(f'[OK]  Total lines: {content.count(chr(10))}')
print()
print('Next steps:')
print('  1. File > Invalidate Caches > Invalidate and Restart  (Android Studio)')
print('  2. Run app (Shift+F10)')
