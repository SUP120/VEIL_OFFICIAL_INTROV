"""
fix_veil_profile.py  —  run from project root:  python fix_veil_profile.py
Appends missing VHMemberPhoto, AvatarSquircle, ModernMenuRow, MenuRow helpers.
"""

FILE = r"app\src\main\java\com\ns\findyourintroverfriend\home\VeilHomeScreen.kt"

HELPERS = '''

// ── Shared helpers (used in HomeTab sheet + ProfileTab) ────────────────────────

@Composable
private fun VHMemberPhoto(
    photoUrl     : String,
    contentScale : ContentScale = ContentScale.Crop,
    modifier     : Modifier = Modifier
) {
    coil.compose.AsyncImage(
        model              = photoUrl,
        contentDescription = null,
        contentScale       = contentScale,
        modifier           = modifier
    )
}

@Composable
private fun AvatarSquircle(state: VeilHomeUiState, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HAccentSoft)
            .border(1.5.dp, HAccent.copy(0.25f), RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (state.photoUrl.isNotBlank() && !state.isLoading)
            VHMemberPhoto(state.photoUrl, ContentScale.Crop, Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
        else
            Text(state.emoji.ifBlank { "\\u25CB" }, fontSize = 22.sp)
    }
}

@Composable
private fun ModernMenuRow(
    icon     : String,
    label    : String,
    sublabel : String?,
    tint     : Color,
    onClick  : () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(icon, fontSize = 20.sp, color = tint)
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = HPrimary)
            if (sublabel != null)
                Text(sublabel, fontSize = 12.sp, color = HSecondary, lineHeight = 16.sp)
        }
        Text("\\u203A", fontSize = 20.sp, color = HMuted)
    }
}

@Composable
private fun MenuRow(
    icon     : String,
    label    : String,
    sublabel : String?,
    tint     : Color,
    onClick  : () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(icon, fontSize = 18.sp, color = tint)
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HPrimary)
            if (sublabel != null) Text(sublabel, fontSize = 11.sp, color = HSecondary)
        }
        Text("\\u203A", fontSize = 18.sp, color = HMuted)
    }
}
'''

with open(FILE, encoding="utf-8", errors="replace") as f:
    content = f.read()

# Only add if not already present
if "fun VHMemberPhoto" in content:
    print("VHMemberPhoto already exists — nothing to do.")
else:
    with open(FILE, "a", encoding="utf-8") as f:
        f.write(HELPERS)
    print("Appended VHMemberPhoto, AvatarSquircle, ModernMenuRow, MenuRow helpers.")

print("Done! Rebuild the project.")
