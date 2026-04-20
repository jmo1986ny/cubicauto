package com.cubicauto.model

import android.net.Uri

enum class TrackSource { LOCAL, SPOTIFY }

data class Track(
    val id:          String,
    val title:       String,
    val artist:      String,
    val album:       String,
    val durationMs:  Long,
    val isLocal:     Boolean   = false,
    val localUri:    Uri?      = null,
    val fileName:    String?   = null,
    val isPodcast:   Boolean   = false
) {
    val durationFormatted: String get() {
        val s = durationMs / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }
    companion object {
        val EMPTY = Track("", "", "", "", 0L)
    }
}

data class PlaybackState(
    val track:      Track       = Track.EMPTY,
    val positionMs: Long        = 0L,
    val isPlaying:  Boolean     = false,
    val shuffle:    Boolean     = false,
    val repeatMode: RepeatMode  = RepeatMode.OFF,
    val volume:     Float       = 1f,
    val source:     TrackSource = TrackSource.LOCAL
) {
    val durationMs: Long get() = track.durationMs
}

enum class RepeatMode { OFF, ONE, ALL }

sealed class SpotifyConnectionState {
    object Disconnected : SpotifyConnectionState()
    object Connecting   : SpotifyConnectionState()
    object Connected    : SpotifyConnectionState()
    data class Error(val message: String) : SpotifyConnectionState()
}
