package com.aman.streamify

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        player = exoPlayer
        session = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }

    companion object {
        fun mediaItem(track: Track): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setTitle(track.name)
                .setArtist(track.artist)
                .setAlbumTitle(track.provider)
                .setArtworkUri(android.net.Uri.parse(track.image))
                .build()

            return MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(track.play)
                .setMediaMetadata(metadata)
                .build()
        }
    }
}