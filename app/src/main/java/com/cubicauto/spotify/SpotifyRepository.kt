package com.cubicauto.spotify

import android.content.Context
import android.util.Log
import com.cubicauto.model.PlaybackState
import com.cubicauto.model.RepeatMode
import com.cubicauto.model.SpotifyConnectionState
import com.cubicauto.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SpotifyRepository — stub mode for demo/CI builds.
 *
 * This version has NO dependency on the Spotify SDK .aar so the project
 * compiles and runs on any device showing the full Cubic UI.
 *
 * To wire up real Spotify playback:
 *  1. Drop spotify-app-remote-release-0.8.0.aar into app/libs/
 *  2. Add imports for com.spotify.android.appremote.api.*
 *  3. Uncomment the blocks marked UNCOMMENT FOR REAL SDK
 *  4. Add your Client ID to BuildConfig in app/build.gradle.kts
 *  5. Register your debug SHA-1 at developer.spotify.com/dashboard
 */
object SpotifyRepository {

    private const val TAG = "SpotifyRepo"

    private val _connection = MutableStateFlow<SpotifyConnectionState>(
        SpotifyConnectionState.Disconnected
    )
    val connectionState: StateFlow<SpotifyConnectionState> = _connection.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playback.asStateFlow()

    // ── Demo queue loaded on connect ──────────────────────────────────────────
    private val DEMO_TRACKS = listOf(
        Track("1", "Nothing Else Matters", "Metallica", "Metallica (Black Album)", 388_000),
        Track("2", "Enter Sandman",        "Metallica", "Metallica (Black Album)", 330_000),
        Track("3", "Fade To Black",        "Metallica", "Ride The Lightning",      417_000),
        Track("4", "Master Of Puppets",    "Metallica", "Master Of Puppets",       516_000),
        Track("5", "One",                  "Metallica", "...And Justice For All",  445_000),
    )
    private var currentIndex = 0

    fun connect(context: Context) {
        if (_connection.value is SpotifyConnectionState.Connected) return
        _connection.value = SpotifyConnectionState.Connecting
        Log.d(TAG, "Stub: simulating Spotify connect")

        // Simulate a 800ms connection delay then go connected
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            _connection.value = SpotifyConnectionState.Connected
            _playback.value = PlaybackState(
                track     = DEMO_TRACKS[currentIndex],
                isPlaying = true,
                positionMs = 0L
            )
            Log.d(TAG, "Stub: connected, playing ${DEMO_TRACKS[currentIndex].title}")
        }, 800)

        /* UNCOMMENT FOR REAL SDK ─────────────────────────────────────────────
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                appRemote = remote
                _connection.value = SpotifyConnectionState.Connected
                subscribeToPlayerState(remote)
            }
            override fun onFailure(t: Throwable) {
                _connection.value = SpotifyConnectionState.Error(t.message ?: "Unknown")
            }
        })
        ──────────────────────────────────────────────────────────────────────*/
    }

    fun disconnect() {
        _connection.value = SpotifyConnectionState.Disconnected
        Log.d(TAG, "Disconnected")
    }

    // ── Playback controls (stub updates local state; SDK version calls playerApi) ──

    fun resume() {
        _playback.value = _playback.value.copy(isPlaying = true)
    }

    fun pause() {
        _playback.value = _playback.value.copy(isPlaying = false)
    }

    fun play(spotifyUri: String) {
        Log.d(TAG, "play($spotifyUri)")
        // Real: appRemote?.playerApi?.play(spotifyUri)
        _playback.value = _playback.value.copy(isPlaying = true)
    }

    fun skipNext() {
        currentIndex = (currentIndex + 1) % DEMO_TRACKS.size
        _playback.value = _playback.value.copy(
            track = DEMO_TRACKS[currentIndex], positionMs = 0L, isPlaying = true
        )
    }

    fun skipPrevious() {
        currentIndex = (currentIndex - 1 + DEMO_TRACKS.size) % DEMO_TRACKS.size
        _playback.value = _playback.value.copy(
            track = DEMO_TRACKS[currentIndex], positionMs = 0L, isPlaying = true
        )
    }

    fun seekTo(positionMs: Long) {
        _playback.value = _playback.value.copy(positionMs = positionMs)
    }

    fun setShuffle(enabled: Boolean) {
        _playback.value = _playback.value.copy(shuffle = enabled)
    }

    fun setRepeat(mode: RepeatMode) {
        _playback.value = _playback.value.copy(repeatMode = mode)
    }
}
