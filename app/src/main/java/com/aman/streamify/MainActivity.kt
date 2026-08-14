package com.aman.streamify

import android.Manifest
import android.app.Dialog
import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private var currentQueue: List<Track> = emptyList()
    private var searchJob: Job? = null

    private lateinit var home: ScrollView
    private lateinit var search: LinearLayout
    private lateinit var library: ScrollView
    private lateinit var history: ScrollView

    private lateinit var homeContainer: LinearLayout
    private lateinit var searchResults: LinearLayout
    private lateinit var libraryContainer: LinearLayout
    private lateinit var historyContainer: LinearLayout

    private lateinit var searchBox: EditText
    private lateinit var searchStatus: TextView

    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniArtwork: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlay: TextView


    private val prefs by lazy {
        getSharedPreferences("streamify", Context.MODE_PRIVATE)
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val voiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()

            if (!text.isNullOrBlank()) {
                searchBox.setText(text)
                performSearch(text)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        requestNotificationPermission()
        connectController()
        configureNavigation()
        configureSearch()
        loadHome()
    }

    private fun bindViews() {
        home = findViewById(R.id.homeScreen)
        search = findViewById(R.id.searchScreen)
        library = findViewById(R.id.libraryScreen)
        history = findViewById(R.id.historyScreen)

        homeContainer = findViewById(R.id.homeContainer)
        searchResults = findViewById(R.id.searchResults)
        libraryContainer = findViewById(R.id.libraryContainer)
        historyContainer = findViewById(R.id.historyContainer)

        searchBox = findViewById(R.id.searchBox)
        searchStatus = findViewById(R.id.searchStatus)

        miniPlayer = findViewById(R.id.miniPlayer)
        miniArtwork = findViewById(R.id.miniArtwork)
        miniTitle = findViewById(R.id.miniTitle)
        miniArtist = findViewById(R.id.miniArtist)
        miniPlay = findViewById(R.id.playButton)

        findViewById<TextView>(R.id.prevButton).setOnClickListener {
            controller?.seekToPreviousMediaItem()
        }

        findViewById<TextView>(R.id.nextButton).setOnClickListener {
            controller?.seekToNextMediaItem()
        }

        miniPlay.setOnClickListener {
            controller?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        }

        miniPlayer.setOnClickListener {
            showNowPlaying()
        }
    }

    private fun configureNavigation() {
        val navHome = findViewById<TextView>(R.id.navHome)
        val navSearch = findViewById<TextView>(R.id.navSearch)
        val navLibrary = findViewById<TextView>(R.id.navLibrary)
        val navHistory = findViewById<TextView>(R.id.navHistory)

        val all = listOf(navHome, navSearch, navLibrary, navHistory)

        fun activate(view: TextView) {
            all.forEach { it.setTextColor(Color.parseColor("#8E9AA8")) }
            view.setTextColor(Color.parseColor("#E91DFF"))
        }

        navHome.setOnClickListener {
            showScreen(home)
            activate(navHome)
        }

        navSearch.setOnClickListener {
            showScreen(search)
            activate(navSearch)
        }

        navLibrary.setOnClickListener {
            renderLibrary()
            showScreen(library)
            activate(navLibrary)
        }

        navHistory.setOnClickListener {
            renderHistory()
            showScreen(history)
            activate(navHistory)
        }
    }

    private fun showScreen(target: View) {
        home.visibility = if (target === home) View.VISIBLE else View.GONE
        search.visibility = if (target === search) View.VISIBLE else View.GONE
        library.visibility = if (target === library) View.VISIBLE else View.GONE
        history.visibility = if (target === history) View.VISIBLE else View.GONE
    }

    private fun configureSearch() {
        searchBox.setOnEditorActionListener { _, _, _ ->
            performSearch(searchBox.text.toString())
            true
        }

        searchBox.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showScreen(search)
        }

        searchBox.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchJob?.cancel()
                    val q = s?.toString().orEmpty()

                    if (q.length >= 2) {
                        searchJob = lifecycleScope.launch {
                            delay(600)
                            performSearch(q)
                        }
                    }
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            }
        )

        findViewById<TextView>(R.id.voiceButton).setOnClickListener {
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search Streamify")
            }

            runCatching {
                voiceLauncher.launch(intent)
            }.onFailure {
                Toast.makeText(this, "Voice search unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadHome() {
        homeContainer.removeAllViews()

        homeContainer.addView(heroCard())

        val categories = listOf(
            Triple("Trending Hindi", "Arijit Singh Hindi", "jio"),
            Triple("Bollywood", "Bollywood hits", "jio"),
            Triple("Bhojpuri Hits", "Pawan Singh Bhojpuri", "jio"),
            Triple("Telugu / Tollywood", "Telugu Tollywood hits", "jio"),
            Triple("Punjabi Beats", "Diljit Punjabi", "jio"),
            Triple("Hollywood / English", "English hits", "jio"),
            Triple("Global Pop", "international English pop hits", "jio")
        )

        categories.forEach { (title, query, provider) ->
            lifecycleScope.launch {
                val songs = StreamifyApi.search(query, provider)

                if (songs.isNotEmpty()) {
                    addSection(title, songs)
                }
            }
        }
    }

    private fun heroCard(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))

            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#260936"),
                    Color.parseColor("#12101A"),
                    Color.parseColor("#0B0F14")
                )
            ).apply {
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), Color.parseColor("#652D80"))
            }
        }

        box.addView(TextView(this).apply {
            text = "Your music,\nyour mood."
            textSize = 38f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        box.addView(TextView(this).apply {
            text = "Hindi, Bollywood, Bhojpuri, Telugu, Punjabi, English and more."
            textSize = 16f
            setTextColor(Color.parseColor("#ADB7C2"))
            setPadding(0, dp(14), 0, dp(18))
        })

        box.addView(TextView(this).apply {
            text = "Search music  →"
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.parseColor("#FFFFFF"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(20), dp(13), dp(20), dp(13))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E91DFF"))
                cornerRadius = dp(30).toFloat()
            }

            setOnClickListener {
                showScreen(search)
                searchBox.requestFocus()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        return box
    }

    private fun addSection(title: String, tracks: List<Track>) {
        val titleView = TextView(this).apply {
            text = title
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(2), dp(26), 0, dp(12))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        tracks.take(6).forEachIndexed { index, track ->
            row.addView(songCard(track) {
                playQueue(tracks, index)
            })
        }

        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }

        runOnUiThread {
            homeContainer.addView(titleView)
            homeContainer.addView(horizontal)
        }
    }

    private fun songCard(track: Track, click: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(10))

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10151C"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#202A35"))
            }

            setOnClickListener { click() }
        }

        val artwork = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(track.image) {
                crossfade(false)
            }
        }

        card.addView(
            artwork,
            LinearLayout.LayoutParams(dp(132), dp(132))
        )

        card.addView(TextView(this).apply {
            text = track.name
            textSize = 16f
            maxLines = 1
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(9), 0, 0)
        })

        card.addView(TextView(this).apply {
            text = track.artist
            textSize = 13f
            maxLines = 1
            setTextColor(Color.parseColor("#929DAA"))
        })

        card.addView(TextView(this).apply {
            text = track.provider
            textSize = 11f
            setTextColor(Color.parseColor("#E91DFF"))
            setPadding(0, dp(5), 0, 0)
        })

        return card.apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(150),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(10)
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return

        searchStatus.text = "Searching…"
        searchResults.removeAllViews()

        lifecycleScope.launch {
            val tracks = StreamifyApi.search(query)

            searchStatus.text =
                if (tracks.isEmpty()) "No songs found"
                else "${tracks.size} results"

            searchResults.removeAllViews()

            tracks.forEachIndexed { index, track ->
                searchResults.addView(resultRow(track) {
                    saveSearch(query)
                    playQueue(tracks, index)
                })
            }
        }
    }

    private fun resultRow(track: Track, click: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10151C"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#202A35"))
            }

            setOnClickListener { click() }
        }

        val art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(track.image)
        }

        row.addView(art, LinearLayout.LayoutParams(dp(62), dp(62)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }

        info.addView(TextView(this).apply {
            text = track.name
            textSize = 16f
            maxLines = 1
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        info.addView(TextView(this).apply {
            text = "${track.artist} • ${track.provider}"
            textSize = 13f
            maxLines = 1
            setTextColor(Color.parseColor("#929DAA"))
        })

        row.addView(
            info,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        row.addView(TextView(this).apply {
            text = "▶"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E91DFF"))
            }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))

        return row.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun connectController() {
        val token = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(this, token).buildAsync()

        controllerFuture?.addListener({
            controller = runCatching { controllerFuture?.get() }.getOrNull()

            controller?.addListener(object : Player.Listener {

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    updateMiniPlayer()
                    saveHistory()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    miniPlay.text = if (isPlaying) "Ⅱ" else "▶"
}

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateMiniPlayer()
                }

                override fun onMediaMetadataChanged(
                    mediaMetadata: androidx.media3.common.MediaMetadata
                ) {
                    updateMiniPlayer()
                }
            })

            updateMiniPlayer()

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResume() {
        super.onResume()
updateMiniPlayer()
    }

    private fun playQueue(tracks: List<Track>, index: Int) {
        val c = controller ?: run {
            Toast.makeText(this, "Player is connecting…", Toast.LENGTH_SHORT).show()
            return
        }

        currentQueue = tracks

        c.setMediaItems(
            tracks.map(PlaybackService::mediaItem),
            index,
            0L
        )

        c.prepare()
        c.play()

        miniPlayer.visibility = View.VISIBLE

        updateMiniPlayer()
        saveHistory()
    }

    private fun updateMiniPlayer() {
        val c = controller ?: return
        val item = c.currentMediaItem ?: return

        miniPlayer.visibility = View.VISIBLE

        miniTitle.text = item.mediaMetadata.title ?: "Unknown"
        miniArtist.text = item.mediaMetadata.artist ?: ""

        miniPlay.text = if (c.isPlaying) "Ⅱ" else "▶"
}

    private fun currentTrack(): Track? {
        val c = controller ?: return null
        val index = c.currentMediaItemIndex

        currentQueue.getOrNull(index)?.let { return it }

        val item = c.currentMediaItem ?: return null
        val metadata = item.mediaMetadata
        val mediaId = item.mediaId

        return Track(
            id = mediaId.substringAfter(':', mediaId),
            name = metadata.title?.toString() ?: "Unknown",
            artist = metadata.artist?.toString().orEmpty(),
            image = metadata.artworkUri?.toString().orEmpty(),
            play = item.localConfiguration?.uri?.toString().orEmpty(),
            provider = metadata.albumTitle?.toString()
                ?.ifBlank { null }
                ?: mediaId.substringBefore(':', "streamify")
        )
    }

    private fun showNowPlaying() {
        val track = currentTrack() ?: return

        val dialog = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(28))
            setBackgroundColor(Color.parseColor("#050409"))
        }

        val close = TextView(this).apply {
            text = "×"
            textSize = 34f
            gravity = Gravity.END
            setTextColor(Color.WHITE)
            setOnClickListener { dialog.dismiss() }
        }

        box.addView(close)

        val art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(track.image) {
                crossfade(false)
            }
        }

        box.addView(
            art,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(270)
            ).apply {
                topMargin = dp(12)
            }
        )

        box.addView(TextView(this).apply {
            text = track.name
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(5))
        })

        box.addView(TextView(this).apply {
            text = track.artist
            textSize = 15f
            setTextColor(Color.parseColor("#9AA5B3"))
        })

        box.addView(TextView(this).apply {
            text = track.provider
            textSize = 13f
            setTextColor(Color.parseColor("#E91DFF"))
            setPadding(0, dp(8), 0, dp(18))
        })

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }

        fun control(text: String, action: () -> Unit): TextView =
            TextView(this).apply {
                this.text = text
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(18), dp(14), dp(18), dp(14))
                setOnClickListener { action() }
            }

        controls.addView(control("⏮") {
            controller?.seekToPreviousMediaItem()
        })

        controls.addView(control(
            if (controller?.isPlaying == true) "Ⅱ" else "▶"
        ) {
            controller?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        })

        controls.addView(control("⏭") {
            controller?.seekToNextMediaItem()
        })

        box.addView(controls)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        actions.addView(actionButton("♡ Like") {
            toggleLike(track)
        })

        actions.addView(actionButton("⇩ Download") {
            downloadTrack(track)
        })

        box.addView(actions)

        dialog.setContentView(box)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }



    private fun actionButton(text: String, click: () -> Unit): View =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#151C25"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#2A3542"))
            }
            setOnClickListener { click() }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
                topMargin = dp(18)
            }
        }

    private fun downloadTrack(track: Track) {
        if (track.provider != "JioSaavn" || track.raw.isBlank()) {
            Toast.makeText(
                this,
                "Direct download is unavailable for this track",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val url =
            StreamifyApi.BASE_URL +
            "/api/jio-stream?download=1&url=" +
            Uri.encode(track.raw)

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(track.name)
            .setDescription(track.artist)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_MUSIC,
                sanitize(track.name) + ".m4a"
            )

        val manager =
            getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        manager.enqueue(request)

        Toast.makeText(
            this,
            "Download started",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleLike(track: Track) {
        val current = prefs
            .getStringSet("liked", emptySet())
            ?.toMutableSet() ?: mutableSetOf()

        val value =
            listOf(
                track.id,
                track.name,
                track.artist,
                track.image,
                track.play,
                track.provider,
                track.raw
            ).joinToString("|||")

        if (current.contains(value)) {
            current.remove(value)
            Toast.makeText(this, "Removed from Library", Toast.LENGTH_SHORT).show()
        } else {
            current.add(value)
            Toast.makeText(this, "Added to Library", Toast.LENGTH_SHORT).show()
        }

        prefs.edit().putStringSet("liked", current).apply()
    }

    private fun renderLibrary() {
        libraryContainer.removeAllViews()

        libraryContainer.addView(sectionHeading("Your Library"))

        val liked =
            prefs.getStringSet("liked", emptySet()).orEmpty()

        if (liked.isEmpty()) {
            libraryContainer.addView(emptyText("Like songs to build your library."))
            return
        }

        val tracks = liked.mapNotNull(::decodeTrack)

        tracks.forEachIndexed { index, track ->
            libraryContainer.addView(resultRow(track) {
                playQueue(tracks, index)
            })
        }
    }

    private fun saveHistory() {
        val track = currentTrack() ?: return

        val value =
            listOf(
                track.id,
                track.name,
                track.artist,
                track.image,
                track.play,
                track.provider,
                track.raw
            ).joinToString("|||")

        val current = prefs
            .getString("history", "")
            .orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }
            .toMutableList()

        current.remove(value)
        current.add(0, value)

        prefs.edit()
            .putString("history", current.take(50).joinToString("\n"))
            .apply()
    }

    private fun renderHistory() {
        historyContainer.removeAllViews()
        historyContainer.addView(sectionHeading("Recently Played"))

        val values = prefs
            .getString("history", "")
            .orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }

        val tracks = values.mapNotNull(::decodeTrack)

        if (tracks.isEmpty()) {
            historyContainer.addView(emptyText("Your listening history will appear here."))
            return
        }

        tracks.forEachIndexed { index, track ->
            historyContainer.addView(resultRow(track) {
                playQueue(tracks, index)
            })
        }
    }

    private fun saveSearch(query: String) {
        prefs.edit().putString("last_search", query).apply()
    }

    private fun decodeTrack(value: String): Track? {
        val p = value.split("|||")
        if (p.size < 7) return null

        return Track(
            id = p[0],
            name = p[1],
            artist = p[2],
            image = p[3],
            play = p[4],
            provider = p[5],
            raw = p[6]
        )
    }

    private fun sectionHeading(text: String) =
        TextView(this).apply {
            this.text = text
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(18))
        }

    private fun emptyText(text: String) =
        TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.parseColor("#8994A3"))
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(60), dp(20), dp(60))
        }

    private fun requestNotificationPermission() {
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9 _-]"), "")
            .take(80)
            .ifBlank { "Streamify" }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }

        controller = null
        super.onDestroy()
    }
}