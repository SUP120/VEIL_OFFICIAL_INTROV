"""
fix_emojis.py - SAFE RESTORE
Repairs the VeilHomeScreen.kt file:
  1. Fixes triple \r\r\r line endings back to normal \r\n or \n
  2. Replaces all non-ASCII garbled sequences with clean ASCII alternatives
  3. Saves as proper UTF-8

Run from project root:  python fix_emojis.py
"""

FILE = r"app\src\main\java\com\ns\findyourintroverfriend\home\VeilHomeScreen.kt"

with open(FILE, "rb") as f:
    raw = f.read()

print(f"File size: {len(raw)} bytes")

# Step 1: Convert all \r sequences to single \n
# The file has \r\r\r between lines; normalise to \n
content = raw.decode("latin-1")
# Replace triple \r\r\r\n or just \r\r\r  with single newline
import re
content = re.sub(r'\r+\n', '\n', content)  # \r...\n -> \n
content = re.sub(r'\r+', '\n', content)    # bare \r... -> \n

print(f"Lines after newline fix: {content.count(chr(10))}")

# Step 2: Un-double-encode UTF-8 
# Many sequences like "ÃƒÂ°Ã…Â¸..." are double-encoded UTF-8.
# Encode back to latin-1 bytes, then decode as utf-8 to recover originals.
try:
    recovered = content.encode("latin-1", errors="replace").decode("utf-8", errors="replace")
    # Check if recovery worked by looking for known strings
    if "VeilHomeUiState" in recovered and "ProfileTab" in recovered:
        content = recovered
        print("Double-encoding fixed successfully.")
    else:
        print("Recovery check failed, keeping normalised version.")
except Exception as e:
    print(f"Recovery error: {e}, continuing.")

# Step 3: Replace all remaining garbled/emoji text in string literals with clean ASCII
# Target the specific display strings only (not import paths, class names, etc.)
SAFE_REPLACEMENTS = [
    # Stat card emoji values - replace with clean text
    # Pattern: top = "...emoji...$cookieBalance..."  
    (r'top\s*=\s*"[^"]*cookieBalance[^"]*"', 'top = "$cookieBalance"'),
    # Moon stat
    (r'top\s*=\s*"[^\x00-\x7F][^"]*",\s*bottom\s*=\s*"ANONYMOUS"', 'top = "*", bottom = "ANONYMOUS"'),
    # Verified stat  
    (r'top\s*=\s*"[^\x00-\x7F][^"]*",\s*bottom\s*=\s*"VERIFIED"', 'top = "+", bottom = "VERIFIED"'),
    
    # Menu row icons with non-ASCII - replace icon param strings that contain non-ASCII
    # Cookie store icon
    (r'icon\s*=\s*"[^\x00-\x7F][^"]*",\s*label\s*=\s*"Cookie Store"', 'icon = "C", label = "Cookie Store"'),
    # Invite Friends icon
    (r'icon\s*=\s*"[^\x00-\x7F][^"]*",\s*label\s*=\s*"Invite Friends"', 'icon = "+", label = "Invite Friends"'),
    # Dev Panel icon
    (r'icon\s*=\s*"[^\x00-\x7F][^"]*",\s*label\s*=\s*"Dev Panel"', 'icon = "/", label = "Dev Panel"'),
    # Sign Out icon
    (r'icon\s*=\s*"[^\x00-\x7F][^"]*",\s*label\s*=\s*"Sign Out"', 'icon = "<", label = "Sign Out"'),
    # Delete Account icon
    (r'icon\s*=\s*"[^\x00-\x7F][^"]*",\s*label\s*=\s*"Delete Account"', 'icon = "x", label = "Delete Account"'),
    
    # ModernMenuRow icons in the dialog sheet
    (r'icon\s*=\s*"[^\x00-\x7F][^"]*",\s*label\s*=\s*"Dev Panel"', 'icon = "/", label = "Dev Panel"'),
    (r'icon\s*=\s*"[^\x00-\x7F][^"]*",\s*label\s*=\s*"View Profile"', 'icon = ">", label = "View Profile"'),
    # Sign Out in dialog
    (r'icon\s*=\s*"[^\x00-\x7F][^"]*",\s*label\s*=\s*"Sign Out",\s*sublabel\s*=\s*null,\s*tint', 'icon = "<", label = "Sign Out", sublabel = null, tint'),
    
    # Membership badge text
    (r'"Introvert\s+[^\x00-\x7F]\s*Veil Member"', '"Introvert - Veil Member"'),
    (r'"Introvert [^\x00-\x7F\w][^\x00-\x7F\w]? Veil Member"', '"Introvert - Veil Member"'),

    # Footer text with emoji/special chars
    (r'"Veil [^\x00-\x7F\w]+ made for introverts[^"]*"', '"Veil - made for introverts"'),
    
    # Error icon
    (r'Text\("[^\x00-\x7F][^"]*",\s*fontSize\s*=\s*13\.sp,\s*color\s*=\s*PPrimary\)', 'Text("!", fontSize = 13.sp, color = PPrimary)'),
    
    # Chevron in PMenuRow
    (r'Text\("[^\x00-\x7F][^"]*",\s*fontSize\s*=\s*22\.sp,\s*color\s*=\s*PMuted', 'Text(">", fontSize = 22.sp, color = PMuted'),
    # Chevron in ModernMenuRow / MenuRow (20sp)
    (r'Text\("[^\x00-\x7F][^"]*",\s*fontSize\s*=\s*20\.sp,\s*color\s*=\s*HMuted', 'Text(">", fontSize = 20.sp, color = HMuted'),
    (r'Text\("[^\x00-\x7F][^"]*",\s*fontSize\s*=\s*18\.sp,\s*color\s*=\s*HMuted', 'Text(">", fontSize = 18.sp, color = HMuted'),

    # Separator dots in footer (  ·  )
    (r'"  [^\x00-\x7F][^\x00-\x7F]?  "',  '"  -  "'),
    
    # Middle dot in "Buy cookies · send compliments"
    (r'sub\s*=\s*"Buy cookies [^\x00-\x7F][^\x00-\x7F]? send compliments"', 'sub = "Buy cookies - send compliments"'),
    
    # "Finding your circle…" in background banner
    (r'"Finding your circle[^\x00-\x7F][^"]*"', '"Finding your circle..."'),
    
    # Notification text in background banner
    (r'"[^"]*in queue [^\x00-\x7F][^\x00-\x7F]? [^"]*notified[^"]*"', '"In queue - You will be notified"'),
    
    # Emoji default in data class / viewmodel ("🎃" = emoji)
    (r'val emoji\s*:\s*String\s*=\s*"[^\x00-\x7F][^"]*"', 'val emoji            : String  = ":)"'),

    # Avatar fallback text in AvatarSquircle
    (r'emoji\.ifBlank\s*\{\s*"[^\x00-\x7F][^"]*"\s*\}', 'emoji.ifBlank { "o" }'),
    
    # Welcome gift dialog cookie emojis (Text("🍪"...))
    (r'Text\("[^\x00-\x7F][^"]*",\s*fontSize\s*=\s*52\.sp', 'Text(":)", fontSize = 52.sp'),
    (r'Text\("[^\x00-\x7F][^"]*",\s*fontSize\s*=\s*18\.sp\)', 'Text(":)", fontSize = 18.sp)'),
]

changed = 0
for pattern, replacement in SAFE_REPLACEMENTS:
    try:
        new, n = re.subn(pattern, replacement, content)
        if n > 0:
            content = new
            changed += n
            print(f"  [{n}x] {pattern[:60]}")
    except Exception as ex:
        print(f"  SKIP: {pattern[:40]} ({ex})")

# Step 4: Nuclear sweep - strip any remaining non-ASCII from inside string literals
def replace_nonascii_in_string(m):
    s = m.group(0)
    result = ""
    i = 0
    while i < len(s):
        c = s[i]
        if ord(c) > 127:
            # Skip non-ASCII chars
            pass
        else:
            result += c
        i += 1
    # Clean up empty strings that result from full emoji strings
    if result.strip() in ('""', '"  "', '" "'):
        result = '"?"'
    return result

new_content = re.sub(r'"[^"\n]*"', replace_nonascii_in_string, content)
extra = sum(1 for a, b in zip(content, new_content) if a != b)
if extra > 0:
    print(f"  Nuclear: cleaned {extra} remaining non-ASCII chars from strings.")
    content = new_content
    changed += 1

print(f"\nTotal change operations: {changed}")

# Save
with open(FILE, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)

lines = content.count("\n")
print(f"Saved. Lines: {lines}")
print("Now rebuild the project in Android Studio.")
