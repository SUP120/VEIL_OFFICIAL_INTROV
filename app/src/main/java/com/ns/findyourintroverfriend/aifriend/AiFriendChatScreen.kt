package com.ns.findyourintroverfriend.aifriend

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  VEIL Friends — AI Chat Screen
//  Painterly ambient background + minimal dark chat UI.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiFriendChatScreen(
    friendId       : String,
    conversationId : String = "new",
    onBack         : () -> Unit,
    vm             : AiFriendViewModel = viewModel(),
) {
    val friend = remember(friendId) { VeilFriends.find(friendId) } ?: run { onBack(); return }

    val messages by vm.messages.collectAsStateWithLifecycle()
    val chatState by vm.chatState.collectAsStateWithLifecycle()
    val memories  by vm.memories.collectAsStateWithLifecycle()

    var inputText       by remember { mutableStateOf("") }
    var showMemories    by remember { mutableStateOf(false) }
    val listState       = rememberLazyListState()
    val scope           = rememberCoroutineScope()
    val isSending       = chatState is ChatState.Sending
    val errorMsg        = (chatState as? ChatState.Error)?.message

    // Init on first composition
    LaunchedEffect(friendId, conversationId) {
        vm.initFriend(friendId, conversationId)
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // ── Memory sheet ──────────────────────────────────────────────────────
    if (showMemories) {
        MemorySheet(
            friend    = friend,
            memories  = memories,
            onDelete  = { id -> vm.deleteMemory(friendId, id) },
            onForget  = { vm.forgetEverything(friendId) },
            onDismiss = { showMemories = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Painterly background ──────────────────────────────────────────
        // This is where you drop in your poetic/painting images.
        // Each friend has its own background asset (e.g. luna_bg, blaze_bg).
        // Until you add the images, it gracefully falls back to a solid color.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Gradient overlay for the mood — each friend has its own accent
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                friendAccentColor(friendId).copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            radius = 800f
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {

            // ── Chat header ───────────────────────────────────────────────
            ChatHeader(
                friend          = friend,
                onBack          = onBack,
                onMemoriesClick = {
                    vm.loadMemories(friendId)
                    showMemories = true
                }
            )

            // ── Messages ──────────────────────────────────────────────────
            LazyColumn(
                state           = listState,
                contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier        = Modifier.weight(1f)
            ) {
                // Welcome message if no messages yet
                if (messages.isEmpty() && !isSending) {
                    item {
                        WelcomeBubble(friend = friend)
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    ChatBubble(
                        message = msg,
                        friend  = friend,
                    )
                }

                // Typing indicator
                if (isSending) {
                    item {
                        TypingIndicator(friend = friend)
                    }
                }
            }

            // ── Error snackbar ────────────────────────────────────────────
            AnimatedVisibility(visible = errorMsg != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A0000))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment      = Alignment.CenterVertically,
                        horizontalArrangement  = Arrangement.SpaceBetween,
                        modifier               = Modifier.fillMaxWidth()
                    ) {
                        Text(errorMsg ?: "", fontSize = 13.sp, color = Color(0xFFFF6B6B), modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearError() }) {
                            Text("OK", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            // ── Input bar ─────────────────────────────────────────────────
            InputBar(
                text      = inputText,
                onChange  = { inputText = it },
                onSend    = {
                    val text = inputText.trim()
                    if (text.isNotBlank() && !isSending) {
                        inputText = ""
                        vm.sendMessage(friendId, text)
                    }
                },
                isSending = isSending,
            )
        }
    }
}

@Composable
private fun ChatHeader(
    friend          : AiFriend,
    onBack          : () -> Unit,
    onMemoriesClick : () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(8.dp))
        // Emoji avatar
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) { Text(friend.emoji, fontSize = 18.sp) }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(friend.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
                Spacer(Modifier.width(4.dp))
                Text(friend.statusLine, fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
            }
        }
        // Memories button
        TextButton(onClick = onMemoriesClick) {
            Text("🧠 Memories", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
    // Divider
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.07f))
    )
}

@Composable
private fun WelcomeBubble(friend: AiFriend) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(friend.emoji, fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text      = "\"${friend.description}\"",
            fontSize  = 15.sp,
            color     = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            modifier  = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "${friend.name}  •  AI Friend",
            fontSize  = 11.sp,
            color     = Color.White.copy(alpha = 0.25f),
        )
    }
}

@Composable
private fun ChatBubble(message: AiMessage, friend: AiFriend) {
    val isUser = message.sender == "user"
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .align(Alignment.Bottom),
                contentAlignment = Alignment.Center
            ) { Text(friend.emoji, fontSize = 14.sp) }
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart    = if (isUser) 18.dp else 4.dp,
                        topEnd      = if (isUser) 4.dp  else 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd   = 18.dp
                    )
                )
                .background(
                    if (isUser) Color.White
                    else        Color(0xFF1C1C1C)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text     = message.content,
                fontSize = 14.sp,
                color    = if (isUser) Color.Black else Color.White.copy(alpha = 0.9f),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun TypingIndicator(friend: AiFriend) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha1 by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(400, 0)), label = "d1")
    val alpha2 by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(400, 133)), label = "d2")
    val alpha3 by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(400, 266)), label = "d3")

    Row(verticalAlignment = Alignment.Bottom) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) { Text(friend.emoji, fontSize = 14.sp) }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(Color(0xFF1C1C1C))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(alpha1, alpha2, alpha3).forEach { a ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .alpha(a)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    text      : String,
    onChange  : (String) -> Unit,
    onSend    : () -> Unit,
    isSending : Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Text input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1C1C1C))
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (text.isEmpty()) {
                    Text("Type something…", fontSize = 14.sp, color = Color.White.copy(alpha = 0.25f))
                }
                BasicTextField(
                    value         = text,
                    onValueChange = onChange,
                    cursorBrush   = SolidColor(Color.White),
                    textStyle     = TextStyle(fontSize = 14.sp, color = Color.White, lineHeight = 20.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier      = Modifier.fillMaxWidth(),
                    maxLines      = 5,
                )
            }

            // Send button
            val canSend = text.isNotBlank() && !isSending
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (canSend) Color.White else Color(0xFF1C1C1C))
                    .clickable(enabled = canSend) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier     = Modifier.size(18.dp),
                        color        = Color.White.copy(alpha = 0.5f),
                        strokeWidth  = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Rounded.Send,
                        contentDescription = "Send",
                        tint               = if (canSend) Color.Black else Color.White.copy(alpha = 0.2f),
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MemorySheet(
    friend    : AiFriend,
    memories  : List<AiMemory>,
    onDelete  : (String) -> Unit,
    onForget  : () -> Unit,
    onDismiss : () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF111111))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    "What ${friend.name} Remembers",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your AI Friend  •  ${memories.size} memories",
                    fontSize = 11.sp,
                    color    = Color.White.copy(alpha = 0.35f)
                )
                Spacer(Modifier.height(16.dp))

                if (memories.isEmpty()) {
                    Text(
                        "Nothing remembered yet.\n${friend.name} will remember important things you share.",
                        fontSize = 13.sp,
                        color    = Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                    ) {
                        memories.forEach { memory ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "✓  ${memory.memoryText}",
                                    fontSize = 13.sp,
                                    color    = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onDelete(memory.id) }, contentPadding = PaddingValues(0.dp)) {
                                    Text("✕", fontSize = 12.sp, color = Color.White.copy(alpha = 0.25f))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onForget(); onDismiss() },
                        modifier = Modifier.weight(1f),
                        border   = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Text("Forget Everything", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape  = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    ) {
                        Text("Done", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ── Each friend gets a subtle accent color for the radial background glow ──
private fun friendAccentColor(friendId: String): Color = when (friendId) {
    "luna"  -> Color(0xFF3D5AFE)   // deep blue
    "blaze" -> Color(0xFFFF6D00)   // warm orange
    "rose"  -> Color(0xFFE91E63)   // soft pink
    "sage"  -> Color(0xFF00BCD4)   // cyan
    "ego"   -> Color(0xFFFFD600)   // gold
    "kira"  -> Color(0xFF9C27B0)   // purple
    "nox"   -> Color(0xFF00E5FF)   // electric teal
    "rex"   -> Color(0xFFFF1744)   // sharp red
    else    -> Color(0xFF424242)
}
