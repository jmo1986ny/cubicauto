package com.cubicauto.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicauto.R
import com.cubicauto.model.PlaybackState
import com.cubicauto.model.RepeatMode as PlayerRepeatMode
import com.cubicauto.model.SpotifyConnectionState
import com.cubicauto.model.Track
import com.cubicauto.model.TrackSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ── Font ──────────────────────────────────────────────────────────────────────
// Consolas is loaded from res/font/consolas.ttf (copy the file there)
// Fallback to system monospace if not present
private val CONSOLAS: FontFamily by lazy {
    try {
        FontFamily(Font(R.font.consolas, FontWeight.Normal))
    } catch (e: Exception) {
        FontFamily.Monospace
    }
}

// ── CLI palette ───────────────────────────────────────────────────────────────
private object T {
    val bg       = Color(0xFF000000)
    val surface  = Color(0xFF0A0A0A)
    val border   = Color(0xFF1E1E1E)
    val white    = Color(0xFFEEEEEE)
    val dim      = Color(0xFF555555)
    val dimmer   = Color(0xFF2A2A2A)
    val green    = Color(0xFF00FF41)
    val greenDim = Color(0xFF003B0E)
    val amber    = Color(0xFFFFB000)
    val red      = Color(0xFFFF3333)
    val cyan     = Color(0xFF00FFFF)
    val magenta  = Color(0xFFFF00FF)
    val cursor   = Color(0xFF00FF41)
}

private enum class VisMode { BARS, MILKDROP }

@Composable
fun CubicPlayerScreen(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val playback   by viewModel.playback.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val spectrum   by viewModel.spectrum.collectAsState()
    val posMs      by viewModel.displayPositionMs.collectAsState()
    var visMode    by remember { mutableStateOf(VisMode.BARS) }

    Box(modifier.fillMaxSize().background(T.bg)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            StatusBar(connection, playback)
            Divider()
            NowPlaying(playback.track, posMs, playback.isPlaying)
            Divider()
            // Tappable visualizer — toggles between BARS and MILKDROP
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clickable { visMode = if (visMode == VisMode.BARS) VisMode.MILKDROP else VisMode.BARS }
            ) {
                when (visMode) {
                    VisMode.BARS     -> SpectrumBars(spectrum, playback.isPlaying)
                    VisMode.MILKDROP -> MilkDrop(spectrum, playback.isPlaying)
                }
                // Mode label
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Text(
                        if (visMode == VisMode.BARS) "SPECTRUM" else "MILKDROP",
                        color = T.dim, fontSize = 8.sp, fontFamily = CONSOLAS
                    )
                }
            }
            Divider()
            ProgressRow(posMs, playback.durationMs)
            Divider()
            TransportRow(playback, viewModel)
            Divider()
            QueuePanel(viewModel)
            Divider()
            SourcePanel(viewModel)
            BottomBar()
        }
    }
}

// ── Status bar ────────────────────────────────────────────────────────────────
@Composable
private fun StatusBar(connection: SpotifyConnectionState, playback: PlaybackState) {
    val inf = rememberInfiniteTransition(label = "cur")
    val cur by inf.animateFloat(1f, 0f,
        infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), "c")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("CUBICAUTO", color = T.green, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, fontFamily = CONSOLAS, letterSpacing = 3.sp)
            Text("v1.0", color = T.dim, fontSize = 10.sp, fontFamily = CONSOLAS)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val srcColor = if (playback.source == TrackSource.LOCAL) T.green else T.amber
            Tag(if (playback.source == TrackSource.LOCAL) "LOCAL" else "SPOTIFY", srcColor)
            val connColor = if (connection is SpotifyConnectionState.Connected) T.green else T.dim
            Tag(if (connection is SpotifyConnectionState.Connected) "CONN" else "DISC", connColor)
        }
        Box(Modifier.size(width = 8.dp, height = 14.dp)
            .background(T.cursor.copy(alpha = cur)))
    }
}

// ── Now playing ───────────────────────────────────────────────────────────────
@Composable
private fun NowPlaying(track: Track, posMs: Long, isPlaying: Boolean) {
    val inf = rememberInfiniteTransition(label = "colon")
    val colon by inf.animateFloat(1f, 0f,
        infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), "cl")
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(">>", color = T.green, fontSize = 11.sp, fontFamily = CONSOLAS)
            Text("NOW PLAYING", color = T.dim, fontSize = 10.sp,
                fontFamily = CONSOLAS, letterSpacing = 2.sp)
        }
        Text(
            track.title.uppercase().ifEmpty { "NO TRACK LOADED" },
            color = T.white, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            fontFamily = CONSOLAS, maxLines = 1, overflow = TextOverflow.Ellipsis,
            letterSpacing = 1.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(track.artist.uppercase().ifEmpty { "UNKNOWN" },
                color = T.green, fontSize = 11.sp, fontFamily = CONSOLAS)
            Text("/", color = T.dimmer, fontSize = 11.sp, fontFamily = CONSOLAS)
            Text(track.album.uppercase().ifEmpty { "UNKNOWN ALBUM" },
                color = T.dim, fontSize = 11.sp, fontFamily = CONSOLAS,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val s   = posMs / 1000
        val dur = track.durationMs / 1000
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("[", color = T.dimmer, fontSize = 13.sp, fontFamily = CONSOLAS)
            Text("%02d".format(s / 60), color = T.green, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, fontFamily = CONSOLAS)
            Text(":", color = T.green.copy(alpha = if (isPlaying) colon else 1f),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = CONSOLAS)
            Text("%02d".format(s % 60), color = T.green, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, fontFamily = CONSOLAS)
            Text("/", color = T.dimmer, fontSize = 13.sp, fontFamily = CONSOLAS)
            Text("%02d:%02d".format(dur / 60, dur % 60),
                color = T.dim, fontSize = 13.sp, fontFamily = CONSOLAS)
            Text("]", color = T.dimmer, fontSize = 13.sp, fontFamily = CONSOLAS)
            Spacer(Modifier.width(8.dp))
            Text(if (isPlaying) "▶ PLAYING" else "■ PAUSED",
                color = if (isPlaying) T.green else T.dim,
                fontSize = 10.sp, fontFamily = CONSOLAS, letterSpacing = 2.sp)
        }
    }
}

// ── Visualizer: classic spectrum bars ────────────────────────────────────────
@Composable
private fun SpectrumBars(bands: FloatArray, isPlaying: Boolean) {
    val peakH = remember { FloatArray(32) { 0f } }
    val peakV = remember { FloatArray(32) { 0f } }
    Canvas(Modifier.fillMaxSize()) {
        val n    = bands.size
        val gap  = 1.5f
        val barW = (size.width - gap * (n - 1)) / n
        val H    = size.height
        bands.forEachIndexed { i, raw ->
            val h   = (raw * H).coerceIn(1f, H)
            val x   = i * (barW + gap)
            val col = when {
                raw > 0.8f -> T.red
                raw > 0.5f -> T.amber
                else       -> T.green
            }
            drawRect(col.copy(alpha = 0.9f), Offset(x, H - h), Size(barW, h))
            // Subtle gradient overlay
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.07f), Color.Transparent),
                    startY = H - h, endY = H
                ),
                Offset(x, H - h), Size(barW, h)
            )
            if (isPlaying) {
                if (h > peakH[i]) { peakH[i] = h; peakV[i] = 0f }
                else { peakV[i] += 0.35f; peakH[i] = (peakH[i] - peakV[i]).coerceAtLeast(0f) }
            } else {
                peakH[i] *= 0.9f
            }
            drawRect(T.green, Offset(x, H - peakH[i] - 1f), Size(barW, 1.5f))
        }
        // Bottom line
        drawLine(T.green.copy(0.3f), Offset(0f, H - 1f), Offset(size.width, H - 1f), 1f)
    }
}

// ── Visualizer: MilkDrop-style ────────────────────────────────────────────────
// Oscilloscope waveform + radial energy burst + tunnel rings
@Composable
private fun MilkDrop(bands: FloatArray, isPlaying: Boolean) {
    val inf  = rememberInfiniteTransition(label = "md")
    val time by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(4000, easing = LinearEasing)), "t"
    )
    val time2 by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(7000, easing = LinearEasing)), "t2"
    )

    Canvas(Modifier.fillMaxSize()) {
        val W   = size.width
        val H   = size.height
        val cx  = W / 2f
        val cy  = H / 2f

        // Bass / treble energy
        val bass   = bands.take(4).average().toFloat().coerceIn(0f, 1f)
        val mid    = bands.drop(8).take(8).average().toFloat().coerceIn(0f, 1f)
        val treble = bands.drop(20).average().toFloat().coerceIn(0f, 1f)

        if (!isPlaying) {
            // Idle: slow dim rings
            drawMilkDropRings(cx, cy, minOf(W, H) * 0.35f, time, 0.1f, 0.1f, 0.1f)
            return@Canvas
        }

        // ── Tunnel rings ──────────────────────────────────────────────────────
        drawMilkDropRings(cx, cy, minOf(W, H) * 0.45f, time, bass, mid, treble)

        // ── Radial burst lines ────────────────────────────────────────────────
        val numRays = 32
        for (j in 0 until numRays) {
            val angle  = (j.toFloat() / numRays) * 2f * PI.toFloat() + time
            val energy = bands.getOrElse(j % bands.size) { 0f }
            val r0     = minOf(W, H) * 0.08f
            val r1     = r0 + energy * minOf(W, H) * 0.35f
            val cos    = cos(angle); val sin = sin(angle)
            val alpha  = (energy * 0.9f).coerceIn(0.05f, 0.9f)
            val col = when {
                energy > 0.75f -> T.red.copy(alpha = alpha)
                energy > 0.45f -> T.amber.copy(alpha = alpha)
                energy > 0.2f  -> T.green.copy(alpha = alpha)
                else           -> T.green.copy(alpha = alpha * 0.4f)
            }
            drawLine(col, Offset(cx + r0 * cos, cy + r0 * sin),
                Offset(cx + r1 * cos, cy + r1 * sin), 1.5f, cap = StrokeCap.Round)
        }

        // ── Oscilloscope waveform across the middle ───────────────────────────
        val path = Path()
        val pts  = 64
        var first = true
        for (k in 0..pts) {
            val t      = k.toFloat() / pts
            val x      = t * W
            val bIdx   = (t * (bands.size - 1)).toInt().coerceIn(0, bands.size - 1)
            val energy = bands[bIdx]
            // Multi-harmonic wave
            val wave = energy * H * 0.18f *
                (sin(t * 6f * PI.toFloat() + time) * 0.6f +
                 sin(t * 14f * PI.toFloat() - time2) * 0.25f +
                 sin(t * 2f * PI.toFloat() + time * 0.5f) * 0.15f)
            val y = cy + wave
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
        }
        val waveAlpha = (0.3f + mid * 0.7f).coerceIn(0f, 1f)
        drawPath(path, T.cyan.copy(alpha = waveAlpha),
            style = Stroke(width = 1.5f, join = StrokeJoin.Round, cap = StrokeCap.Round))

        // ── Central energy circle ─────────────────────────────────────────────
        val pulse = minOf(W, H) * (0.03f + bass * 0.06f)
        drawCircle(T.green.copy(alpha = bass * 0.6f), pulse, Offset(cx, cy))
        drawCircle(Color.Transparent, pulse, Offset(cx, cy),
            style = Stroke(1.5f))
        drawCircle(T.green.copy(0.8f), 2f, Offset(cx, cy))
    }
}

private fun DrawScope.drawMilkDropRings(
    cx: Float, cy: Float, maxR: Float,
    time: Float, bass: Float, mid: Float, treble: Float
) {
    val rings = 5
    for (r in 0 until rings) {
        val frac    = (r + 1).toFloat() / rings
        val phase   = time + r * 0.4f
        val wobble  = sin(phase) * mid * 8f
        val radius  = maxR * frac + wobble
        val alpha   = ((1f - frac) * 0.4f + bass * 0.3f).coerceIn(0f, 0.7f)
        val col = when {
            r == 0 -> T.green
            r == 1 -> T.cyan
            r == 2 -> T.amber
            r == 3 -> T.magenta
            else   -> T.red
        }
        // Draw as polygon for the classic MilkDrop angular look
        val sides = 6 + r * 2
        val path  = Path()
        for (s in 0..sides) {
            val angle = (s.toFloat() / sides) * 2f * PI.toFloat() + phase * 0.3f
            val rx    = radius * (1f + treble * 0.15f * cos(angle * 3f + time))
            val ry    = radius * (1f + treble * 0.15f * sin(angle * 2f - time))
            val px    = cx + rx * cos(angle)
            val py    = cy + ry * sin(angle)
            if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        drawPath(path, col.copy(alpha = alpha),
            style = Stroke(width = 1.2f, join = StrokeJoin.Round))
    }
}

// ── Progress bar ──────────────────────────────────────────────────────────────
@Composable
private fun ProgressRow(posMs: Long, durMs: Long) {
    val frac = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("[", color = T.dimmer, fontSize = 12.sp, fontFamily = CONSOLAS)
            Box(Modifier.weight(1f).height(10.dp).background(T.surface).border(1.dp, T.border)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac)
                    .background(Brush.horizontalGradient(listOf(T.greenDim, T.green))))
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac)
                    .background(Brush.verticalGradient(
                        listOf(Color.White.copy(0.08f), Color.Transparent))))
            }
            Text("]", color = T.dimmer, fontSize = 12.sp, fontFamily = CONSOLAS)
            Text("%3d%%".format((frac * 100).toInt()), color = T.green,
                fontSize = 11.sp, fontFamily = CONSOLAS, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Transport ─────────────────────────────────────────────────────────────────
@Composable
private fun TransportRow(state: PlaybackState, vm: PlayerViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$ transport", color = T.dimmer, fontSize = 9.sp, fontFamily = CONSOLAS)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CliBtn("|<<", onClick = vm::skipPrevious)
            CliBtn("<<")
            CliBtn(if (state.isPlaying) "|| PAUSE" else "> PLAY",
                primary = true, onClick = vm::playPause)
            CliBtn(">>")
            CliBtn(">>|", onClick = vm::skipNext)
            Spacer(Modifier.width(8.dp))
            CliBtn("SHF", active = state.shuffle, onClick = vm::toggleShuffle)
            CliBtn(
                when (state.repeatMode) {
                    PlayerRepeatMode.OFF -> "REP:0"
                    PlayerRepeatMode.ALL -> "REP:A"
                    PlayerRepeatMode.ONE -> "REP:1"
                },
                active = state.repeatMode != PlayerRepeatMode.OFF,
                onClick = vm::cycleRepeat
            )
        }
    }
}

@Composable
private fun CliBtn(
    label: String,
    primary: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit = {}
) {
    val fg = when { primary -> T.bg;     active -> T.green; else -> T.dim }
    val bg = when { primary -> T.green;  active -> T.greenDim; else -> T.surface }
    val bc = when { primary -> T.green;  active -> T.green; else -> T.border }
    Box(
        Modifier.background(bg).border(1.dp, bc).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 11.sp, fontFamily = CONSOLAS,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal)
    }
}

// ── Queue ─────────────────────────────────────────────────────────────────────
@Composable
private fun QueuePanel(vm: PlayerViewModel) {
    val playback by vm.playback.collectAsState()
    val queue    by vm.queue.collectAsState()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("$ queue", color = T.dimmer, fontSize = 9.sp, fontFamily = CONSOLAS)
            Text("${queue.size} tracks", color = T.green, fontSize = 9.sp, fontFamily = CONSOLAS)
        }
        Spacer(Modifier.height(2.dp))
        queue.forEachIndexed { i, track ->
            val active = track.id == playback.track.id
            Row(
                Modifier.fillMaxWidth()
                    .then(if (active) Modifier.background(T.surface) else Modifier)
                    .then(if (active) Modifier.drawBehind {
                        drawRect(T.green, size = Size(2.dp.toPx(), size.height))
                    } else Modifier)
                    .clickable { vm.playTrack(track) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (active) ">" else "%2d".format(i + 1),
                    color = if (active) T.green else T.dimmer,
                    fontSize = 10.sp, fontFamily = CONSOLAS,
                    modifier = Modifier.width(18.dp))
                Text(
                    track.title.ifEmpty { track.fileName ?: "unknown" },
                    color = if (active) T.green else T.white,
                    fontSize = 11.sp, fontFamily = CONSOLAS,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(track.durationFormatted, color = T.dim,
                    fontSize = 10.sp, fontFamily = CONSOLAS)
                Text(if (track.isLocal) "L" else "S",
                    color = if (track.isLocal) T.green else T.amber,
                    fontSize = 9.sp, fontFamily = CONSOLAS)
            }
        }
        if (queue.isEmpty()) {
            Text("  (empty — tap SCAN LOCAL MEDIA or connect Spotify)",
                color = T.dimmer, fontSize = 10.sp, fontFamily = CONSOLAS)
        }
    }
}

// ── Source ────────────────────────────────────────────────────────────────────
@Composable
private fun SourcePanel(vm: PlayerViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$ source", color = T.dimmer, fontSize = 9.sp, fontFamily = CONSOLAS)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CliBtn("SCAN LOCAL MEDIA", primary = true, onClick = vm::scanLocalMedia)
            CliBtn("CLEAR QUEUE", onClick = vm::clearQueue)
        }
        Text("  scans /Music, /Downloads — mp3 flac ogg m4a wav",
            color = T.dimmer, fontSize = 9.sp, fontFamily = CONSOLAS)
    }
}

// ── Bottom ────────────────────────────────────────────────────────────────────
@Composable
private fun BottomBar() {
    Divider()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text("cubicauto://ready", color = T.dimmer, fontSize = 9.sp, fontFamily = CONSOLAS)
        Text("android auto | local media", color = T.dimmer, fontSize = 9.sp, fontFamily = CONSOLAS)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(T.border))
}

@Composable
private fun Tag(label: String, color: Color) {
    Box(Modifier.border(1.dp, color).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text(label, color = color, fontSize = 9.sp, fontFamily = CONSOLAS, letterSpacing = 1.sp)
    }
}
