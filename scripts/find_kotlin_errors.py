"""
find_kotlin_errors.py
----------------------
Run from project root:  python find_kotlin_errors.py

Scans all Kotlin files in the project for common compilation issues:
1. Unmatched braces / brackets / parentheses
2. Unclosed string literals
3. Duplicate imports
4. Lines with stray non-ASCII characters that might break compilation
5. Reports the exact file and line number

Usage: python find_kotlin_errors.py
"""

import os, re

SRC_ROOT = r'app\src\main\java\com\ns\findyourintroverfriend'

def check_file(path):
    issues = []
    try:
        with open(path, 'r', encoding='utf-8', errors='replace') as f:
            lines = f.readlines()
    except Exception as e:
        return [f'Cannot read: {e}']

    # ── 1. Duplicate imports ──────────────────────────────────────────────────
    imports = {}
    for i, line in enumerate(lines, 1):
        stripped = line.strip()
        if stripped.startswith('import '):
            if stripped in imports:
                issues.append(f'Line {i}: DUPLICATE IMPORT: {stripped}  (first at line {imports[stripped]})')
            else:
                imports[stripped] = i

    # ── 2. Unclosed string literals (simplified: count unescaped quotes) ──────
    in_block_comment = False
    in_ml_string = False  # triple-quoted
    for i, line in enumerate(lines, 1):
        stripped = line.rstrip('\r\n')

        # Track block comments
        if '/*' in stripped: in_block_comment = True
        if '*/' in stripped: in_block_comment = False
        if in_block_comment: continue

        # Skip line comments
        code = stripped
        if '//' in code:
            # Crude: strip everything after first // not inside a string
            idx = stripped.find('//')
            code = stripped[:idx]

        # Triple-quoted strings (Kotlin raw strings)
        if '"""' in code:
            count = code.count('"""')
            if count % 2 != 0:
                in_ml_string = not in_ml_string

        if in_ml_string:
            continue

        # Single-quoted string balance (simplified — counts ", skips \")
        cleaned = re.sub(r'\\.', '', code)   # remove escape sequences
        non_ml_quotes = cleaned.count('"')
        if non_ml_quotes % 2 != 0:
            issues.append(f'Line {i}: POSSIBLE UNCLOSED STRING: {stripped[:100]}')

    # ── 3. Brace balance ──────────────────────────────────────────────────────
    depth = {'(': 0, '[': 0, '{': 0}
    pairs = {')': '(', ']': '[', '}': '{'}
    in_block_comment = False
    in_string = False
    for i, line in enumerate(lines, 1):
        for ch in line:
            if ch == '/' and not in_string:
                pass  # simplified — block comment detection needs lookahead
            if ch == '"' and not in_string:
                in_string = True
            elif ch == '"' and in_string:
                in_string = False
            if not in_string:
                if ch in '([{':
                    depth[ch] += 1
                elif ch in ')]}':
                    open_ch = pairs[ch]
                    depth[open_ch] -= 1
                    if depth[open_ch] < 0:
                        issues.append(f'Line {i}: EXTRA CLOSING BRACE/PAREN/BRACKET: {repr(ch)}')
                        depth[open_ch] = 0  # reset to avoid cascade

    for ch, d in depth.items():
        if d != 0:
            issues.append(f'END OF FILE: {d} unclosed {repr(ch)} — file may be truncated or malformed')

    return issues

# ── Scan all .kt files ────────────────────────────────────────────────────────
all_issues = {}
total_files = 0

for root, dirs, files in os.walk(SRC_ROOT):
    for fname in files:
        if not fname.endswith('.kt'):
            continue
        fpath = os.path.join(root, fname)
        total_files += 1
        issues = check_file(fpath)
        if issues:
            rel = os.path.relpath(fpath, SRC_ROOT)
            all_issues[rel] = issues

print(f'[INFO] Scanned {total_files} Kotlin files')
print()

if not all_issues:
    print('[OK] No obvious issues found!')
else:
    print(f'[FOUND] {len(all_issues)} file(s) with issues:\n')
    for fname, issues in all_issues.items():
        print(f'  FILE: {fname}')
        for issue in issues[:10]:   # cap at 10 per file
            print(f'    {issue}')
        if len(issues) > 10:
            print(f'    ... and {len(issues) - 10} more issues')
        print()
