package com.cubicauto.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.cubicauto.CubicAutoApplication
import com.cubicauto.MainActivity
import com.cubicauto.model.PlaybackState
import com.cubicauto.model.SpotifyConnectionState
import com.cubicauto.model.Track
import com.cubicauto.spotify.SpotifyRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * CubicMediaService — MediaBrowserService
 *
 * This is the Android Auto integration point. AA binds here, pulls session
 * token, subscribes to MediaSession, and renders its own car-safe UI.
 *
 * Flow:
 *  AA binds → onGetRoot() → onLoadChildren() populates browse tree
 *  SpotifyRepository emits PlaybackState → updateMediaSession() pushes to AA
 *  AA sends transport commands → MediaSessionCallback → SpotifyRepository
 */
class CubicMediaService : MediaBrowserServiceCompat() {

    companion object {
        private const val TAG          = "CubicMediaSvc"
        private const val ROOT_ID      = "ROOT"
        private const val RECENT_ID    = "RECENT"
        const val ACTION_PLAY          = "com.cubicauto.PLAY"
        const val ACTION_PAUSE         = "com.cubicauto.PAUSE"
        const val ACTION_SKIP_NEXT     = "com.cubicauto.SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "com.cubicauto.SKIP_PREVIOUS"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        val activityPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSessionCompat(this, TAG).apply {
            setSessionActivity(activityPi)
            setCallback(SessionCallback())
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            stateBuilder = PlaybackStateCompat.Builder().setActions(
                PlaybackStateCompat.ACTION_PLAY             or
                PlaybackStateCompat.ACTION_PAUSE            or
                PlaybackStateCompat.ACTION_PLAY_PAUSE       or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT     or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO          or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
            )
            setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_NONE, 0, 1f).build())
            isActive = true
        }

        sessionToken = session.sessionToken

        SpotifyRepository.connect(applicationContext)

        SpotifyRepository.playbackState
            .onEach { pushToSession(it) }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(session, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        session.release()
        SpotifyRepository.disconnect()
    }

    // ── Browse ────────────────────────────────────────────────────────────────

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        Log.d(TAG, "onGetRoot: $clientPackageName")
        val extras = Bundle().apply {
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT",  1)
        }
        return BrowserRoot(ROOT_ID, extras)
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaItem>>) {
        result.sendResult(when (parentId) {
            ROOT_ID -> listOf(browsable(RECENT_ID, "Recently Played", "From Spotify"))
            else    -> emptyList()
        })
    }

    // ── Push state to MediaSession (Android Auto reads this) ──────────────────

    private fun pushToSession(state: PlaybackState) {
        val pbState = if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING
                      else                 PlaybackStateCompat.STATE_PAUSED
        session.setPlaybackState(
            stateBuilder.setState(pbState, state.positionMs, 1f).build()
        )
        if (state.track != Track.EMPTY) {
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID,  state.track.id)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE,     state.track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,    state.track.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,     state.track.album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,    state.track.durationMs)
                    .build()
            )
        }
    }

    private fun startFg() {
        startForeground(
            CubicAutoApplication.NOTIFICATION_ID,
            NotificationHelper.buildMediaNotification(
                this, session,
                CubicAutoApplication.MEDIA_CHANNEL_ID,
                CubicAutoApplication.NOTIFICATION_ID
            )
        )
    }

    // ── Session callbacks (commands from AA or lock screen) ───────────────────

    private inner class SessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay()             { SpotifyRepository.resume();                startFg() }
        override fun onPause()            { SpotifyRepository.pause() }
        override fun onSkipToNext()       { SpotifyRepository.skipNext() }
        override fun onSkipToPrevious()   { SpotifyRepository.skipPrevious() }
        override fun onSeekTo(pos: Long)  { SpotifyRepository.seekTo(pos) }
        override fun onPlayFromMediaId(id: String?, extras: Bundle?) {
            id?.let { SpotifyRepository.play(it) }; startFg()
        }
        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            SpotifyRepository.resume(); startFg()
        }
        override fun onSetShuffleMode(mode: Int) {
            SpotifyRepository.setShuffle(mode != PlaybackStateCompat.SHUFFLE_MODE_NONE)
        }
    }

    private fun browsable(id: String, title: String, subtitle: String): MediaItem =
        MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(id).setTitle(title).setSubtitle(subtitle).build(),
            MediaItem.FLAG_BROWSABLE
        )
}
