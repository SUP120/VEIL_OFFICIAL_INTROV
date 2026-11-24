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
import com.ns.findyourintroverfriend.feed.peopleProfileFields
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update




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





internal const val DISPLAY_NAME_MIN_LENGTH = 2
internal const val DISPLAY_NAME_MAX_LENGTH = 24

// Data model 


data class VeilHomeUiState(


 val alias : String = "",


 val emoji : String = "\uD83C\uDF19",


 val photoUrl : String = "",

 val joinedAt : Long = 0L,


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

 joinedAt = when (val joined = doc.get("createdAt") ?: doc.get("joinedAt")) {
     is com.google.firebase.Timestamp -> joined.toDate().time
     is Number -> joined.toLong()
     else -> 0L
 },


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





 internal suspend fun saveDisplayName(input: String): String? {
    val alias = input.trim()
    if (alias.length !in DISPLAY_NAME_MIN_LENGTH..DISPLAY_NAME_MAX_LENGTH) {
        return "Use a display name between $DISPLAY_NAME_MIN_LENGTH and $DISPLAY_NAME_MAX_LENGTH characters."
    }
    if (alias.any(Char::isISOControl)) return "Enter a display name on one line."
    val uid = auth.currentUser?.uid ?: return "Sign in to edit your display name."
    val user = db.collection("users").document(uid)
    val peopleSettings = db.collection("peopleSettings").document(uid)
    val peopleProfile = db.collection("peopleProfiles").document(uid)
    val swipeProfile = db.collection("swipeProfiles").document(uid)
    return try {
        val saved = db.runTransaction { transaction ->
            val current = transaction.get(user)
            if (!current.exists()) return@runTransaction false

            val settings = transaction.get(peopleSettings)
            val swipe = transaction.get(swipeProfile)
            val oldName = current.getString("alias").orEmpty()
            val swipeAlias = swipe.getString("alias").orEmpty()

            transaction.update(user, "alias", alias)
            if (settings.getBoolean("listed") == true && current.getBoolean("profileComplete") == true &&
                current.getBoolean("isReviewerAccount") != true && current.getBoolean("isBanned") != true
            ) {
                transaction.set(peopleProfile, peopleProfileFields(alias, settings.getString("username").orEmpty()))
            }
            if (swipe.exists() && (swipeAlias.isBlank() || swipeAlias == oldName)) {
                transaction.update(swipeProfile, "alias", alias)
            }
            true
        }.await()
        if (!saved) return "Finish setting up your profile to edit your display name."
        _state.update { it.copy(alias = alias) }
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        "Couldn't save your display name. Please try again."
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
 onOpenHelpSafety    : () -> Unit = {},
 onOpenPeople        : () -> Unit = {},


 ) {


 val state by vm.state.collectAsStateWithLifecycle()


 val scroll = androidx.compose.foundation.rememberScrollState()


 val haptic = LocalHapticFeedback.current


 val context = LocalContext.current


 val scope = androidx.compose.runtime.rememberCoroutineScope()


 val swipeVm : com.ns.findyourintroverfriend.swipe.SwipeViewModel = viewModel()


 val authUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }


 val db = remember { FirebaseFirestore.getInstance() }





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


 if (!ok) photoUploadError = err


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

         onOpenUserProfile = onOpenUserProfile,
         onOpenPeople = onOpenPeople

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


 3 -> OwnProfileTab(
     uid = authUid,
     email = FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
     state = state,
     isDev = isDev,
     uploadingPhoto = isUploadingPhoto,
     photoUploadError = photoUploadError,
     bottomAndSystemPadding = scaffoldPadding,
     onChangePhoto = { galleryLauncher.launch("image/*") },
     onSaveDisplayName = vm::saveDisplayName,
     onOpenProfileThemes = onOpenProfileThemes,
     onOpenPeople = onOpenPeople,
     onOpenVeilFriends = onOpenVeilFriends,
     onOpenCookieStore = onOpenCookieStore,
     onOpenInvite = onOpenInvite,
     onOpenHelpSafety = onOpenHelpSafety,
     onOpenDevPanel = onOpenDevPanel,
     onLogout = onLogout,
     onOpenHome = {
         previousTab = selectedTab
         selectedTab = 0
     }
 )


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
}











//

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
