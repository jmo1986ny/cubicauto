package com.cubicauto.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.cubicauto.MainActivity
import com.cubicauto.R

object NotificationHelper {
    fun buildMediaNotification(
        context: android.content.Context,
        session: MediaSessionCompat,
        channelId: String,
        notificationId: Int
    ): Notification {
        val ctrl      = session.controller
        val meta      = ctrl.metadata
        val pbState   = ctrl.playbackState
        val isPlaying = pbState?.state == PlaybackStateCompat.STATE_PLAYING
        val title     = meta?.description?.title  ?: "CubicAuto"
        val artist    = meta?.description?.subtitle ?: "Spotify"

        val contentPi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = androidx.media.session.MediaButtonReceiver
            .buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_STOP)

        fun actionPi(action: String, req: Int) = PendingIntent.getBroadcast(
            context, req, Intent(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "Prev",
            actionPi(CubicMediaService.ACTION_SKIP_PREVIOUS, 0)
        )
        val playPause = NotificationCompat.Action(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            actionPi(if (isPlaying) CubicMediaService.ACTION_PAUSE else CubicMediaService.ACTION_PLAY, 1)
        )
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Next",
            actionPi(CubicMediaService.ACTION_SKIP_NEXT, 2)
        )

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(contentPi)
            .setDeleteIntent(stopPi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(prevAction)
            .addAction(playPause)
            .addAction(nextAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(stopPi)
            )
            .build()
    }
}
