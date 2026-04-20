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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicauto.model.PlaybackState
import com.cubicauto.model.RepeatMode as PlayerRepeatMode
import com.cubicauto.model.SpotifyConnectionState
import com.cubicauto.model.Track

// ── CLI terminal palette ──────────────────────────────────────────────────────
private object T {
    val bg      = Color(0xFF000000)
    val surface = Color(0xFF0A0A0A)
    val border  = Color(0xFF222222)
    val white   = Color(0xFFEEEEEE)
    val dim     = Color(0xFF666666)
    val dimmer  = Color(0xFF333333)
    val green   = Color(0xFF00FF41)   // matrix green — primary informative
    val greenDim= Color(0xFF006616)
    val amber   = Color(0xFFFFB000)   // warnings / active state
    val red     = Color(0xFFFF3333)   // errors / stop
    val cursor  = Color(0xFF00FF41)
}

private val MONO = FontFamily.Monospace

@Composable
fun CubicPlayerScreen(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val playback   by viewModel.playback.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val spectrum   by viewModel.spectrum.collectAsState()
    val posMs      by viewModel.displayPositionMs.collectAsState()

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
            SpectrumRow(spectrum, playback.isPlaying)
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
    val inf = rememberInfiniteTransition(label = "cursor")
    val cur by inf.animateFloat(1f, 0f,
        infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), "c")

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("CUBICAUTO", color = T.green, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, fontFamily = MONO, letterSpacing = 3.sp)
            Text("v1.0", color = T.dim, fontSize = 10.sp, fontFamily = MONO)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val srcColor = if (playback.source == TrackSource.LOCAL) T.green else T.amber
            val srcLabel = if (playback.source == TrackSource.LOCAL) "LOCAL" else "SPOTIFY"
            Tag(srcLabel, srcColor)
            val connColor = if (connection is SpotifyConnectionState.Connected) T.green else T.dim
            Tag(if (connection is SpotifyConnectionState.Connected) "CONN" else "DISC", connColor)
        }
        // Blinking cursor
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

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // Prompt line
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(">>", color = T.green, fontSize = 11.sp, fontFamily = MONO)
            Text("NOW PLAYING", color = T.dim, fontSize = 10.sp, fontFamily = MONO,
                letterSpacing = 2.sp)
        }
        // Track title — big, white
        Text(
            track.title.uppercase().ifEmpty { "NO TRACK LOADED" },
            color = T.white, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            fontFamily = MONO, maxLines = 1, overflow = TextOverflow.Ellipsis,
            letterSpacing = 1.sp
        )
        // Artist / album
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(track.artist.uppercase().ifEmpty { "UNKNOWN" },
                color = T.green, fontSize = 11.sp, fontFamily = MONO)
            Text("/", color = T.dimmer, fontSize = 11.sp, fontFamily = MONO)
            Text(track.album.uppercase().ifEmpty { "UNKNOWN ALBUM" },
                color = T.dim, fontSize = 11.sp, fontFamily = MONO,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Time
        val s   = posMs / 1000
        val dur = track.durationMs / 1000
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("[", color = T.dimmer, fontSize = 13.sp, fontFamily = MONO)
            Text("%02d".format(s / 60), color = T.green, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, fontFamily = MONO)
            Text(":", color = T.green.copy(alpha = if (isPlaying) colon else 1f),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
            Text("%02d".format(s % 60), color = T.green, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, fontFamily = MONO)
            Text("/", color = T.dimmer, fontSize = 13.sp, fontFamily = MONO)
            Text("%02d:%02d".format(dur / 60, dur % 60),
                color = T.dim, fontSize = 13.sp, fontFamily = MONO)
            Text("]", color = T.dimmer, fontSize = 13.sp, fontFamily = MONO)
            Spacer(Modifier.width(8.dp))
            Text(if (isPlaying) "▶ PLAYING" else "■ PAUSED",
                color = if (isPlaying) T.green else T.dim,
                fontSize = 10.sp, fontFamily = MONO, letterSpacing = 2.sp)
        }
    }
}

// ── Spectrum ──────────────────────────────────────────────────────────────────
@Composable
private fun SpectrumRow(bands: FloatArray, isPlaying: Boolean) {
    val peakH = remember { FloatArray(32) { 0f } }
    val peakV = remember { FloatArray(32) { 0f } }

    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("SPECTRUM", color = T.dimmer, fontSize = 8.sp, fontFamily = MONO,
            letterSpacing = 2.sp, modifier = Modifier.padding(end = 4.dp))
        Canvas(Modifier.weight(1f).height(40.dp)) {
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
                drawRect(col.copy(alpha = 0.85f), Offset(x, H - h), Size(barW, h))
                if (isPlaying) {
                    if (h > peakH[i]) { peakH[i] = h; peakV[i] = 0f }
                    else { peakV[i] += 0.3f; peakH[i] = (peakH[i] - peakV[i]).coerceAtLeast(0f) }
                } else { peakH[i] *= 0.9f }
                drawRect(T.green, Offset(x, H - peakH[i] - 1f), Size(barW, 1f))
            }
        }
    }
}

// ── Progress bar ──────────────────────────────────────────────────────────────
@Composable
private fun ProgressRow(posMs: Long, durMs: Long) {
    val frac = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
    val pct  = (frac * 100).toInt()

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // ASCII progress bar
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("[", color = T.dimmer, fontSize = 12.sp, fontFamily = MONO)
            Box(Modifier.weight(1f).height(10.dp)
                .background(T.surface)
                .border(1.dp, T.border)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac)
                    .background(Brush.horizontalGradient(listOf(T.greenDim, T.green))))
                // Scanline effect
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac)
                    .background(Brush.verticalGradient(
                        listOf(Color.White.copy(0.08f), Color.Transparent))))
            }
            Text("]", color = T.dimmer, fontSize = 12.sp, fontFamily = MONO)
            Text("%3d%%".format(pct), color = T.green, fontSize = 11.sp,
                fontFamily = MONO, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Transport controls ────────────────────────────────────────────────────────
@Composable
private fun TransportRow(state: PlaybackState, vm: PlayerViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Command prompt style label
        Text("$ transport", color = T.dimmer, fontSize = 9.sp, fontFamily = MONO)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CliBtn("|<<", onClick = vm::skipPrevious)
            CliBtn("<<",  onClick = {})
            CliBtn(
                if (state.isPlaying) "|| PAUSE" else "> PLAY",
                primary = true,
                onClick = vm::playPause
            )
            CliBtn(">>",  onClick = {})
            CliBtn(">>|", onClick = vm::skipNext)
            Spacer(Modifier.width(8.dp))
            CliBtn(
                "SHF",
                active = state.shuffle,
                onClick = vm::toggleShuffle
            )
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
    val fg = when {
        primary -> T.bg
        active  -> T.green
        else    -> T.dim
    }
    val bg = when {
        primary -> T.green
        active  -> T.greenDim
        else    -> T.surface
    }
    val border = when {
        primary -> T.green
        active  -> T.green
        else    -> T.border
    }
    Box(
        Modifier
            .background(bg)
            .border(1.dp, border)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 11.sp,
            fontFamily = MONO, fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal)
    }
}

// ── Queue panel ───────────────────────────────────────────────────────────────
@Composable
private fun QueuePanel(vm: PlayerViewModel) {
    val playback by vm.playback.collectAsState()
    val queue    by vm.queue.collectAsState()

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("$ queue", color = T.dimmer, fontSize = 9.sp, fontFamily = MONO)
            Text("${queue.size} tracks", color = T.green, fontSize = 9.sp, fontFamily = MONO)
        }
        Spacer(Modifier.height(2.dp))
        queue.forEachIndexed { i, track ->
            val active = track.id == playback.track.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (active) Modifier.background(T.surface) else Modifier)
                    .then(if (active) Modifier.drawBehind {
                        drawRect(T.green, size = Size(2.dp.toPx(), size.height))
                    } else Modifier)
                    .clickable { vm.playTrack(track) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (active) ">" else "%2d".format(i + 1),
                    color = if (active) T.green else T.dimmer,
                    fontSize = 10.sp, fontFamily = MONO,
                    modifier = Modifier.width(18.dp)
                )
                Text(
                    track.title.ifEmpty { track.fileName ?: "unknown" },
                    color = if (active) T.green else T.white,
                    fontSize = 11.sp, fontFamily = MONO,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    track.durationFormatted,
                    color = T.dim, fontSize = 10.sp, fontFamily = MONO
                )
                val srcMark = if (track.isLocal) "L" else "S"
                Text(srcMark, color = if (track.isLocal) T.green else T.amber,
                    fontSize = 9.sp, fontFamily = MONO)
            }
        }
        if (queue.isEmpty()) {
            Text("  (empty — load local files or connect Spotify)",
                color = T.dimmer, fontSize = 10.sp, fontFamily = MONO)
        }
    }
}

// ── Source panel ──────────────────────────────────────────────────────────────
@Composable
private fun SourcePanel(vm: PlayerViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$ source", color = T.dimmer, fontSize = 9.sp, fontFamily = MONO)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CliBtn("SCAN LOCAL MEDIA", primary = true, onClick = vm::scanLocalMedia)
            CliBtn("CLEAR QUEUE", onClick = vm::clearQueue)
        }
        Text(
            "  scans /Music, /Downloads — mp3 flac ogg m4a wav",
            color = T.dimmer, fontSize = 9.sp, fontFamily = MONO
        )
    }
}

// ── Bottom bar ────────────────────────────────────────────────────────────────
@Composable
private fun BottomBar() {
    Divider()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text("cubicauto://ready", color = T.dimmer, fontSize = 9.sp, fontFamily = MONO)
        Text("android auto | local media", color = T.dimmer, fontSize = 9.sp, fontFamily = MONO)
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
        Text(label, color = color, fontSize = 9.sp, fontFamily = MONO, letterSpacing = 1.sp)
    }
}
