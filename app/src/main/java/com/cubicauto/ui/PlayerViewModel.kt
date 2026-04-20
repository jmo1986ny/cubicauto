package com.cubicauto.ui

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubicauto.model.PlaybackState
import com.cubicauto.model.RepeatMode
import com.cubicauto.model.SpotifyConnectionState
import com.cubicauto.model.Track
import com.cubicauto.model.TrackSource
import com.cubicauto.spotify.SpotifyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel : ViewModel() {

    private val _playback    = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private val _connection  = MutableStateFlow<SpotifyConnectionState>(SpotifyConnectionState.Disconnected)
    val connection: StateFlow<SpotifyConnectionState> = _connection.asStateFlow()

    private val _spectrum    = MutableStateFlow(FloatArray(32) { 0f })
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()

    private val _positionMs  = MutableStateFlow(0L)
    val displayPositionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _queue       = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private var localPlayer: android.media.MediaPlayer? = null
    private var queueIndex  = 0

    init {
        SpotifyRepository.playbackState.onEach { state ->
            if (_playback.value.source == TrackSource.SPOTIFY) {
                _playback.value   = state
                _positionMs.value = state.positionMs
            }
        }.launchIn(viewModelScope)

        SpotifyRepository.connectionState
            .onEach { _connection.value = it }
            .launchIn(viewModelScope)

        // Tick playhead
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                val pb = _playback.value
                if (pb.isPlaying) {
                    if (pb.source == TrackSource.LOCAL) {
                        val pos = localPlayer?.currentPosition?.toLong() ?: 0L
                        _positionMs.value = pos
                        _playback.value = pb.copy(positionMs = pos)
                    } else {
                        _positionMs.value = minOf(_positionMs.value + 500L, pb.track.durationMs)
                    }
                }
            }
        }

        // Spectrum animation
        viewModelScope.launch {
            val rng = java.util.Random()
            while (isActive) {
                delay(80)
                if (_playback.value.isPlaying) {
                    _spectrum.value = FloatArray(32) { i ->
                        val center = 16f
                        val dist   = Math.abs(i - center) / center
                        val base   = 1f - dist * 0.55f
                        (base * (0.2f + rng.nextFloat() * 0.8f)).coerceIn(0.03f, 1f)
                    }
                } else {
                    _spectrum.value = FloatArray(32) { i -> (_spectrum.value[i] * 0.88f) }
                }
            }
        }
    }

    // ── Connection ────────────────────────────────────────────────────────────

    fun connect(context: Context) = SpotifyRepository.connect(context)

    // ── Transport ─────────────────────────────────────────────────────────────

    fun playPause() {
        val pb = _playback.value
        if (pb.source == TrackSource.LOCAL) {
            val mp = localPlayer ?: return
            if (pb.isPlaying) {
                mp.pause()
                _playback.value = pb.copy(isPlaying = false)
            } else {
                mp.start()
                _playback.value = pb.copy(isPlaying = true)
            }
        } else {
            if (pb.isPlaying) SpotifyRepository.pause() else SpotifyRepository.resume()
        }
    }

    fun skipNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        queueIndex = (queueIndex + 1) % q.size
        playTrack(q[queueIndex])
    }

    fun skipPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return
        queueIndex = (queueIndex - 1 + q.size) % q.size
        playTrack(q[queueIndex])
    }

    fun seekTo(fraction: Float) {
        val pb = _playback.value
        val ms = (fraction * pb.track.durationMs).toLong().coerceAtLeast(0L)
        if (pb.source == TrackSource.LOCAL) {
            localPlayer?.seekTo(ms.toInt())
        } else {
            SpotifyRepository.seekTo(ms)
        }
        _positionMs.value = ms
    }

    fun toggleShuffle() {
        val newShuffle = !_playback.value.shuffle
        _playback.value = _playback.value.copy(shuffle = newShuffle)
        // Also update Spotify side when connected
        if (_playback.value.source == TrackSource.SPOTIFY) {
            SpotifyRepository.setShuffle(newShuffle)
        }
        // Reshuffle or restore queue order
        val q = _queue.value
        if (q.isNotEmpty()) {
            if (newShuffle) {
                val current = q[queueIndex]
                val rest    = (q - current).shuffled()
                _queue.value = listOf(current) + rest
                queueIndex   = 0
            } else {
                // Restore MediaStore order (re-sort by title)
                _queue.value = q.sortedBy { it.title.lowercase() }
                queueIndex   = _queue.value.indexOfFirst {
                    it.id == _playback.value.track.id
                }.coerceAtLeast(0)
            }
        }
    }

    fun cycleRepeat() {
        val next = when (_playback.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        SpotifyRepository.setRepeat(next)
        _playback.value = _playback.value.copy(repeatMode = next)
    }

    // ── Local media ───────────────────────────────────────────────────────────

    fun playTrack(track: Track) {
        if (track.isLocal && track.localUri != null) {
            playLocalTrack(track)
        } else {
            SpotifyRepository.play(track.id)
            _playback.value = _playback.value.copy(
                track = track, isPlaying = true, source = TrackSource.SPOTIFY)
        }
        queueIndex = _queue.value.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
    }

    private fun playLocalTrack(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                localPlayer?.release()
                localPlayer = null

                val mp = android.media.MediaPlayer().also { localPlayer = it }

                // Required on Android 12+ — without this, prepare() silently fails
                mp.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                // Use context-aware setDataSource so content:// URIs resolve correctly
                mp.setDataSource(appContext!!, track.localUri!!)
                mp.prepare()   // synchronous — fine on IO dispatcher

                val durationMs = mp.duration.toLong()

                mp.setOnCompletionListener {
                    val pb = _playback.value
                    when (pb.repeatMode) {
                        RepeatMode.ONE -> { it.seekTo(0); it.start() }
                        RepeatMode.ALL, RepeatMode.OFF -> skipNext()
                    }
                }

                withContext(Dispatchers.Main) {
                    mp.start()
                    _playback.value = _playback.value.copy(
                        track      = track.copy(durationMs = durationMs),
                        isPlaying  = true,
                        source     = TrackSource.LOCAL,
                        positionMs = 0L
                    )
                    Log.d("PlayerVM", "Playing: ${track.title} (${durationMs}ms)")
                }
            } catch (e: Exception) {
                Log.e("PlayerVM", "Failed to play: ${track.title} uri=${track.localUri}", e)
                withContext(Dispatchers.Main) {
                    _playback.value = _playback.value.copy(isPlaying = false)
                }
            }
        }
    }

    fun scanLocalMedia(context: Context) {
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                queryLocalMedia(context)
            }
            _queue.value = tracks
            Log.d("PlayerVM", "Scanned ${tracks.size} local tracks")
        }
    }

    // Public no-arg version called from UI lambda (context not available there)
    // Context is injected by the composable via a side-effect — see MainActivity
    private var appContext: Context? = null
    fun setContext(ctx: Context) { appContext = ctx.applicationContext }
    fun scanLocalMedia() { appContext?.let { scanLocalMedia(it) } }
    fun clearQueue() {
        _queue.value = emptyList()
        localPlayer?.release()
        localPlayer = null
        _playback.value = PlaybackState()
        _positionMs.value = 0L
    }

    private fun queryLocalMedia(context: Context): List<Track> {
        val tracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.IS_MUSIC
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val fileCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id  = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()
                )
                tracks.add(Track(
                    id         = id.toString(),
                    title      = cursor.getString(titleCol) ?: "",
                    artist     = cursor.getString(artistCol) ?: "Unknown Artist",
                    album      = cursor.getString(albumCol) ?: "Unknown Album",
                    durationMs = cursor.getLong(durCol),
                    isLocal    = true,
                    localUri   = uri,
                    fileName   = cursor.getString(fileCol)
                ))
            }
        }
        return tracks
    }

    override fun onCleared() {
        super.onCleared()
        localPlayer?.release()
        localPlayer = null
        SpotifyRepository.disconnect()
    }
}
