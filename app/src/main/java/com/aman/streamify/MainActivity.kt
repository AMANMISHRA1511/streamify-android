package com.aman.streamify

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var queue: List<Track> = emptyList()

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        connectController()

        val searchBox = findViewById<android.widget.EditText>(R.id.searchBox)
        val searchButton = findViewById<Button>(R.id.searchButton)
        val status = findViewById<TextView>(R.id.status)
        val results = findViewById<LinearLayout>(R.id.results)

        searchButton.setOnClickListener {
            val q = searchBox.text.toString().trim()
            if (q.isEmpty()) return@setOnClickListener
            status.text = "Searching…"
            lifecycleScope.launch {
                queue = StreamifyApi.search(q)
                results.removeAllViews()
                status.text = if (queue.isEmpty()) "No songs found." else "${queue.size} songs"
                queue.forEachIndexed { index, track ->
                    val row = Button(this@MainActivity).apply {
                        text = "${track.name}\n${track.artist} · ${track.provider}"
                        textAlignment = Button.TEXT_ALIGNMENT_TEXT_START
                        isAllCaps = false
                        setOnClickListener { playQueue(index) }
                    }
                    results.addView(
                        row,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
            }
        }

        findViewById<Button>(R.id.playButton).setOnClickListener {
            controller?.let { c -> if (c.isPlaying) c.pause() else c.play() }
        }
        findViewById<Button>(R.id.prevButton).setOnClickListener {
            controller?.seekToPreviousMediaItem()
        }
        findViewById<Button>(R.id.nextButton).setOnClickListener {
            controller?.seekToNextMediaItem()
        }
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun playQueue(startIndex: Int) {
        val c = controller ?: return
        val mediaItems = queue.map(PlaybackService::mediaItem)
        c.setMediaItems(mediaItems, startIndex, 0L)
        c.prepare()
        c.play()
    }

    override fun onDestroy() {
        controllerFuture?.let(MediaController::releaseFuture)
        controller = null
        super.onDestroy()
    }
}