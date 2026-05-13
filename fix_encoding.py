"""
fix_encoding.py — fixes UTF-8 double-encoded text in VeilHomeScreen.kt
Run from the project root:
    python fix_encoding.py
"""

import os

TARGET = os.path.join(
    "app", "src", "main", "java", "com", "ns",
    "findyourintroverfriend", "home", "VeilHomeScreen.kt"
)

def fix_file(path):
    print(f"Reading: {path}")
    with open(path, "rb") as f:
        raw = f.read()

    # The file was double-encoded: UTF-8 bytes were interpreted as latin-1 and re-saved.
    # Fix: decode as latin-1 (reversing the misread), then re-encode to get original UTF-8.
    try:
        text = raw.decode("latin-1")
        fixed_bytes = text.encode("latin-1")   # gives original UTF-8 byte sequence
        fixed_text  = fixed_bytes.decode("utf-8")  # verify it's valid UTF-8
    except Exception as e:
        print(f"Encoding error: {e}")
        return

    # Back up original
    backup = path + ".bak"
    with open(backup, "wb") as f:
        f.write(raw)
    print(f"Backup saved to: {backup}")

    # Write fixed file as UTF-8
    with open(path, "wb") as f:
        f.write(fixed_bytes)

    print(f"\n✅ Fixed: {path}")
    print(f"\nSample (first 500 chars):\n{fixed_text[:500]}")


if __name__ == "__main__":
    fix_file(TARGET)
