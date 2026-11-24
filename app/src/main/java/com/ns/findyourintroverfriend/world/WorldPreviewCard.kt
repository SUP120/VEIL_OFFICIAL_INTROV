package com.ns.findyourintroverfriend.world

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ns.findyourintroverfriend.BuildConfig
import com.ns.findyourintroverfriend.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun WorldPreviewCard(dark: Boolean, onOpenWhisper: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.getBooleanExtra("openWhisper", false) == true) {
            onOpenWhisper()
        }
    }

    WorldCard(
        dark = dark,
        opening = opening,
        notice = notice,
        onPlay = {
            opening = true
            notice = null
            scope.launch {
                try {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user == null) {
                        notice = "Sign in to open VEIL World."
                        return@launch
                    }
                    context.assets.open("veil_world.pck").close()
                    val online = BuildConfig.VEIL_WORLD_ONLINE_URL.isNotEmpty()
                    val token = if (online) {
                        try {
                            withTimeoutOrNull(8000) { user.getIdToken(false).await().token }
                        } catch (_: FirebaseNetworkException) {
                            null
                        }
                    } else {
                        null
                    }
                    val alias = publicWorldAlias(user.uid)
                    if (FirebaseAuth.getInstance().currentUser?.uid != user.uid) {
                        notice = "Sign in to open VEIL World."
                        return@launch
                    }
                    launcher.launch(
                        Intent().setClassName(context.packageName, "${context.packageName}.world.VeilWorldActivity")
                            .putExtra("worldAlias", alias)
                            .putExtra("worldJournal", user.uid)
                            .putExtra("worldLight", !dark)
                            .putExtra("worldIdToken", token)
                    )
                } catch (_: IOException) {
                    notice = "The world pack is missing. Reinstall this version of VEIL."
                } catch (_: ActivityNotFoundException) {
                    notice = "The world preview is unavailable in this build."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    notice = "Could not open VEIL World. Try again."
                } finally {
                    opening = false
                }
            }
        }
    )
}

@Composable
private fun WorldCard(
    dark: Boolean,
    opening: Boolean,
    notice: String?,
    onPlay: () -> Unit
) {
    val surface = if (dark) Color(0xFF17171E) else Color.White
    val border = if (dark) Color(0xFF2C2C34) else Color(0xFFE8E3DC)
    val primary = if (dark) Color(0xFFE0DDD8) else Color(0xFF2C2825)
    val secondary = if (dark) Color(0xFFABA6A0) else Color(0xFF716B66)
    val sage = if (dark) Color(0xFFA5B4A7) else Color(0xFF596E60)
    val sageSoft = if (dark) Color(0xFF232B27) else Color(0xFFEDF1EC)
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(surface)
            .border(1.dp, border, shape)
    ) {
        Image(
            painter = painterResource(R.drawable.veil_world_orchard),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(146.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("VEIL World", color = primary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (BuildConfig.VEIL_WORLD_ONLINE_URL.isEmpty())
                        "Walk the forest trails solo or host friends on the same Wi-Fi."
                    else "Meet in the forest park, follow a trail, or visit a private island.",
                    color = secondary, fontSize = 13.sp, lineHeight = 18.sp
                )
                notice?.let { Text(it, color = primary, fontSize = 12.sp) }
            }
            Button(
                onClick = onPlay,
                enabled = !opening,
                colors = ButtonDefaults.buttonColors(containerColor = sageSoft, contentColor = sage)
            ) {
                Text(if (opening) "Opening…" else "Play")
            }
        }
    }
}

private suspend fun publicWorldAlias(uid: String): String = try {
    withTimeoutOrNull(1500) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
            .getString("alias")?.trim()?.takeIf { it.isNotEmpty() }
    } ?: "Wanderer"
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    "Wanderer"
}
