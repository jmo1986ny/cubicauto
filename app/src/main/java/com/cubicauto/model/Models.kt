package com.cubicauto.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val isPodcast: Boolean = false
) {
    val durationFormatted: String get() {
        val totalSec = durationMs / 1000
        return "%02d:%02d".format(totalSec / 60, totalSec % 60)
    }
    companion object {
        val EMPTY = Track("", "NO TRACK", "──────────", "", 0L)
    }
}

data class PlaybackState(
    val track: Track = Track.EMPTY,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volume: Float = 1f
)

enum class RepeatMode { OFF, ONE, ALL }

sealed class SpotifyConnectionState {
    object Disconnected : SpotifyConnectionState()
    object Connecting   : SpotifyConnectionState()
    object Connected    : SpotifyConnectionState()
    data class Error(val message: String) : SpotifyConnectionState()
}
