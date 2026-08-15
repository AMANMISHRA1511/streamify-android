package com.aman.streamify

import android.app.PendingIntent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val exo = ExoPlayer.Builder(this)
            .build()
            .apply {

                setAudioAttributes(
                    AudioAttributes.DEFAULT,
                    true
                )

                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_NETWORK)
                repeatMode = Player.REPEAT_MODE_OFF

                addListener(
                    object : Player.Listener {

                        override fun onPlayerError(
                            error: PlaybackException
                        ) {

                            if (hasNextMediaItem()) {
                                seekToNextMediaItem()
                                prepare()
                                play()
                            }
                        }
                    }
                )
            }

        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        player = exo

        session =
            MediaSession.Builder(this, exo)
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

        fun mediaItem(t: Track): MediaItem {

            val metadata =
                MediaMetadata.Builder()
                    .setTitle(t.name)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .apply {
                        if (t.image.isNotBlank()) {
                            setArtworkUri(
                                android.net.Uri.parse(t.image)
                            )
                        }
                    }
                    .build()

            return MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(t.play)
                .setMediaMetadata(metadata)
                .build()
        }
    }
}
