package com.ns.findyourintroverfriend.aifriend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ns.findyourintroverfriend.ui.theme.currentProfilePalette

@Composable
fun VeilFriendsLockedScreen(onBack: () -> Unit) {
    val colors = currentProfilePalette
    Scaffold(
        containerColor = colors.background,
        topBar = {
            AiTopBar(onBack) {
                Text("VEIL Friends AI", color = colors.text, style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(
                color = colors.card,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = colors.accent)
                    Text("AI chats are on hold", color = colors.text, style = MaterialTheme.typography.headlineSmall)
                    Text("Check back in a future VEIL update.", color = colors.secondaryText)
                }
            }
        }
    }
}

@Composable
fun VeilFriendsScreen(onBack: () -> Unit, onOpenFriend: (String) -> Unit) {
    val colors = currentProfilePalette
    Scaffold(
        containerColor = colors.background,
        topBar = {
            AiTopBar(onBack = onBack) {
                Text("VEIL Friends AI", style = MaterialTheme.typography.titleLarge, color = colors.text)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = colors.tintedSurface,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Find someone to talk to",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.text
                        )
                        Text(
                            "Choose an AI friend for a quiet check-in, a laugh, or a little practice before a real conversation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText
                        )
                        Text(
                            "20 free replies per account · 1 cookie per reply after that",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accent
                        )
                    }
                }
            }
            items(VeilFriends.all, key = { it.id }) { friend ->
                Surface(
                    onClick = { onOpenFriend(friend.id) },
                    color = colors.card,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AiPortrait(friend, 60.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                friend.name,
                                color = colors.text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(friend.style, color = colors.accent, style = MaterialTheme.typography.labelMedium)
                            Text(
                                friend.description,
                                color = colors.secondaryText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.secondaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AiPortrait(friend: AiFriend, size: Dp) {
    val colors = currentProfilePalette
    Box(Modifier.size(size).clip(CircleShape).background(colors.tintedSurface)) {
        Image(
            painter = painterResource(friend.portrait),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
internal fun AiTopBar(
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    title: @Composable () -> Unit
) {
    val colors = currentProfilePalette
    Surface(color = colors.card) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) { title() }
            actions()
        }
    }
}
