package com.ns.findyourintroverfriend.home





import android.net.Uri


import androidx.activity.compose.rememberLauncherForActivityResult


import androidx.activity.result.contract.ActivityResultContracts


import androidx.compose.animation.*


import androidx.compose.animation.core.*


import androidx.compose.foundation.*


import androidx.compose.foundation.layout.*


import androidx.compose.foundation.shape.CircleShape


import androidx.compose.foundation.shape.RoundedCornerShape


import androidx.compose.material.icons.Icons


import androidx.compose.material.icons.rounded.CameraAlt


import androidx.compose.material.icons.rounded.CardGiftcard


import androidx.compose.material.icons.rounded.Edit


import androidx.compose.material.icons.rounded.FavoriteBorder


import androidx.compose.material.icons.rounded.Person


import androidx.compose.material.icons.rounded.Photo


import androidx.compose.material3.*


import androidx.compose.runtime.*


import androidx.compose.runtime.saveable.rememberSaveable


import androidx.compose.ui.Alignment


import androidx.compose.ui.Modifier


import androidx.compose.ui.draw.*


import androidx.compose.ui.geometry.Offset


import androidx.compose.ui.graphics.*


import androidx.compose.ui.graphics.painter.Painter


import androidx.compose.ui.hapticfeedback.HapticFeedbackType


import androidx.compose.ui.layout.ContentScale


import androidx.compose.ui.platform.LocalContext


import androidx.compose.ui.platform.LocalDensity


import androidx.compose.ui.platform.LocalHapticFeedback


import androidx.compose.ui.res.painterResource


import androidx.compose.ui.text.font.FontWeight


import androidx.compose.ui.text.style.TextAlign


import androidx.compose.ui.unit.*


import androidx.compose.foundation.gestures.Orientation


import androidx.compose.foundation.gestures.draggable


import androidx.compose.foundation.gestures.rememberDraggableState


import androidx.lifecycle.ViewModel


import androidx.lifecycle.compose.collectAsStateWithLifecycle


import androidx.lifecycle.viewModelScope


import androidx.lifecycle.viewmodel.compose.viewModel


import com.google.firebase.auth.FirebaseAuth


import com.google.firebase.firestore.FirebaseFirestore


import com.ns.findyourintroverfriend.R


import com.airbnb.lottie.compose.*


import kotlinx.coroutines.flow.MutableStateFlow


import kotlinx.coroutines.flow.StateFlow


import kotlinx.coroutines.flow.asStateFlow


import kotlinx.coroutines.launch


import kotlinx.coroutines.tasks.await



import kotlinx.coroutines.tasks.await


import java.util.Calendar


import kotlin.math.roundToInt
import com.ns.findyourintroverfriend.util.VeilThemeManager
import com.ns.findyourintroverfriend.util.LocalVeilIsDark





// Data model 


data class VeilHomeUiState(


 val alias : String = "",


 val emoji : String = "\uD83C\uDF19",


 val photoUrl : String = "",


 val roomsCompleted : Int = 0,


 val daysPlayed : Int = 0,


 val activeRooms : Int = 0,


 val queueSize : Int = 0,


 val isLoading : Boolean = true,


 val isInBackgroundQueue : Boolean = false,


 val bgQueueCount : Int = 0,


 )





// ViewModel 


class VeilHomeViewModel : ViewModel() {


 private val _state = MutableStateFlow(VeilHomeUiState())


 val state: StateFlow<VeilHomeUiState> = _state.asStateFlow()


 val auth = FirebaseAuth.getInstance()


 val db = FirebaseFirestore.getInstance()


 init { loadProfile() }





 private fun loadProfile() {


 val uid = auth.currentUser?.uid ?: return


 viewModelScope.launch {


 try {


 val doc = db.collection("users").document(uid).get().await()


 // Use Google profile photo from FirebaseAuth as fallback if Firestore is empty


 val authPhotoUrl = auth.currentUser?.photoUrl?.toString() ?: ""


 _state.value = VeilHomeUiState(


 alias = doc.getString("alias") ?: auth.currentUser?.displayName ?: "Wanderer",


 emoji = doc.getString("emoji") ?: "\uD83C\uDF19",


 photoUrl = doc.getString("photoUrl")?.takeIf { it.isNotBlank() } ?: authPhotoUrl,


 roomsCompleted = 0,


 daysPlayed = 0,


 isLoading = false,


 )


 } catch (e: Exception) {


 // Even on Firestore failure, show the Google photo


 val authPhotoUrl = auth.currentUser?.photoUrl?.toString() ?: ""


 _state.value = _state.value.copy(


 isLoading = false,


 photoUrl = authPhotoUrl,


 alias = auth.currentUser?.displayName ?: "Wanderer",


 )


 }


 }


 }





 fun cancelBackgroundQueue(context: android.content.Context) {


 val uid = auth.currentUser?.uid ?: return


 _state.value = _state.value.copy(isInBackgroundQueue = false)


 com.ns.findyourintroverfriend.waiting.QueueHeartbeatWorker.cancel(context)


 viewModelScope.launch {


 try {


 db.collection("matchmaking_queue").document(uid).delete().await()


 db.collection("users").document(uid).update("leftRoom", true).await()


 } catch (_: Exception) {}


 }


 }





 fun clearLeftRoomFlag() {


 val uid = auth.currentUser?.uid ?: return


 viewModelScope.launch { runCatching { db.collection("users").document(uid).update("leftRoom", false).await() } }


 }





 fun updateProfilePhoto(


 context : android.content.Context,


 uri : Uri,


 onResult : (success: Boolean, error: String?) -> Unit


 ) {


 val uid = auth.currentUser?.uid ?: return onResult(false, "Not signed in")


 viewModelScope.launch {


 try {


 val cloudName = com.ns.findyourintroverfriend.BuildConfig.CLOUDINARY_CLOUD_NAME


 val uploadPreset = com.ns.findyourintroverfriend.BuildConfig.CLOUDINARY_UPLOAD_PRESET


 if (cloudName.isBlank() || uploadPreset.isBlank()) {


 onResult(false, "Cloudinary not configured - check local.properties"); return@launch


 }


 val url = com.ns.findyourintroverfriend.util.CloudinaryUploader.upload(


 context = context, uri = uri, cloudName = cloudName, uploadPreset = uploadPreset


 )


 db.collection("users").document(uid).update("photoUrl", url).await()


 _state.value = _state.value.copy(photoUrl = url)


 onResult(true, null)


 } catch (e: Exception) {


 android.util.Log.e("VeilHomeVM", "Photo update failed: ${e.message}", e)


 onResult(false, e.message?.take(120))


 }


 }


 }


}





// Colors 


private val HBg = Color(0xFFF7F5F0)


private var _homeDark by mutableStateOf(VeilThemeManager.initialResolvedDark)

private val HBg2        get() = if (_homeDark) Color(0xFF0D0D12) else Color(0xFFF2EDE6)
private val HCard       get() = if (_homeDark) Color(0xFF1D1D28) else Color(0xFFFFFFFF)
private val HCardBorder get() = if (_homeDark) Color(0xFF272730) else Color(0xFFEDE8DF)
private val HAccent            = Color(0xFF7C6AE8)
private val HAccentSoft get() = if (_homeDark) Color(0xFF1A1530) else Color(0xFFEFEBFF)
private val HGold              = Color(0xFFE89820)
private val HPrimary    get() = if (_homeDark) Color(0xFFDFDFEA) else Color(0xFF1C1916)
private val HSecondary  get() = if (_homeDark) Color(0xFFAAAAB8) else Color(0xFF8A8480)
private val HMuted      get() = if (_homeDark) Color(0xFF787888) else Color(0xFFBBB5AC)





private fun timeGreeting() = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {


 in 5..11 -> "Good morning"


 in 12..16 -> "Good afternoon"


 in 17..20 -> "Good evening"


 else -> "Good night"


}





// BackgroundQueueBanner 


@Composable


private fun BackgroundQueueBanner(queueCount: Int, onCancel: () -> Unit) {


 val inf = rememberInfiniteTransition(label = "bqb")


 val pulse by inf.animateFloat(0.7f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), "bqp")


 Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)


 .clip(RoundedCornerShape(16.dp))


 .background(Brush.horizontalGradient(listOf(Color(0xFF3A2400), Color(0xFF2E1A00))))


 .border(1.dp, Brush.horizontalGradient(listOf(Color(0xFFFFC567).copy(0.6f), Color(0xFFFFD080).copy(0.3f))), RoundedCornerShape(16.dp))


 .padding(horizontal = 16.dp, vertical = 12.dp)) {


 Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {


 Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {


 Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFC567).copy(alpha = pulse)))


 Column {


 Text("Finding your circle...", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFFFC567), fontWeight = FontWeight.Bold, fontSize = 13.sp))


 Text("${if (queueCount > 0) "$queueCount people in queue · " else ""}You'll be notified", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFBB8833), fontSize = 11.sp))


 }


 }


 Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A0E00)).border(0.5.dp, Color(0xFF555533), RoundedCornerShape(8.dp)).clickable { onCancel() }.padding(horizontal = 10.dp, vertical = 6.dp)) {


 Text("Cancel", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF887755), fontSize = 11.sp))


 }


 }


 }


}





// VeilHomeScreen 


@Composable


fun VeilHomeScreen(


 vm : VeilHomeViewModel = viewModel(),


 onFindRoom : () -> Unit,


 onOpenSwipe : () -> Unit = {},


 onOpenMatch : (String) -> Unit = {},


 onNavigateToMatches: () -> Unit = {},


 onViewProfile : () -> Unit = {},


 onViewRooms : () -> Unit = {},


 onOpenFounders : () -> Unit = {},


 onOpenDevPanel : () -> Unit = {},


 onOpenProfileThemes: () -> Unit = {},


 onLogout : () -> Unit = {},


 onOpenDms : () -> Unit = {},


 onOpenCookieStore : () -> Unit = {},


 onOpenUserProfile : (uid: String) -> Unit = {},


 onOpenListeningRoom: () -> Unit = {},


 onOpenVoiceRooms    : () -> Unit = {},


 onOpenGroups        : () -> Unit = {},


 onOpenInvite        : () -> Unit = {},


 onNavigateDrawGuess  : () -> Unit = {},


 onOpenVeilFriends   : () -> Unit = {},


 ) {


 val state by vm.state.collectAsStateWithLifecycle()


 val scroll = androidx.compose.foundation.rememberScrollState()


 val haptic = LocalHapticFeedback.current


 val context = LocalContext.current


 val scope = androidx.compose.runtime.rememberCoroutineScope()


 val swipeVm : com.ns.findyourintroverfriend.swipe.SwipeViewModel = viewModel()


 val authUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }


 val db = remember { FirebaseFirestore.getInstance() }





 var showLogout by remember { mutableStateOf(false) }


 var selectedTab by rememberSaveable { mutableIntStateOf(0) }


 var previousTab by rememberSaveable { mutableIntStateOf(0) }


 var isUploadingPhoto by remember { mutableStateOf(false) }


 var photoUploadError by remember { mutableStateOf<String?>(null) }





 // cookie 


 var showWelcomeGiftDialog by remember { mutableStateOf(false) }


 var welcomeGiftClaiming by remember { mutableStateOf(false) }


 var welcomeGiftClaimed by remember { mutableStateOf(false) }





 LaunchedEffect(authUid) {


 if (authUid.isBlank()) return@LaunchedEffect


 try {


 val doc = db.collection("users").document(authUid).get().await()


 val already = doc.getBoolean("welcomeGiftClaimed") ?: false


 if (!already) showWelcomeGiftDialog = true


 } catch (_: Exception) {}


 }





 // UCrop 


 val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->


 val outUri = com.yalantis.ucrop.UCrop.getOutput(result.data ?: return@rememberLauncherForActivityResult)


 if (result.resultCode == android.app.Activity.RESULT_OK && outUri != null) {


 isUploadingPhoto = true; photoUploadError = null


 vm.updateProfilePhoto(context, outUri) { ok, err ->


 isUploadingPhoto = false


 if (ok) { showLogout = false } else { photoUploadError = err }


 }


 }


 }


 // UCrop 


 val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { src ->


 if (src == null) return@rememberLauncherForActivityResult


 val dest = Uri.fromFile(java.io.File(context.cacheDir, "veil_profile_${System.currentTimeMillis()}.jpg"))


 cropLauncher.launch(


 com.yalantis.ucrop.UCrop.of(src, dest)


 .withAspectRatio(1f, 1f).withMaxResultSize(600, 600)


 .withOptions(com.yalantis.ucrop.UCrop.Options().apply {


 setCircleDimmedLayer(true); setShowCropGrid(false)


 setToolbarTitle("Move & scale")


 setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)


 setCompressionQuality(90)


 }).getIntent(context)


 )


 }





 val isDev = remember {


 FirebaseAuth.getInstance().currentUser?.email?.lowercase()


 ?.let { it in com.ns.findyourintroverfriend.dev.DevConfig.DEV_EMAILS } == true


 }





 val deleteAccountUrl = "https://docs.google.com/forms/d/e/1FAIpQLScLs32PUEm-BFcwq_M8q-wy5a1M0d1ZIpOZoTW9a7BYNvlg6Q/viewform"





 val isDark = LocalVeilIsDark.current
 SideEffect { _homeDark = isDark }


 Scaffold(


 containerColor = if (isDark) Color(0xFF111118) else HBg,


 bottomBar = {


 NavigationBar(containerColor = if (isDark) Color(0xFF111118) else HCard, tonalElevation = 0.dp,


 modifier = Modifier.shadow(elevation = 8.dp, spotColor = Color.Black.copy(0.06f)).border(0.5.dp, if (isDark) Color(0xFF272730) else HCardBorder)) {


 // Tab 0 — Feed


 NavigationBarItem(selected = selectedTab == 0, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); previousTab = selectedTab; selectedTab = 0 },


 icon = { Image(
                        painterResource(R.drawable.ic_nav_home), null,
                        Modifier.size(26.dp),
                        colorFilter = ColorFilter.tint(
                            if (selectedTab == 0) HAccent
                            else if (isDark) Color(0xFFDFDFEA).copy(alpha = 0.55f)
                            else HPrimary.copy(alpha = 0.45f),
                            BlendMode.SrcIn
                        )
                    ) },


 label = null, colors = NavigationBarItemDefaults.colors(indicatorColor = if (isDark) Color(0xFF1A1530) else HAccentSoft, unselectedIconColor = if (isDark) Color(0xFFDFDFEA).copy(0.45f) else HPrimary.copy(0.40f)))


 // Tab 1 — Explore / Games


 NavigationBarItem(selected = selectedTab == 1, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); previousTab = selectedTab; selectedTab = 1 },


 icon = { Image(
                        painterResource(R.drawable.ic_nav_gamepad), null,
                        Modifier.size(26.dp),
                        colorFilter = ColorFilter.tint(
                            if (selectedTab == 1) HAccent
                            else if (isDark) Color(0xFFDFDFEA).copy(alpha = 0.55f)
                            else HPrimary.copy(alpha = 0.45f),
                            BlendMode.SrcIn
                        )
                    ) },


 label = null, colors = NavigationBarItemDefaults.colors(indicatorColor = if (isDark) Color(0xFF1A1530) else HAccentSoft, unselectedIconColor = if (isDark) Color(0xFFDFDFEA).copy(0.45f) else HPrimary.copy(0.40f)))


 // Tab 2 — Swipe (heart icon)


 NavigationBarItem(selected = selectedTab == 2, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); previousTab = selectedTab; selectedTab = 2 },


 icon = {


 Icon(


 imageVector = Icons.Rounded.FavoriteBorder,


 contentDescription = "Swipe",


 tint = if (selectedTab == 2) HAccent else if (isDark) Color(0xFFDFDFEA).copy(0.45f) else HPrimary.copy(0.40f),


 modifier = Modifier.size(26.dp)


 )


 },


 label = null, colors = NavigationBarItemDefaults.colors(indicatorColor = if (isDark) Color(0xFF1A1530) else HAccentSoft, unselectedIconColor = if (isDark) Color(0xFFDFDFEA).copy(0.45f) else HPrimary.copy(0.40f)))


 // Tab 3 — Profile


 NavigationBarItem(selected = selectedTab == 3, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); previousTab = selectedTab; selectedTab = 3 },


 icon = {


 Icon(


 imageVector = Icons.Rounded.Person,


 contentDescription = "Profile",


 tint = if (selectedTab == 3) HAccent else if (isDark) Color(0xFFDFDFEA).copy(0.45f) else HPrimary.copy(0.40f),


 modifier = Modifier.size(26.dp)


 )


 },


 label = null, colors = NavigationBarItemDefaults.colors(indicatorColor = if (isDark) Color(0xFF1A1530) else HAccentSoft, unselectedIconColor = if (isDark) Color(0xFFDFDFEA).copy(0.45f) else HPrimary.copy(0.40f)))


 }


 }


 ) { scaffoldPadding ->


 // Determine slide direction based on tab navigation


 val slideDirection = if (selectedTab > previousTab) {


 // Moving right (left to right)


 AnimatedContentTransitionScope.SlideDirection.Start


 } else {


 // Moving left (right to left)


 AnimatedContentTransitionScope.SlideDirection.End


 }





 AnimatedContent(


 targetState = selectedTab,


 transitionSpec = {


 (slideIntoContainer(


 towards = slideDirection,


 animationSpec = tween(100, easing = FastOutSlowInEasing)


 ) togetherWith slideOutOfContainer(


 towards = slideDirection,


 animationSpec = tween(100, easing = FastOutSlowInEasing)


 ))


 },


 label = "tabContent"


 ) { targetTab ->


 when (targetTab) {


 0 -> Box(Modifier.fillMaxSize()) {

     com.ns.findyourintroverfriend.feed.FeedScreen(

         modifier = Modifier.fillMaxSize(),

         bottomNavPadding = scaffoldPadding.calculateBottomPadding(),

         onOpenDms = onOpenDms,

         onOpenCookieStore = onOpenCookieStore,

         onOpenUserProfile = onOpenUserProfile

     )

 }


 1 -> com.ns.findyourintroverfriend.game.GameLobbyScreen(
     onNavigateDrawGuess = onNavigateDrawGuess,
     onOpenVoiceRooms    = onOpenVoiceRooms,
     onOpenGroups        = onOpenGroups,
 )


 2 -> com.ns.findyourintroverfriend.swipe.SwipeContent(


 vm = swipeVm,


 modifier = Modifier.fillMaxSize().padding(scaffoldPadding),


 onOpenMatch = onOpenMatch,


 onNavigateToMatches = onNavigateToMatches


 )


 3 -> ProfileTab(


 state = state,


 vm = vm,


 isDev = isDev,


 isUploadingPhoto = isUploadingPhoto,


 photoUploadError = photoUploadError,


 deleteAccountUrl = deleteAccountUrl,


 context = context,


 scaffoldPadding = scaffoldPadding,


 onGalleryLaunch = { galleryLauncher.launch("image/*") },


 onOpenProfileThemes = onOpenProfileThemes,


 onOpenDevPanel = onOpenDevPanel,


 onOpenCookieStore = onOpenCookieStore,


 onOpenInvite = onOpenInvite,


 onOpenVeilFriends = onOpenVeilFriends,


 onLogout = onLogout


 )


 }


 }





 // dialog 


 if (showLogout) {


 androidx.compose.ui.window.Dialog(onDismissRequest = { showLogout = false }) {


 Box(


 Modifier


 .fillMaxWidth()


 .clip(RoundedCornerShape(24.dp))


 .background(HCard)


 .border(1.dp, HCardBorder, RoundedCornerShape(24.dp))


 ) {


 Column(horizontalAlignment = Alignment.CenterHorizontally) {


 Spacer(Modifier.height(32.dp))





 // Profile picture


 Box(


 Modifier


 .size(80.dp)


 .clip(CircleShape)


 .background(HAccentSoft)


 .border(2.5.dp, HAccent.copy(0.25f), CircleShape)


 ) {


 val p = state.photoUrl


 if (p.isNotBlank()) {


 VHMemberPhoto(p, ContentScale.Crop, Modifier.fillMaxSize().clip(CircleShape))


 } else {


 Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {


 Text(state.emoji.ifBlank { "" }, fontSize = 32.sp)


 }


 }


 }





 Spacer(Modifier.height(16.dp))





 // Name and email


 Text(


 state.alias,


 fontSize = 18.sp,


 fontWeight = FontWeight.Bold,


 color = HPrimary


 )


 Spacer(Modifier.height(4.dp))


 Text(


 "signed in with google",


 fontSize = 12.sp,


 color = HSecondary


 )





 Spacer(Modifier.height(24.dp))





 // Divider


 HorizontalDivider(color = HCardBorder, thickness = 1.dp)





 // Menu items


 if (isDev) {


 ModernMenuRow(icon = "⚙", label = "Dev Panel", sublabel = null, tint = Color(0xFF6BBFCC)) {


 showLogout = false; onOpenDevPanel()


 }


 HorizontalDivider(color = HCardBorder, thickness = 1.dp)


 }





 ModernMenuRow(icon = "👤", label = "View Profile", sublabel = "Manage account & settings", tint = HAccent) {


 showLogout = false


 }





 HorizontalDivider(color = HCardBorder, thickness = 1.dp)





 ModernMenuRow(icon = "🚪", label = "Sign Out", sublabel = null, tint = Color(0xFFE85D5D)) {


 showLogout = false; onLogout()


 }





 HorizontalDivider(color = HCardBorder, thickness = 1.dp)





 Spacer(Modifier.height(12.dp))





 // Footer links


 Row(


 Modifier.fillMaxWidth(),


 horizontalArrangement = Arrangement.Center


 ) {


 Text(


 "privacy policy",


 fontSize = 11.sp,


 color = HMuted,


 style = androidx.compose.ui.text.TextStyle(


 textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline


 ),


 modifier = Modifier.clickable {


 android.content.Intent(


 android.content.Intent.ACTION_VIEW,


 android.net.Uri.parse("https://veil-app.in/privacy")


 ).also { context.startActivity(it) }


 }


 )


 Text(" ", fontSize = 11.sp, color = HMuted)


 Text(


 "request account deletion",


 fontSize = 11.sp,


 color = HMuted,


 style = androidx.compose.ui.text.TextStyle(


 textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline


 ),


 modifier = Modifier.clickable {


 android.content.Intent(


 android.content.Intent.ACTION_VIEW,


 android.net.Uri.parse(deleteAccountUrl)


 ).also { context.startActivity(it) }


 }


 )


 }





 Spacer(Modifier.height(16.dp))


 }


 }


 }


 }


 }





 // \u2500\u2500 Welcome Gift Dialog (30 cookies, shows once per account) \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500


 if (showWelcomeGiftDialog && !welcomeGiftClaimed) {


 androidx.compose.ui.window.Dialog(onDismissRequest = {}) { // non-dismissable \u2014 must claim


 Box(


 Modifier


 .fillMaxWidth()


 .clip(RoundedCornerShape(24.dp))


 .background(HCard)


 .border(1.dp, HCardBorder, RoundedCornerShape(24.dp))


 ) {





 Column(


 Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 32.dp),


 horizontalAlignment = Alignment.CenterHorizontally,


 verticalArrangement = Arrangement.spacedBy(0.dp)


 ) {


 // Pulsing cookie emoji


 val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "wg")


 val pulse by inf.animateFloat(


 1f, 1.08f,


 androidx.compose.animation.core.infiniteRepeatable(


 androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing),


 androidx.compose.animation.core.RepeatMode.Reverse


 ), "wgp"


 )


 Text("", fontSize = 52.sp, modifier = Modifier.scale(pulse))





 Spacer(Modifier.height(24.dp))





 Text(


 "A little welcome gift.",


 fontSize = 22.sp,


 fontWeight = FontWeight.Bold,


 color = HPrimary,


 textAlign = TextAlign.Center,


 letterSpacing = (-0.5).sp


 )





 Spacer(Modifier.height(8.dp))





 Text(


 "30 cookies, just for showing up.",


 fontSize = 14.sp,


 color = HSecondary,


 textAlign = TextAlign.Center,


 lineHeight = 20.sp


 )





 Spacer(Modifier.height(28.dp))





 HorizontalDivider(thickness = 1.dp, color = HCardBorder)





 Spacer(Modifier.height(24.dp))





 // Cookie count pill


 Box(


 Modifier


 .clip(RoundedCornerShape(12.dp))


 .background(HBg)


 .border(1.dp, HCardBorder, RoundedCornerShape(12.dp))


 .padding(horizontal = 20.dp, vertical = 14.dp),


 contentAlignment = Alignment.Center


 ) {


 Row(


 verticalAlignment = Alignment.CenterVertically,


 horizontalArrangement = Arrangement.spacedBy(10.dp)


 ) {


 Text("", fontSize = 18.sp)


 Text(


 "30 cookies",


 fontSize = 17.sp,


 fontWeight = FontWeight.SemiBold,


 color = HPrimary


 )


 }


 }





 Spacer(Modifier.height(24.dp))





 // Claim button


 Button(


 onClick = {


 if (welcomeGiftClaiming) return@Button


 welcomeGiftClaiming = true


 scope.launch {


 try {


 db.collection("users").document(authUid)


 .update(mapOf(


 "cookieBalance" to com.google.firebase.firestore.FieldValue.increment(30L),


 "cookiesEarned" to com.google.firebase.firestore.FieldValue.increment(30L),


 "welcomeGiftClaimed" to true


 )).await()


 welcomeGiftClaimed = true


 kotlinx.coroutines.delay(300L)


 showWelcomeGiftDialog = false


 } catch (_: Exception) {


 welcomeGiftClaiming = false


 }


 }


 },


 enabled = !welcomeGiftClaiming,


 modifier = Modifier.fillMaxWidth().height(52.dp),


 shape = RoundedCornerShape(14.dp),


 colors = ButtonDefaults.buttonColors(


 containerColor = HPrimary,


 contentColor = Color.White,


 disabledContainerColor = HPrimary.copy(0.15f),


 disabledContentColor = Color.White.copy(0.5f)


 ),


 elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)


 ) {


 if (welcomeGiftClaiming) {


 CircularProgressIndicator(


 Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp


 )


 } else {


 Text(


 "Claim your cookies",


 fontWeight = FontWeight.Bold,


 fontSize = 15.sp,


 letterSpacing = 0.sp


 )


 }


 }





 Text(


 "For all users \u2014 new and returning",


 fontSize = 11.sp,


 color = HMuted,


 textAlign = TextAlign.Center,


 letterSpacing = 0.3.sp


 )


 }


 }


 }


 }





} // Close Scaffold











// 


private val PBg         get() = if (_homeDark) Color(0xFF111118) else Color(0xFFFFFFFF)
private val PCard       get() = if (_homeDark) Color(0xFF1D1D28) else Color(0xFFFFFFFF)
private val PBorder     get() = if (_homeDark) Color(0xFFFFFFFF).copy(alpha = 0.10f) else Color(0xFF000000).copy(alpha = 0.10f)
private val PBorderHard get() = if (_homeDark) Color(0xFFFFFFFF).copy(alpha = 0.18f) else Color(0xFF000000).copy(alpha = 0.18f)
private val PPrimary    get() = if (_homeDark) Color(0xFFDFDFEA) else Color(0xFF0A0A0A)
private val PFill       get() = if (_homeDark) Color(0xFF2C2C3C) else Color(0xFF0A0A0A)
private val PSub        get() = if (_homeDark) Color(0xFFAAAAB8) else Color(0xFF555555)
private val PMuted      get() = if (_homeDark) Color(0xFF787888) else Color(0xFF999999)
private val PGhost      get() = if (_homeDark) Color(0xFF1E1E2C) else Color(0xFFF2F2F2)





// ProfileTab 


@Composable


private fun ProfileTab(


 state : VeilHomeUiState,


 vm : VeilHomeViewModel,


 isDev : Boolean,


 isUploadingPhoto : Boolean,


 photoUploadError : String?,


 deleteAccountUrl : String,


 context : android.content.Context,


 scaffoldPadding : PaddingValues,


 onGalleryLaunch : () -> Unit,


 onOpenProfileThemes : () -> Unit,


 onOpenDevPanel : () -> Unit,


 onOpenCookieStore : () -> Unit,


 onOpenInvite : () -> Unit,


 onOpenVeilFriends : () -> Unit,


 onLogout : () -> Unit,


 ) {


 val auth = FirebaseAuth.getInstance()


 val db = FirebaseFirestore.getInstance()


 val uid = auth.currentUser?.uid ?: ""


 val email = auth.currentUser?.email ?: ""


 val haptic = LocalHapticFeedback.current





 // cookie 


 var cookieBalance by remember { mutableIntStateOf(0) }


 LaunchedEffect(uid) {


 if (uid.isBlank()) return@LaunchedEffect


 try {


 val doc = db.collection("users").document(uid).get().await()


 cookieBalance = (doc.getLong("cookieBalance") ?: 0L).toInt()


 } catch (_: Exception) {}


 }





 // 


 val shimTrans = rememberInfiniteTransition(label = "ps")


 val shimX by shimTrans.animateFloat(


 -800f, 800f, label = "psx",


 animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart)


 )


 val shimBrush = Brush.linearGradient(


 colors = if (_homeDark) listOf(Color(0xFF1A1A26), Color(0xFF272733), Color(0xFF1A1A26)) else listOf(Color(0xFFEEEEEE), Color(0xFFF8F8F8), Color(0xFFEEEEEE)),


 start = Offset(shimX - 200f, 0f), end = Offset(shimX + 200f, 0f)


 )





 // 


 val ringTrans = rememberInfiniteTransition(label = "pr")


 val ringScale by ringTrans.animateFloat(


 1f, 1.06f, label = "prs",
 animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
 )


 // 
 // NOTE: copy profilepageani.lottie app/src/main/assets/profilepageani.lottie
 val lottieComp by rememberLottieComposition(
 LottieCompositionSpec.Asset("profilepageani.lottie")
 )
 val lottieProg by animateLottieCompositionAsState(
 composition = lottieComp,
 iterations = LottieConstants.IterateForever,
 isPlaying = true
 )


 // 
 // SKELETON
 // 
 if (state.isLoading) {
 Column(
 Modifier
 .fillMaxSize()
 .background(PBg)
 .padding(scaffoldPadding)
 .statusBarsPadding()
 .verticalScroll(rememberScrollState())
 ) {
 Spacer(Modifier.height(24.dp))
 // Animation placeholder
 Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(180.dp)
 .clip(RoundedCornerShape(24.dp)).background(shimBrush))
 Spacer(Modifier.height(16.dp))
 // Avatar card skeleton
 Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(220.dp)
 .clip(RoundedCornerShape(24.dp)).background(shimBrush))
 Spacer(Modifier.height(16.dp))
 // Stats skeleton
 Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
 repeat(3) { Box(Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(16.dp)).background(shimBrush)) }
 }
 Spacer(Modifier.height(20.dp))
 repeat(2) {
 Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(140.dp)
 .clip(RoundedCornerShape(20.dp)).background(shimBrush))
 Spacer(Modifier.height(14.dp))
 }
 }
 return
 }


 // 
 // LOADED CONTENT
 // 
 Column(
 Modifier
 .fillMaxSize()
 .background(PBg)
 .padding(scaffoldPadding)
 .verticalScroll(rememberScrollState())
 .statusBarsPadding()
 ) {
 Spacer(Modifier.height(20.dp))

 // 
 Box(
 Modifier
 .fillMaxWidth()
 .padding(horizontal = 20.dp)
 .clip(RoundedCornerShape(24.dp))
 .background(PCard)
 .border(1.dp, PBorderHard, RoundedCornerShape(24.dp))
 .padding(vertical = 28.dp, horizontal = 24.dp),
 contentAlignment = Alignment.Center
 ) {
 Column(horizontalAlignment = Alignment.CenterHorizontally) {

 // 
 Box(contentAlignment = Alignment.Center) {
 Box(
 Modifier.size(108.dp).scale(ringScale).clip(CircleShape)
 .border(1.5.dp, PBorder, CircleShape)
 )
 Box(
 Modifier
 .size(96.dp)
 .clip(CircleShape)
 .background(PGhost)
 .border(2.dp, PPrimary, CircleShape)
 .clickable {
 haptic.performHapticFeedback(HapticFeedbackType.LongPress)
 onGalleryLaunch()
 },
 contentAlignment = Alignment.Center
 ) {
 if (state.photoUrl.isNotBlank()) {
 VHMemberPhoto(state.photoUrl, ContentScale.Crop, Modifier.fillMaxSize().clip(CircleShape))
 } else {
 Text(state.emoji.ifBlank { "\uD83C\uDF19" }, fontSize = 38.sp)
 }
 if (isUploadingPhoto) {
 Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
 CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
 }
 }
 }
 // Edit badge
 Box(
 Modifier
 .align(Alignment.BottomEnd)
 .offset(x = 3.dp, y = 3.dp)
 .size(28.dp)
 .clip(CircleShape)
 .background(PFill)
 .border(2.dp, PCard, CircleShape),
 contentAlignment = Alignment.Center
 ) {
 Icon(Icons.Rounded.CameraAlt, null, tint = Color.White, modifier = Modifier.size(14.dp))
 }
 }

 Spacer(Modifier.height(18.dp))

 Text(
 state.alias.ifBlank { "Wanderer" },
 fontSize = 22.sp,
 fontWeight = FontWeight.ExtraBold,
 color = PPrimary,
 letterSpacing = (-0.5).sp
 )
 Spacer(Modifier.height(3.dp))
 Text(
 email.ifBlank { "Signed in with Google" },
 fontSize = 12.sp,
 color = PSub,
 letterSpacing = 0.sp
 )

 Spacer(Modifier.height(16.dp))

 // 
 Row(
 Modifier
 .clip(RoundedCornerShape(6.dp))
 .background(PFill)
 .padding(horizontal = 14.dp, vertical = 7.dp),
 horizontalArrangement = Arrangement.spacedBy(7.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(0.7f)))
 Text(
 "Introvert Veil Member",
 fontSize = 12.sp,
 fontWeight = FontWeight.SemiBold,
 color = Color.White,
 letterSpacing = 0.3.sp
 )
 }

 // Upload error
 if (photoUploadError != null) {
 Spacer(Modifier.height(12.dp))
 Row(
 Modifier
 .clip(RoundedCornerShape(8.dp))
 .border(1.dp, PPrimary.copy(0.15f), RoundedCornerShape(8.dp))
 .background(PGhost)
 .padding(horizontal = 12.dp, vertical = 8.dp),
 horizontalArrangement = Arrangement.spacedBy(8.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Text("", fontSize = 13.sp, color = PPrimary)
 Text(photoUploadError, fontSize = 12.sp, color = PSub)
 }
 }
 }
 }

 Spacer(Modifier.height(14.dp))

 // Stats 
 Row(
 Modifier.fillMaxWidth().padding(horizontal = 20.dp),
 horizontalArrangement = Arrangement.spacedBy(10.dp)
 ) {
 PStatCard(Modifier.weight(1f), top = " $cookieBalance", bottom = "COOKIES")
 PStatCard(Modifier.weight(1f), top = "\uD83C\uDF19", bottom = "ANONYMOUS")
 PStatCard(Modifier.weight(1f), top = "", bottom = "VERIFIED")
 }

 Spacer(Modifier.height(24.dp))

 // Features 
 PSectionLabel("Features")

 PCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
  PMenuRow(icon = "🍪", label = "Cookie Store",
  sub = "Buy cookies · send compliments",
  badge = if (cookieBalance > 0) "$cookieBalance" else null) {
  haptic.performHapticFeedback(HapticFeedbackType.LongPress); onOpenCookieStore()
  }
  PDivider()
  PMenuRow(
      icon  = "🌙",
      label = "VEIL Friends",
      sub   = "AI companions that remember you",
      badge = "NEW"
  ) {
      haptic.performHapticFeedback(HapticFeedbackType.LongPress); onOpenVeilFriends()
  }
  PDivider()
  PMenuRow(icon = "👥", label = "Invite Friends",
  sub = "Share Veil, grow the community",
  badge = "FREE") {
  haptic.performHapticFeedback(HapticFeedbackType.LongPress); onOpenInvite()
  }

  PDivider()

  // ── Rate on Play Store ───────────────────────────────────────────────────
  PMenuRow(
      icon  = "⭐",
      label = "Rate Veil on Play Store",
      sub   = "Your review means the world to us",
      badge = "QUICK"
  ) {
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      android.content.Intent(
          android.content.Intent.ACTION_VIEW,
          android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.ns.findyourintroverfriend")
      ).also { context.startActivity(it) }
  }

  PDivider()

  // ── Tell the Developers ──────────────────────────────────────────────────
  PMenuRow(
      icon  = "💌",
      label = "Tell the Developers",
      sub   = "Feedback, bugs, or just say hi",
      badge = null
  ) {
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
          data = android.net.Uri.parse("mailto:")
          putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("solynlabs@gmail.com"))
          putExtra(android.content.Intent.EXTRA_SUBJECT, "[Veil Feedback] from ${state.alias}")
          putExtra(android.content.Intent.EXTRA_TEXT, "Hi Veil team,\n\n")
      }
      runCatching { context.startActivity(intent) }
  }

  if (isDev) {
  PDivider()
  PMenuRow(icon = "⚙", label = "Dev Panel",
  sub = "Internal tools & debug", badge = "DEV") {
  haptic.performHapticFeedback(HapticFeedbackType.LongPress); onOpenDevPanel()
  }
  }
 }

 Spacer(Modifier.height(14.dp))

 // Account 
 // Appearance
 PSectionLabel("Appearance")

 PCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
  val currentMode = VeilThemeManager.themeMode
  Row(
   modifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 14.dp, vertical = 14.dp),
   horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
   listOf(
    Triple(VeilThemeManager.ThemeMode.SYSTEM, "System", "⊙"),
    Triple(VeilThemeManager.ThemeMode.LIGHT,  "Light",  "☀"),
    Triple(VeilThemeManager.ThemeMode.DARK,   "Dark",   "🌙"),
   ).forEachIndexed { idx, (mode, label, icon) ->
    val isSelected = currentMode == mode
    val segBg by animateColorAsState(
     if (isSelected) PFill else PGhost,
     tween(220), label = "seg$idx"
    )
    val segTxt by animateColorAsState(
     if (isSelected) Color.White else PSub,
     tween(220), label = "tc$idx"
    )
    Box(
     modifier = Modifier
      .weight(1f)
      .clip(RoundedCornerShape(10.dp))
      .background(segBg)
      .border(0.6.dp, if (isSelected) Color.Transparent else PBorder, RoundedCornerShape(10.dp))
      .clickable {
       haptic.performHapticFeedback(HapticFeedbackType.LongPress)
       VeilThemeManager.setTheme(context, mode)
      }
      .padding(vertical = 10.dp),
     contentAlignment = Alignment.Center
    ) {
     Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp)
     ) {
      Text(icon, fontSize = 15.sp, color = segTxt)
      Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = segTxt, letterSpacing = 0.3.sp)
     }
    }
   }
  }
 }

 Spacer(Modifier.height(14.dp))

 PSectionLabel("Account")

 PCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
     PMenuRow(icon = "\uD83D\uDEAA", label = "Sign Out",
         sub = "See you soon", badge = null, isDanger = true) {
         haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLogout()
     }
     PDivider()
     PMenuRow(icon = "\uD83D\uDDD1", label = "Delete Account",
         sub = "Permanently remove your data", badge = null, isDanger = true) {
         haptic.performHapticFeedback(HapticFeedbackType.LongPress)
         android.content.Intent(android.content.Intent.ACTION_VIEW,
             android.net.Uri.parse(deleteAccountUrl)).also { context.startActivity(it) }
     }
 }

 Spacer(Modifier.height(28.dp))

 // ── Lottie banner — now at bottom ──────────────────────────────────────
 Box(
     Modifier
         .fillMaxWidth()
         .padding(horizontal = 20.dp)
         .clip(RoundedCornerShape(24.dp))
         .background(PGhost)
         .border(1.dp, PBorder, RoundedCornerShape(24.dp))
         .height(160.dp),
     contentAlignment = Alignment.Center
 ) {
     LottieAnimation(
         composition = lottieComp,
         progress    = { lottieProg },
         modifier    = Modifier.fillMaxSize()
     )
     Box(
         Modifier
             .align(Alignment.BottomStart)
             .padding(14.dp)
             .clip(RoundedCornerShape(8.dp))
             .background(if (_homeDark) Color(0xFF2A2A3C).copy(0.92f) else Color.White.copy(0.82f))
             .padding(horizontal = 10.dp, vertical = 4.dp)
     ) {
         Text("Your Space", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
             color = if (_homeDark) Color(0xFFDFDFEA) else Color(0xFF1C1916), letterSpacing = 0.5.sp)
     }
 }

 Spacer(Modifier.height(20.dp))

 Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
     Row(horizontalArrangement = Arrangement.Center) {
         Text("Privacy Policy", fontSize = 11.sp, color = PSub,
             style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
             modifier = Modifier.clickable {
                 android.content.Intent(android.content.Intent.ACTION_VIEW,
                     android.net.Uri.parse("https://veil-app.in/privacy")).also { context.startActivity(it) }
             })
         Text("   ", fontSize = 11.sp, color = PMuted)
         Text("Terms of Service", fontSize = 11.sp, color = PSub,
             style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
             modifier = Modifier.clickable {
                 android.content.Intent(android.content.Intent.ACTION_VIEW,
                     android.net.Uri.parse("https://veil-app.in/terms")).also { context.startActivity(it) }
             })
     }
     Spacer(Modifier.height(6.dp))
     Text("Veil — made for introverts \uD83E\uDDD8", fontSize = 11.sp, color = PMuted, letterSpacing = 0.4.sp)
 }

 Spacer(Modifier.height(36.dp))
 }  // Column (loaded content)
}  // ProfileTab







@Composable


private fun PStatCard(modifier: Modifier, top: String, bottom: String) {


 var pressed by remember { mutableStateOf(false) }


 val sc by animateFloatAsState(if (pressed) 0.94f else 1f,


 spring(stiffness = Spring.StiffnessMediumLow), label = "psc")


 LaunchedEffect(pressed) { if (pressed) { kotlinx.coroutines.delay(80); pressed = false } }


 Box(


 modifier


 .scale(sc)


 .clip(RoundedCornerShape(16.dp))


 .background(PGhost)


 .border(1.dp, PBorderHard, RoundedCornerShape(16.dp))


 .clickable { pressed = true }


 .padding(vertical = 16.dp),


 contentAlignment = Alignment.Center


 ) {


 Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {


 Text(top, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = PPrimary)


 Text(bottom, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PMuted, letterSpacing = 1.sp)


 }


 }


}





@Composable


private fun PSectionLabel(label: String) {


 Text(


 label.uppercase(),


 fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = PMuted,


 letterSpacing = 2.sp,


 modifier = Modifier.padding(start = 28.dp, bottom = 8.dp)


 )


}





@Composable


private fun PCard(modifier: Modifier, content: @Composable () -> Unit) {


 Column(


 modifier


 .clip(RoundedCornerShape(20.dp))


 .background(PCard)


 .border(1.dp, PBorderHard, RoundedCornerShape(20.dp))


 ) { content() }


}





@Composable


private fun PDivider() {


 HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = PBorder, thickness = 0.5.dp)


}





@Composable


private fun PMenuRow(


 icon : String,


 label : String,


 sub : String?,


 badge : String?,


 isDanger : Boolean = false,


 onClick : () -> Unit


) {


 var pressed by remember { mutableStateOf(false) }


 val bgAlpha by animateFloatAsState(if (pressed) 0.05f else 0f, tween(80), label = "pm")


 LaunchedEffect(pressed) { if (pressed) { kotlinx.coroutines.delay(110); pressed = false } }





 Row(


 Modifier


 .fillMaxWidth()


 .background(PPrimary.copy(alpha = bgAlpha))


 .clickable { pressed = true; onClick() }


 .padding(horizontal = 18.dp, vertical = 15.dp),


 verticalAlignment = Alignment.CenterVertically,


 horizontalArrangement = Arrangement.spacedBy(14.dp)


 ) {


 // Icon chip monochrome, filled black if danger else outlined


 Box(


 Modifier


 .size(40.dp)


 .clip(RoundedCornerShape(10.dp))


 .background(if (isDanger) PFill else PGhost)


 .border(1.dp, if (isDanger) Color.Transparent else PBorderHard, RoundedCornerShape(10.dp)),


 contentAlignment = Alignment.Center


 ) {


 Text(icon, fontSize = 18.sp)


 }





 // Label + sub


 Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {


 Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,


 color = if (isDanger) PPrimary else PPrimary)


 if (sub != null) Text(sub, fontSize = 12.sp, color = PSub, lineHeight = 16.sp)


 }





 // Right end: badge or chevron


 if (badge != null) {


 Box(


 Modifier


 .clip(RoundedCornerShape(6.dp))


 .background(PFill)


 .padding(horizontal = 8.dp, vertical = 3.dp)


 ) {


 Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.5.sp)


 }


 } else {


 Text("", fontSize = 22.sp, color = PMuted, fontWeight = FontWeight.Light)


 }


 }


}








// ProfileTab 





@Composable


private fun VHMemberPhoto(


 photoUrl : String,


 contentScale : ContentScale = ContentScale.Crop,


 modifier : Modifier = Modifier


) {


 coil.compose.AsyncImage(


 model = photoUrl,


 contentDescription = null,


 contentScale = contentScale,


 modifier = modifier


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


 Text(state.emoji.ifBlank { "o" }, fontSize = 22.sp)


 }


}





@Composable


private fun ModernMenuRow(


 icon : String,


 label : String,


 sublabel : String?,


 tint : Color,


 onClick : () -> Unit


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


 Text("\u203A", fontSize = 20.sp, color = HMuted)


 }


}





@Composable


private fun MenuRow(


 icon : String,


 label : String,


 sublabel : String?,


 tint : Color,


 onClick : () -> Unit


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


 Text("\u203A", fontSize = 18.sp, color = HMuted)


 }


}

// ─────────────────────────────────────────────────────────────────────────────
//  Voice Rooms Banner — floating pill on Tab 0 (Feed)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VoiceRoomsBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "vrb")
    val pulse by inf.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "vrbp",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF4C1D95), Color(0xFF6D28D9), Color(0xFF7C3AED))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF8B5CF6).copy(0.6f), Color(0xFFA78BFA).copy(0.3f))
                ),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment          = Alignment.CenterVertically,
            horizontalArrangement      = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment      = Alignment.CenterVertically,
                horizontalArrangement  = Arrangement.spacedBy(10.dp),
            ) {
                // Live dot
                Box(
                    Modifier.size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0xFF34D399).copy(pulse))
                )
                Column {
                    Text(
                        "🎙️ Voice Rooms",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                    )
                    Text(
                        "Speak freely · up to 25 min",
                        fontSize = 11.sp,
                        color    = Color(0xFFDDD6FE),
                    )
                }
            }
            Text(
                "Join →",
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color(0xFFA78BFA),
            )
        }
    }
}
