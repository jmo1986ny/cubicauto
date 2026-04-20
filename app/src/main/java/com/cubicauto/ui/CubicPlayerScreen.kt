package com.cubicauto.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicauto.model.PlaybackState
import com.cubicauto.model.RepeatMode as PlayerRepeatMode
import com.cubicauto.model.SpotifyConnectionState
import com.cubicauto.model.Track

// ── Cubic color palette ───────────────────────────────────────────────────────
private object C {
    val bg       = Color(0xFF0A0A0C)
    val panel    = Color(0xFF111318)
    val border   = Color(0xFF2A2E3A)
    val border2  = Color(0xFF1E2230)
    val accent   = Color(0xFF00E5FF)
    val accent2  = Color(0xFF00FF9D)
    val accent3  = Color(0xFFFF3366)
    val dim      = Color(0xFF4A5568)
    val text     = Color(0xFFC8D0E0)
    val textDim  = Color(0xFF5A6480)
    val groove   = Color(0xFF0B0D12)
    val knob     = Color(0xFF1C2030)
    val knobHi   = Color(0xFF2A3050)
    val ledOff   = Color(0xFF0D2A30)
    val dark     = Color(0xFF0A0C10)
    val dark2    = Color(0xFF0E1018)
}

private val DEMO_QUEUE = listOf(
    Track("1", "Enter Sandman",        "Metallica", "Metallica",                  330_000),
    Track("2", "Nothing Else Matters", "Metallica", "Metallica",                  388_000),
    Track("3", "Fade To Black",        "Metallica", "Ride The Lightning",         417_000),
    Track("4", "Master Of Puppets",    "Metallica", "Master Of Puppets",          516_000),
    Track("5", "One",                  "Metallica", "...And Justice For All",     445_000),
)

// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CubicPlayerScreen(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val playback   by viewModel.playback.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val spectrum   by viewModel.spectrum.collectAsState()
    val posMs      by viewModel.displayPositionMs.collectAsState()

    Box(modifier.fillMaxSize().background(C.bg)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TitleBar(connection)
            DisplayPanel(playback.track, posMs, playback.isPlaying, spectrum, viewModel::seekTo)
            ControlsRow(playback, viewModel)
            KnobsRow()
            GraphicEQ()
            PlaylistPanel(DEMO_QUEUE, playback.track) { /* viewModel.play(it.id) */ }
            StatusStrip()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Title bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TitleBar(connection: SpotifyConnectionState) {
    val dotAlpha by rememberInfiniteTransition("dot").animateFloat(
        1f, 0.2f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), "da"
    )
    val connected = connection is SpotifyConnectionState.Connected
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1A1E28), Color(0xFF12151C))),
                RoundedCornerShape(4.dp)
            )
            .border(1.dp, C.border2, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically
    ) {
        Text("CUBICAUTO", color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.Black,
            letterSpacing = 4.sp, fontFamily = FontFamily.Monospace)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).background(
                (if (connected) C.accent2 else C.dim).copy(alpha = if (connected) dotAlpha else 1f),
                CircleShape
            ))
            Text(if (connected) "SPOTIFY CONNECTED" else "CONNECTING...",
                color = if (connected) C.accent2 else C.dim,
                fontSize = 8.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
        }
        Text("v1.0", color = C.textDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Display panel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DisplayPanel(
    track: Track, posMs: Long, isPlaying: Boolean,
    spectrum: FloatArray, onSeek: (Float) -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(C.groove)
            .border(1.dp, C.border2, RoundedCornerShape(3.dp))
            .padding(10.dp)
    ) {
        SpectrumAnalyzer(spectrum, isPlaying)
        Spacer(Modifier.height(8.dp))
        MarqueeTrackInfo(track)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            TimeDisplay(posMs, isPlaying)
            Column(horizontalAlignment = Alignment.End) {
                LedIndicators(isPlaying)
                Spacer(Modifier.height(3.dp))
                Text("TOTAL  ${track.durationFormatted}",
                    color = C.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(posMs, track.durationMs, onSeek)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spectrum analyzer
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SpectrumAnalyzer(bands: FloatArray, isPlaying: Boolean) {
    val peakH = remember { FloatArray(32) { 0f } }
    val peakV = remember { FloatArray(32) { 0f } }
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val n    = bands.size
        val gap  = 2f
        val barW = (size.width - gap * (n - 1)) / n
        val H    = size.height
        bands.forEachIndexed { i, raw ->
            val h = (raw * H).coerceIn(2f, H)
            val x = i * (barW + gap)
            val frac = raw.coerceIn(0f, 1f)
            val col = when {
                frac < 0.5f -> lerpColor(C.accent2, C.accent, frac * 2f)
                frac < 0.8f -> lerpColor(C.accent, Color(0xFFFFEE00), (frac - 0.5f) / 0.3f)
                else        -> lerpColor(Color(0xFFFFEE00), C.accent3, (frac - 0.8f) / 0.2f)
            }
            // Bar
            drawRect(col, Offset(x, H - h), Size(barW, h), alpha = 0.9f)
            // Glow
            drawRect(
                Brush.verticalGradient(listOf(col.copy(0.25f), Color.Transparent), H - h, H),
                Offset(x - 1f, H - h), Size(barW + 2f, h)
            )
            // Peak
            if (isPlaying) {
                if (h > peakH[i]) { peakH[i] = h; peakV[i] = 0f }
                else { peakV[i] += 0.4f; peakH[i] = (peakH[i] - peakV[i]).coerceAtLeast(0f) }
            } else {
                peakH[i] = peakH[i] * 0.92f
            }
            drawRect(C.accent, Offset(x, H - peakH[i] - 2f), Size(barW, 2f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scrolling marquee
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MarqueeTrackInfo(track: Track) {
    val scroll by rememberInfiniteTransition("mq").animateFloat(
        1f, -1f, infiniteRepeatable(tween(13_000, easing = LinearEasing)), "sc"
    )
    Column(
        Modifier.fillMaxWidth()
            .background(C.dark)
            .border(1.dp, C.border2)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(0.dp))) {
            Text(
                (track.title + "   ✦   " + track.artist + "   ✦   " + track.album + "   ✦   ").uppercase(),
                color = C.accent, fontSize = 22.sp, fontFamily = FontFamily.Monospace,
                maxLines = 1, softWrap = false,
                modifier = Modifier.graphicsLayer { translationX = scroll * 700f }
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "${track.artist.uppercase()} · ${track.album.uppercase()}",
            color = C.accent2, fontSize = 9.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Time display with blinking colon
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TimeDisplay(posMs: Long, isPlaying: Boolean) {
    val colon by rememberInfiniteTransition("colon").animateFloat(
        1f, 0f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), "ca"
    )
    val s = posMs / 1000
    Row(verticalAlignment = Alignment.Bottom) {
        listOf("%02d".format(s / 60), ":", "%02d".format(s % 60)).forEachIndexed { i, seg ->
            Text(seg,
                color = if (i == 1) C.accent.copy(if (isPlaying) colon else 1f) else C.accent,
                fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                lineHeight = 34.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LED indicators
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LedIndicators(isPlaying: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Led("PLAY", C.accent,  isPlaying)
        Led("SHUF", C.accent2, false)
        Led("REP",  C.accent,  false)
        Led("REC",  C.accent3, false)
    }
}

@Composable
private fun Led(label: String, color: Color, on: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.size(8.dp).background(if (on) color else C.ledOff, CircleShape)
            .then(if (on) Modifier.drawBehind { drawCircle(color.copy(0.35f), size.minDimension) } else Modifier))
        Text(label, color = C.textDim, fontSize = 7.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Progress bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ProgressBar(posMs: Long, durMs: Long, onSeek: (Float) -> Unit) {
    val frac = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
    Box(
        Modifier.fillMaxWidth().height(12.dp)
            .background(C.dark, RoundedCornerShape(2.dp))
            .border(1.dp, C.border2, RoundedCornerShape(2.dp))
            .pointerInput(durMs) { detectTapGestures { o -> onSeek(o.x / size.width) } }
            .pointerInput(durMs) { detectHorizontalDragGestures { c, _ -> onSeek(c.position.x / size.width) } }
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac)
            .background(Brush.horizontalGradient(listOf(C.accent2, C.accent)), RoundedCornerShape(2.dp)))
        // Playhead pip
        Box(Modifier.align(Alignment.CenterStart).offset(x = (frac * 300).dp - 1.5.dp)
            .height(12.dp).width(3.dp).background(Color.White.copy(0.9f)))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, top = 2.dp), Arrangement.SpaceBetween) {
        val total = durMs / 1000
        listOf(0L, total / 4, total / 2, 3 * total / 4, total).forEach { t ->
            Text("%02d:%02d".format(t / 60, t % 60),
                color = C.dim, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ControlsRow(state: PlaybackState, vm: PlayerViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        Arrangement.Center, Alignment.CenterVertically
    ) {
        CBtn("⏮", 44.dp, 32.dp, onClick = vm::skipPrevious)
        Spacer(Modifier.width(4.dp))
        CBtn("⏪", 44.dp, 32.dp)
        Spacer(Modifier.width(4.dp))
        CBtn(if (state.isPlaying) "⏸" else "▶", 64.dp, 46.dp, accent = true, onClick = vm::playPause)
        Spacer(Modifier.width(4.dp))
        CBtn("⏩", 44.dp, 32.dp)
        Spacer(Modifier.width(4.dp))
        CBtn("⏭", 44.dp, 32.dp, onClick = vm::skipNext)
        Spacer(Modifier.width(14.dp))
        CBtn("⇄", 40.dp, 32.dp, active = state.shuffle, activeColor = C.accent2, onClick = vm::toggleShuffle)
        Spacer(Modifier.width(4.dp))
        CBtn(if (state.repeatMode == PlayerRepeatMode.ONE) "↺" else "↻", 40.dp, 32.dp,
            active = state.repeatMode != PlayerRepeatMode.OFF, onClick = vm::cycleRepeat)
        Spacer(Modifier.width(4.dp))
        CBtn("☰", 40.dp, 32.dp)
    }
}

@Composable
private fun CBtn(
    label: String, w: Dp, h: Dp,
    accent: Boolean = false, active: Boolean = false, activeColor: Color = C.accent,
    onClick: () -> Unit = {}
) {
    val bg = if (accent) Brush.verticalGradient(listOf(Color(0xFF0D3040), Color(0xFF081C28)))
             else        Brush.verticalGradient(listOf(C.knobHi, C.knob))
    val border = when { accent -> C.accent; active -> activeColor; else -> Color(0xFF252840) }
    val tint   = when { accent -> C.accent; active -> activeColor; else -> C.text }
    Box(Modifier.width(w).height(h)
        .background(bg, RoundedCornerShape(4.dp))
        .border(1.dp, border, RoundedCornerShape(4.dp))
        .clickable(onClick = onClick),
        Alignment.Center
    ) {
        Text(label, color = tint, fontSize = if (accent) 20.sp else 14.sp, fontFamily = FontFamily.Monospace)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Knobs
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun KnobsRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically
    ) {
        Knob("VOL",  "75",  -30f)
        Knob("BAL",  "C",    10f)
        VolSlider(0.75f)
        Knob("BASS", "+3",  -60f)
        Knob("TREB", "+1",   20f)
    }
}

@Composable
private fun Knob(label: String, value: String, initAngle: Float) {
    var angle by remember { mutableStateOf(initAngle) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Canvas(Modifier.size(42.dp)) {
            val cx = size.width / 2; val cy = size.height / 2; val r = size.minDimension / 2 - 2f
            drawCircle(Color(0xFF0E1018), r + 3f, Offset(cx, cy))
            drawCircle(
                Brush.radialGradient(listOf(C.knobHi, C.knob, Color(0xFF0D0F18)),
                    Offset(cx * 0.75f, cy * 0.75f), r),
                r, Offset(cx, cy)
            )
            drawCircle(Color(0xFF252840), r, Offset(cx, cy), style = Stroke(1.dp.toPx()))
            val rad = Math.toRadians((angle - 90).toDouble())
            val tx = cx + (r - 5f) * Math.cos(rad).toFloat()
            val ty = cy + (r - 5f) * Math.sin(rad).toFloat()
            val bx = cx + (r * 0.3f) * Math.cos(rad).toFloat()
            val by = cy + (r * 0.3f) * Math.sin(rad).toFloat()
            drawLine(C.accent, Offset(bx, by), Offset(tx, ty), 2.5f, cap = StrokeCap.Round)
        }
        Text(label, color = C.textDim, fontSize = 8.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = C.accent2,  fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun VolSlider(level: Float) {
    Column(Modifier.width(110.dp), Alignment.CenterHorizontally, Arrangement.spacedBy(3.dp)) {
        Box(Modifier.fillMaxWidth().height(8.dp)
            .background(C.dark, RoundedCornerShape(2.dp))
            .border(1.dp, C.border2, RoundedCornerShape(2.dp))
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(level)
                .background(Brush.horizontalGradient(listOf(C.accent2, C.accent)), RoundedCornerShape(2.dp)))
        }
        Text("VOLUME", color = C.textDim, fontSize = 7.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("0",   color = C.dim,    fontSize = 7.sp,  fontFamily = FontFamily.Monospace)
            Text("${(level * 100).toInt()}", color = C.accent, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            Text("100", color = C.dim,    fontSize = 7.sp,  fontFamily = FontFamily.Monospace)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Graphic EQ
// ─────────────────────────────────────────────────────────────────────────────
private val EQ_FREQS  = listOf("60", "150", "400", "1K", "2.4K", "6K", "15K", "20K")
private val EQ_LEVELS = listOf(0.40f, 0.55f, 0.70f, 0.80f, 0.65f, 0.50f, 0.45f, 0.35f)

@Composable
private fun GraphicEQ() {
    Column(
        Modifier.fillMaxWidth()
            .background(C.groove)
            .border(1.dp, C.border2, RoundedCornerShape(3.dp))
            .padding(8.dp)
    ) {
        Text("GRAPHIC EQUALIZER", color = C.textDim, fontSize = 8.sp, letterSpacing = 3.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(50.dp), Arrangement.spacedBy(4.dp)) {
            EQ_FREQS.forEachIndexed { i, freq ->
                Column(Modifier.weight(1f), Alignment.CenterHorizontally, Arrangement.spacedBy(2.dp)) {
                    val barColor = if (i == EQ_FREQS.lastIndex) C.accent3 else C.accent
                    Box(Modifier.weight(1f).width(6.dp)
                        .background(C.dark, RoundedCornerShape(1.dp))
                        .border(1.dp, C.border2, RoundedCornerShape(1.dp))
                    ) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(EQ_LEVELS[i])
                            .background(barColor, RoundedCornerShape(bottomStart = 1.dp, bottomEnd = 1.dp)))
                    }
                    Text(freq, color = C.dim, fontSize = 6.sp, fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Playlist
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PlaylistPanel(tracks: List<Track>, current: Track, onTrackClick: (Track) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(C.groove)
            .border(1.dp, C.border2, RoundedCornerShape(3.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().background(C.dark2)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Text("QUEUE · SPOTIFY", color = C.textDim, fontSize = 8.sp,
                letterSpacing = 3.sp, fontFamily = FontFamily.Monospace)
            Text("${tracks.size} TRACKS", color = C.accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        HDivider()
        tracks.forEachIndexed { i, track ->
            val active = track.id == current.id
            Row(
                Modifier.fillMaxWidth()
                    .background(if (active) C.accent.copy(0.08f) else Color.Transparent)
                    .then(if (active) Modifier.drawBehind {
                        drawRect(C.accent, size = Size(3.dp.toPx(), size.height))
                    } else Modifier)
                    .clickable { onTrackClick(track) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("%02d".format(i + 1), color = if (active) C.accent else C.dim,
                    fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = if (active) C.accent else C.text,
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = C.dim, fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                }
                Text(track.durationFormatted, color = C.textDim,
                    fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            if (i < tracks.lastIndex) HDivider(color = Color(0xFF0D0F14))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status strip
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatusStrip() {
    Row(
        Modifier.fillMaxWidth()
            .background(C.dark2, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .border(1.dp, C.border2, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically
    ) {
        listOf(
            C.accent2 to "SPOTIFY",
            C.accent  to "320KBPS",
            C.accent  to "STEREO",
            C.accent3 to "LIVE",
            C.accent  to "44.1KHZ"
        ).forEach { (color, label) ->
            Text(label, color = color, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────
private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    (a.red   + (b.red   - a.red)   * t).coerceIn(0f, 1f),
    (a.green + (b.green - a.green) * t).coerceIn(0f, 1f),
    (a.blue  + (b.blue  - a.blue)  * t).coerceIn(0f, 1f),
    1f
)

@Composable
private fun HDivider(color: Color = C.border2) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}
