package com.ns.findyourintroverfriend.listening

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val Bg      = Color(0xFFFFFFFF)
private val Black   = Color(0xFF0A0A0A)
private val Gray    = Color(0xFF6B6B6B)
private val Hint    = Color(0xFFB0B0B0)
private val Surface = Color(0xFFF5F5F5)
private val Border  = Color(0xFFE8E8E8)
private val Green   = Color(0xFF22C55E)

// ── Entry: Lobby ──────────────────────────────────────────────────────────────
@Composable
fun ListeningLobbyScreen(
    onBack   : () -> Unit = {},
    onEnterRoom: (roomId: String, amHost: Boolean) -> Unit = { _, _ -> }
) {
    val vm: ListeningRoomViewModel = viewModel()
    val lobbyState by vm.lobbyState.collectAsStateWithLifecycle()
    val roomState  by vm.roomState.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var linkInput  by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var linkError  by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current

    // Navigate once room is active
    LaunchedEffect(roomState) {
        if (roomState is RoomUiState.Active) {
            val s = roomState as RoomUiState.Active
            onEnterRoom(s.room.roomId, s.amHost)
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {

        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = Black, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text("Listening Rooms", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Black)
                Text("Join a room · listen together in sync", fontSize = 12.sp, color = Hint)
            }
            Button(
                onClick = { showCreate = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Black),
                elevation = ButtonDefaults.buttonElevation(0.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Create", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        HorizontalDivider(color = Border)

        when (val s = lobbyState) {
            is LobbyUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Black, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
            }
            is LobbyUiState.Loaded -> {
                val active = s.rooms.filter { it.status != "ended" }
                if (active.isEmpty()) {
                    EmptyLobby(onCreate = { showCreate = true })
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(active, key = { it.roomId }) { room ->
                            RoomCard(
                                room = room,
                                myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
                                onJoin = { vm.joinRoom(room.roomId) {} }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create room dialog
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false; linkError = "" },
            containerColor = Bg,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Create a Room", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Black)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Paste a YouTube link and others can join your room.", fontSize = 13.sp, color = Gray, lineHeight = 18.sp)
                    OutlinedTextField(
                        value = linkInput,
                        onValueChange = { linkInput = it; linkError = "" },
                        placeholder = { Text("YouTube link or video ID", fontSize = 13.sp, color = Hint) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Black.copy(0.4f),
                            unfocusedBorderColor = Border,
                            focusedContainerColor = Surface,
                            unfocusedContainerColor = Surface,
                            cursorColor = Black
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        placeholder = { Text("Song name (optional)", fontSize = 13.sp, color = Hint) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Black.copy(0.4f),
                            unfocusedBorderColor = Border,
                            focusedContainerColor = Surface,
                            unfocusedContainerColor = Surface,
                            cursorColor = Black
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() })
                    )
                    if (linkError.isNotBlank()) {
                        Text(linkError, fontSize = 12.sp, color = Color(0xFFD32F2F))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val vid = ListeningRoomViewModel.extractVideoId(linkInput.trim())
                        if (vid.isBlank()) { linkError = "Couldn't read video ID — paste the full YouTube link."; return@Button }
                        val title = titleInput.trim().ifBlank { "Untitled Track" }
                        vm.createRoom(vid, title) {}
                        showCreate = false; linkInput = ""; titleInput = ""
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Black),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) { Text("Start Room", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false; linkError = "" }) {
                    Text("Cancel", color = Gray)
                }
            }
        )
    }
}

// ── Room card in lobby ────────────────────────────────────────────────────────
@Composable
private fun RoomCard(room: ListeningRoom, myUid: String, onJoin: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "dot")
    val dotA by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), "da")
    val alreadyIn = room.hostUid == myUid || room.participantUid == myUid
    val canJoin   = !room.isFull && !alreadyIn

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Music icon
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Black),
            contentAlignment = Alignment.Center
        ) { Text("♫", fontSize = 20.sp) }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                room.trackTitle.ifBlank { "Untitled Track" },
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Black,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "By ${room.hostAlias}",
                fontSize = 12.sp, color = Gray
            )
            // Presence
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(
                    if (room.status == "listening") Green.copy(dotA) else Hint
                ))
                Text(
                    when {
                        room.isFull -> "2 listening"
                        room.status == "listening" -> "1 listening · open"
                        else -> "Waiting · open"
                    },
                    fontSize = 11.sp, color = Gray
                )
            }
        }

        // Action button
        when {
            alreadyIn -> {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Black).padding(horizontal = 12.dp, vertical = 7.dp)
                ) { Text("Enter", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
            canJoin -> {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Black).clickable { onJoin() }.padding(horizontal = 12.dp, vertical = 7.dp)
                ) { Text("Join", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
            else -> {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Border).padding(horizontal = 12.dp, vertical = 7.dp)
                ) { Text("Full", color = Gray, fontSize = 12.sp) }
            }
        }
    }
}

// ── Empty lobby ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyLobby(onCreate: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "e")
    val sc by inf.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), "es")
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("♫", fontSize = 52.sp)
        Spacer(Modifier.height(16.dp))
        Text("No rooms yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Black)
        Spacer(Modifier.height(6.dp))
        Text("Be the first to start a listening room.\nAnyone can join and listen with you.", fontSize = 14.sp, color = Gray, textAlign = TextAlign.Center, lineHeight = 21.sp)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onCreate,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Black),
            elevation = ButtonDefaults.buttonElevation(0.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create a Room", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

// ── Active room screen ────────────────────────────────────────────────────────
@Composable
fun ListeningRoomScreen(
    roomId : String,
    amHost : Boolean,
    onBack : () -> Unit = {}
) {
    val vm: ListeningRoomViewModel = viewModel()
    val roomState   by vm.roomState.collectAsStateWithLifecycle()
    var webView     by remember { mutableStateOf<WebView?>(null) }
    var playerError by remember { mutableIntStateOf(-1) }   // -1 = no error
    val context     = androidx.compose.ui.platform.LocalContext.current

    // If landed here fresh (e.g. from deep link), re-attach listener
    LaunchedEffect(roomId) {
        if (roomState is RoomUiState.Idle) {
            vm.joinRoom(roomId) {}
        }
    }

    DisposableEffect(Unit) { onDispose { } }

    when (val s = roomState) {
        is RoomUiState.Loading, RoomUiState.Idle -> {
            Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Black, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
        }
        is RoomUiState.Error -> {
            Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⚠️", fontSize = 32.sp)
                    Text(s.msg, fontSize = 14.sp, color = Black, textAlign = TextAlign.Center)
                    Button(onClick = onBack, shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Black),
                        elevation = ButtonDefaults.buttonElevation(0.dp)) {
                        Text("Go back", color = Color.White)
                    }
                }
            }
        }
        is RoomUiState.Active -> {
            val room = s.room
            // Sync WebView when playback state changes (participant)
            LaunchedEffect(room.isPlaying, room.positionSec, room.updatedAt) {
                val wv = webView ?: return@LaunchedEffect
                if (!s.amHost && room.trackId.isNotBlank()) {
                    val elapsed = (System.currentTimeMillis() - room.updatedAt) / 1000
                    val target  = room.positionSec + elapsed
                    wv.evaluateJavascript(
                        if (room.isPlaying) "player.seekTo($target,true);player.playVideo();"
                        else "player.seekTo(${room.positionSec},true);player.pauseVideo();", null
                    )
                }
            }

            Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
                // Header
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { vm.leaveRoom(); onBack() }) {
                        Icon(Icons.Rounded.ArrowBack, null, tint = Black, modifier = Modifier.size(22.dp))
                    }
                    PresencePill(room)
                    Spacer(Modifier.size(48.dp))
                }

                HorizontalDivider(color = Border)

                // Player
                if (room.trackId.isNotBlank()) {
                    if (playerError == 150 || playerError == 100 || playerError == 101) {
                        // Video can't be embedded — show fallback
                        VideoErrorBox(
                            trackId    = room.trackId,
                            amHost     = s.amHost,
                            context    = context,
                            onChangeSong = { playerError = -1; vm.changeSong("", "") }
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Black)) {
                            YouTubePlayer(
                                videoId   = room.trackId,
                                autoPlay  = room.isPlaying,
                                amHost    = s.amHost,
                                onReady   = { webView = it },
                                onPlaying = { sec -> if (s.amHost) vm.syncPlayback(true, sec) },
                                onPaused  = { sec -> if (s.amHost) vm.syncPlayback(false, sec) },
                                onError   = { code -> playerError = code }
                            )
                        }
                    }
                } else {
                    WaitBox(amHost = s.amHost)
                }

                // Track info
                if (room.trackTitle.isNotBlank()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("♫", fontSize = 20.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(room.trackTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (room.isFull) "🎧 ${room.hostAlias} & ${room.participantAlias}"
                            else "Waiting for someone to join…",
                            fontSize = 12.sp, color = Gray
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Footer
                HorizontalDivider(color = Border)
                Text(
                    "Two introverts. One song. Quietly together.",
                    fontSize = 12.sp, color = Hint, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            }
        }
    }
}

// ── Presence pill ─────────────────────────────────────────────────────────────
@Composable
private fun PresencePill(room: ListeningRoom) {
    val inf = rememberInfiniteTransition(label = "p")
    val a by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), "pa")
    val (label, color) = when {
        room.isFull && room.isPlaying -> "Both listening" to Green
        room.isFull -> "Paused" to Hint
        else -> "Waiting…" to Hint
    }
    Row(
        Modifier.clip(RoundedCornerShape(100.dp)).background(Surface).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color.copy(if (room.isPlaying) a else 0.5f)))
        Text(label, fontSize = 11.sp, color = Gray, fontWeight = FontWeight.Medium)
    }
}

// ── Wait box ──────────────────────────────────────────────────────────────────
@Composable
private fun WaitBox(amHost: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("♫", fontSize = 48.sp)
        Text(
            if (amHost) "You're live — waiting for someone to join" else "Waiting for the host to pick a song…",
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Black, textAlign = TextAlign.Center
        )
        Text(
            if (amHost) "Share the app with a friend and they can join your room." else "Sit back and relax.",
            fontSize = 13.sp, color = Gray, textAlign = TextAlign.Center, lineHeight = 20.sp
        )
    }
}

// ── Video error fallback ──────────────────────────────────────────────────────
@Composable
private fun VideoErrorBox(
    trackId     : String,
    amHost      : Boolean,
    context     : android.content.Context,
    onChangeSong: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🚫", fontSize = 32.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "This video can't be played here",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Black, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "The uploader has disabled embedding.\nOpen it directly in YouTube instead.",
            fontSize = 12.sp, color = Gray, textAlign = TextAlign.Center, lineHeight = 18.sp
        )
        Spacer(Modifier.height(16.dp))
        // Open in YouTube app
        Button(
            onClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.youtube.com/watch?v=$trackId")
                )
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Black),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Icon(Icons.Rounded.OpenInNew, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open in YouTube", color = Color.White, fontSize = 13.sp)
        }
        // Host: try a different video
        if (amHost) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onChangeSong) {
                Text("Try a different video", color = Gray, fontSize = 12.sp)
            }
        }
    }
}

// ── YouTube WebView player ────────────────────────────────────────────────────
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(
    videoId  : String,
    autoPlay : Boolean,
    amHost   : Boolean,
    onReady  : (WebView) -> Unit,
    onPlaying: (Long) -> Unit,
    onPaused : (Long) -> Unit,
    onError  : (Int) -> Unit = {}
) {
    // YouTube blocks the default "Android WebView" UA with error 150/152.
    // Spoofing a standard Chrome UA resolves this.
    val chromeUA = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 " +
                   "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    val html = """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>*{margin:0;padding:0;background:#000}html,body,#p{width:100%;height:100%}</style>
</head><body><div id="p"></div><script>
var t=document.createElement('script');
t.src="https://www.youtube.com/iframe_api";
document.head.appendChild(t);
var player;
function onYouTubeIframeAPIReady(){
  player=new YT.Player('p',{
    videoId:'$videoId',
    playerVars:{
      autoplay:${if(autoPlay) 1 else 0},
      controls:${if(amHost) 1 else 0},
      rel:0,
      modestbranding:1,
      fs:0,
      enablejsapi:1,
      origin:'https://www.youtube.com',
      playsinline:1
    },
    events:{
      onReady:function(e){ Android.onReady(); },
      onStateChange:function(e){
        var s=Math.floor(player.getCurrentTime());
        if(e.data===1) Android.onPlaying(s);
        if(e.data===2) Android.onPaused(s);
      },
      onError:function(e){ Android.onError(e.data); }
    }
  });
}
</script></body></html>""".trimIndent()

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled                 = true
                    domStorageEnabled                 = true
                    mediaPlaybackRequiresUserGesture  = false
                    userAgentString                   = chromeUA   // ← key fix
                    mixedContentMode                  = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    allowContentAccess                = true
                    allowFileAccess                   = true
                }
                webChromeClient = WebChromeClient()
                webViewClient   = WebViewClient()
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface fun onReady()            = onReady(this@apply)
                    @android.webkit.JavascriptInterface fun onPlaying(sec: Long) = onPlaying(sec)
                    @android.webkit.JavascriptInterface fun onPaused(sec: Long)  = onPaused(sec)
                    @android.webkit.JavascriptInterface fun onError(code: Int) {
                        android.util.Log.e("YouTubePlayer", "Player error code: $code")
                        onError(code)
                    }
                }, "Android")
                loadDataWithBaseURL(
                    "https://www.youtube.com",   // baseUrl — required for origin to work
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        }
    )
}
