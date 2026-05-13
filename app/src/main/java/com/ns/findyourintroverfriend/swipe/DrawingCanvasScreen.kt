package com.ns.findyourintroverfriend.swipe

import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

// ─────────────────────────────────────────────────────────────────────────────
//  Internal model — PenStroke avoids name clash with drawscope.Stroke
// ─────────────────────────────────────────────────────────────────────────────

private data class PenStroke(val pts: List<Offset>, val color: Color, val width: Float)

private val DCPrimary   = Color(0xFF000000)
private val DCSecondary = Color(0xFF737373)
private val DCSurface   = Color(0xFFFFFFFF)
private val DCDivider   = Color(0xFFEFEFEF)

// ─────────────────────────────────────────────────────────────────────────────
//  Full-screen Drawing Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DrawingCanvasScreen(
    matchId       : String,
    uid           : String,
    myAlias       : String,
    cookieBalance : Int,
    onSpendCookies: (amount: Int, reason: String, cb: (Boolean) -> Unit) -> Unit,
    onDismiss     : () -> Unit
) {
    val scope   = rememberCoroutineScope()
    val db      = remember { FirebaseFirestore.getInstance() }

    val strokes     = remember { mutableStateListOf<PenStroke>() }
    val currentPts  = remember { mutableStateListOf<Offset>() }
    var penColor    by remember { mutableStateOf(Color.Black) }
    var penWidth    by remember { mutableStateOf(8f) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf("") }
    var canvasSize  by remember { mutableStateOf(IntSize(0, 0)) }

    val palette = listOf(
        Color.Black, Color(0xFFE91E63), Color(0xFF2196F3),
        Color(0xFF4CAF50), Color(0xFFFF9800), Color.White
    )

    fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPenStroke(
        pts: List<Offset>, c: Color, w: Float
    ) {
        if (pts.size < 2) return
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, color = c, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    Box(Modifier.fillMaxSize().background(DCSurface)) {

        // ── Drawing surface ────────────────────────────────────────────────
        Canvas(
            modifier = Modifier.fillMaxSize().background(Color.White)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { currentPts.clear(); currentPts.add(it) },
                        onDrag      = { change, _ -> currentPts.add(change.position) },
                        onDragEnd   = {
                            if (currentPts.size > 1)
                                strokes.add(PenStroke(currentPts.toList(), penColor, penWidth))
                            currentPts.clear()
                        }
                    )
                }
        ) {
            drawRect(Color.White)
            strokes.forEach { drawPenStroke(it.pts, it.color, it.width) }
            if (currentPts.size > 1) drawPenStroke(currentPts, penColor, penWidth)
        }

        // ── Top bar ───────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .background(DCSurface.copy(alpha = 0.95f))
                .border(BorderStroke(0.5.dp, DCDivider))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, tint = DCPrimary) }
            Text("Drawing Canvas", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = DCPrimary, modifier = Modifier.weight(1f))
            IconButton(
                onClick  = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                enabled  = strokes.isNotEmpty()
            ) { Icon(Icons.Rounded.Undo, null, tint = if (strokes.isNotEmpty()) DCPrimary else DCDivider) }
            IconButton(onClick = { strokes.clear(); currentPts.clear() }) {
                Icon(Icons.Rounded.DeleteOutline, null, tint = DCSecondary)
            }
        }

        // ── Bottom bar ────────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(DCSurface.copy(alpha = 0.95f))
                .border(BorderStroke(0.5.dp, DCDivider))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (uploadError.isNotBlank()) {
                Text(uploadError, fontSize = 11.sp, color = Color(0xFFED4956),
                    modifier = Modifier.padding(bottom = 6.dp))
            }

            // Pen size
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()) {
                Text("Size", fontSize = 11.sp, color = DCSecondary)
                listOf(4f to "S", 8f to "M", 14f to "L").forEach { (w, label) ->
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (penWidth == w) DCPrimary else DCDivider)
                            .clickable { penWidth = w }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = if (penWidth == w) Color.White else DCSecondary)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Color palette + Send
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.forEach { c ->
                    val selected = (penColor == c)
                    Box(
                        Modifier.size(if (selected) 34.dp else 28.dp).clip(CircleShape)
                            .background(if (c == Color.White) Color(0xFFEEEEEE) else c)
                            .then(if (selected) Modifier.border(2.dp, DCPrimary, CircleShape) else Modifier)
                            .clickable { penColor = c }
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        isUploading = true; uploadError = ""
                        // Deduct 10 cookies before uploading
                        onSpendCookies(10, "Drawing Canvas") { ok ->
                            if (!ok) {
                                isUploading = false
                                uploadError = "Not enough cookies (need 10)."
                                return@onSpendCookies
                            }
                            scope.launch {
                                try {
                                    val w   = canvasSize.width.coerceAtLeast(300)
                                    val h   = canvasSize.height.coerceAtLeast(300)
                                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    val nc  = NativeCanvas(bmp)
                                    nc.drawColor(android.graphics.Color.WHITE)
                                    val paint = NativePaint().apply {
                                        isAntiAlias = true; style = NativePaint.Style.STROKE
                                        strokeCap = NativePaint.Cap.ROUND; strokeJoin = NativePaint.Join.ROUND
                                    }
                                    strokes.forEach { ps ->
                                        if (ps.pts.size < 2) return@forEach
                                        paint.strokeWidth = ps.width
                                        paint.color = android.graphics.Color.argb(
                                            (ps.color.alpha * 255).toInt(),
                                            (ps.color.red   * 255).toInt(),
                                            (ps.color.green * 255).toInt(),
                                            (ps.color.blue  * 255).toInt()
                                        )
                                        val nPath = NativePath()
                                        ps.pts.forEachIndexed { i, p ->
                                            if (i == 0) nPath.moveTo(p.x, p.y) else nPath.lineTo(p.x, p.y)
                                        }
                                        nc.drawPath(nPath, paint)
                                    }
                                    val out = ByteArrayOutputStream()
                                    bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                                    val url = CloudinaryUploader.uploadBytes(
                                        bytes = out.toByteArray(),
                                        fileName = "drawing_${System.currentTimeMillis()}.png",
                                        resourceType = "image", mimeType = "image/png",
                                        folder = "veil/drawings/$matchId"
                                    )
                                    db.collection("matches").document(matchId)
                                        .collection("messages")
                                        .add(SwipeMessage(
                                            messageId = db.collection("_").document().id,
                                            senderUid = uid, senderAlias = myAlias,
                                            text = "drawing::$url",
                                            sentAt = System.currentTimeMillis()
                                        )).await()
                                    onDismiss()
                                } catch (_: Exception) {
                                    uploadError = "Upload failed. Check connection."
                                    isUploading = false
                                }
                            }
                        }
                    },
                    enabled = strokes.isNotEmpty() && !isUploading && cookieBalance >= 10,
                    shape   = RoundedCornerShape(12.dp),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = DCPrimary,
                        disabledContainerColor = Color(0xFFBDBDBD)
                    )
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Send, null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text(if (cookieBalance >= 10) "Send · 10" else "Need 10", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Drawing bubble shown in chat
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DrawingBubble(url: String) {
    AsyncImage(
        model = url, contentDescription = "Drawing",
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(200.dp).clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, DCDivider), RoundedCornerShape(16.dp))
            .background(Color.White)
    )
}
