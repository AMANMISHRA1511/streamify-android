package com.aman.streamify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private lateinit var content: LinearLayout

    private lateinit var mini: LinearLayout
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlay: TextView

    private val liked = mutableListOf<Track>()
    private val history = mutableListOf<Track>()
    private val known = mutableMapOf<String, Track>()

    private var currentQuery = "Hindi Bollywood popular songs"
    private var pageCursor = 3
    private var loadingMore = false

    private val prefs by lazy {
        getSharedPreferences(
            "streamify_native_data",
            Context.MODE_PRIVATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor =
            Color.parseColor("#050506")

        window.navigationBarColor =
            Color.parseColor("#050506")

        loadSaved()
        createUi()
        connectPlayer()
        showHome()
    }

    private fun createUi() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#050506"))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(8))
        }

        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
        }

        header.addView(
            logo,
            LinearLayout.LayoutParams(dp(52), dp(52))
        )

        header.addView(
            TextView(this).apply {
                text = "Streamify"
                textSize = 28f
                setTextColor(Color.WHITE)
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
                setPadding(dp(10), 0, 0, 0)
            },
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val avatar = TextView(this).apply {
            text = "AM"
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.WHITE)

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#9A3EFF"))
            }

            setOnClickListener {
                getSharedPreferences(
                    "streamify_native_auth_v10",
                    Context.MODE_PRIVATE
                )
                    .edit()
                    .putBoolean("logged", false)
                    .apply()

                startActivity(
                    Intent(
                        this@MainActivity,
                        LoginActivity::class.java
                    )
                )

                finish()
            }
        }

        header.addView(
            avatar,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        root.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(18))
        }

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        mini = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            visibility = View.GONE

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111016"))
                setStroke(
                    dp(1),
                    Color.parseColor("#33293C")
                )
            }
        }

        miniArt = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        mini.addView(
            miniArt,
            LinearLayout.LayoutParams(dp(52), dp(52))
        )

        val miniInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(5), 0)
        }

        miniTitle = TextView(this).apply {
            textSize = 14f
            maxLines = 1
            setTextColor(Color.WHITE)
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
        }

        miniArtist = TextView(this).apply {
            textSize = 11f
            maxLines = 1
            setTextColor(Color.parseColor("#8C8993"))
        }

        miniInfo.addView(miniTitle)
        miniInfo.addView(miniArtist)

        mini.addView(
            miniInfo,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val prev = playerButton("⏮")
        miniPlay = playerButton("▶")
        val next = playerButton("⏭")

        prev.setOnClickListener {
            controller?.seekToPreviousMediaItem()
        }

        miniPlay.setOnClickListener {
            controller?.let {
                if (it.isPlaying) it.pause()
                else it.play()
            }
        }

        next.setOnClickListener {
            nextSong()
        }

        mini.addView(prev)
        mini.addView(miniPlay)
        mini.addView(next)

        root.addView(mini)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
            setBackgroundColor(Color.parseColor("#08070C"))
        }

        fun navButton(
            text: String,
            click: () -> Unit
        ): TextView {

            val b = TextView(this).apply {
                this.text = text
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(Color.parseColor("#AAA5B1"))
                setPadding(dp(3), dp(9), dp(3), dp(9))
                setOnClickListener { click() }
            }

            nav.addView(
                b,
                LinearLayout.LayoutParams(
                    0,
                    dp(58),
                    1f
                )
            )

            return b
        }

        navButton("⌂\nHome") { showHome() }
        navButton("⌕\nSearch") { showSearch() }
        navButton("♫\nLibrary") { showLibrary() }
        navButton("◷\nHistory") { showHistory() }
        navButton("♡\nLiked") { showLiked() }

        root.addView(nav)

        setContentView(root)
    }

    private fun showHome() {

        content.removeAllViews()

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))

            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        Color.parseColor("#310741"),
                        Color.parseColor("#121020"),
                        Color.parseColor("#08080D")
                    )
                ).apply {
                    cornerRadius = dp(28).toFloat()
                    setStroke(
                        dp(1),
                        Color.parseColor("#5D2575")
                    )
                }
        }

        hero.addView(
            TextView(this).apply {
                text =
                    "India’s soundtrack.\nOne beautiful player."
                textSize = 29f
                setTextColor(Color.WHITE)
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }
        )

        hero.addView(
            TextView(this).apply {
                text =
                    "Bollywood, Bhojpuri, Telugu, Tamil, Punjabi, romantic and more."
                textSize = 14f
                setTextColor(Color.parseColor("#B0AAB7"))
                setPadding(0, dp(14), 0, dp(18))
            }
        )

        val search = TextView(this).apply {
            text = "Search music  →"
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
            setPadding(dp(18), dp(13), dp(18), dp(13))

            background =
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(
                        Color.parseColor("#E51BEA"),
                        Color.parseColor("#7438FF")
                    )
                ).apply {
                    cornerRadius = dp(28).toFloat()
                }

            setOnClickListener {
                showSearch()
            }
        }

        hero.addView(search)

        content.addView(hero)

        addHomeSection(
            "Trending Now",
            "India trending songs"
        )

        addHomeSection(
            "New Releases",
            "new Hindi Bollywood songs"
        )

        addHomeSection(
            "Bollywood Hits",
            "Bollywood hits"
        )

        addHomeSection(
            "Bhojpuri Beats",
            "Bhojpuri hits"
        )

        addHomeSection(
            "Romantic Mix",
            "Hindi romantic songs"
        )
    }

    private fun addHomeSection(
        title: String,
        query: String
    ) {

        val heading = TextView(this).apply {
            text = title
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
            setPadding(0, dp(26), 0, dp(8))
        }

        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        content.addView(heading)

        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(holder)
        }

        content.addView(horizontal)

        lifecycleScope.launch {

            val songs =
                StreamifyApi.search(
                    query,
                    1,
                    2
                )

            songs.take(10).forEach { t ->
                known[t.id] = t
                holder.addView(card(t, songs, query))
            }
        }
    }

    private fun card(
        t: Track,
        queue: List<Track>,
        query: String
    ): View {

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(8))

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111016"))
                cornerRadius = dp(16).toFloat()
                setStroke(
                    dp(1),
                    Color.parseColor("#27222D")
                )
            }

            setOnClickListener {
                playFlow(t, queue, query)
            }
        }

        val art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(t.image)
        }

        box.addView(
            art,
            LinearLayout.LayoutParams(dp(130), dp(130))
        )

        box.addView(
            TextView(this).apply {
                text = t.name
                textSize = 14f
                maxLines = 1
                setTextColor(Color.WHITE)
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
                setPadding(0, dp(7), 0, 0)
            }
        )

        box.addView(
            TextView(this).apply {
                text = t.artist
                textSize = 11f
                maxLines = 1
                setTextColor(Color.parseColor("#85818B"))
            }
        )

        return box.apply {
            layoutParams =
                LinearLayout.LayoutParams(
                    dp(145),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(10)
                }
        }
    }

    private fun showSearch() {

        content.removeAllViews()

        content.addView(
            title("Search")
        )

        val input = EditText(this).apply {
            hint = "Search song, singer or album"
            textSize = 17f
            singleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#77747F"))
            setPadding(dp(18), 0, dp(18), 0)

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#15141B"))
                setStroke(
                    dp(1),
                    Color.parseColor("#382F43")
                )
                cornerRadius = dp(22).toFloat()
            }
        }

        content.addView(
            input,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(60)
            )
        )

        val button = TextView(this).apply {
            text = "Search"
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )

            background =
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(
                        Color.parseColor("#E51BEA"),
                        Color.parseColor("#7438FF")
                    )
                ).apply {
                    cornerRadius = dp(25).toFloat()
                }
        }

        content.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(12)
            }
        )

        val resultHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }

        content.addView(resultHolder)

        fun runSearch() {

            val q = input.text.toString().trim()
            if (q.length < 2) return

            resultHolder.removeAllViews()

            resultHolder.addView(
                TextView(this).apply {
                    text = "Searching…"
                    setTextColor(Color.parseColor("#AAA5B1"))
                    setPadding(0, dp(12), 0, dp(12))
                }
            )

            lifecycleScope.launch {

                val songs =
                    StreamifyApi.search(
                        q,
                        1,
                        3
                    )

                resultHolder.removeAllViews()

                if (songs.isEmpty()) {

                    resultHolder.addView(
                        TextView(this@MainActivity).apply {
                            text =
                                "No songs found. Check internet and try again."
                            textSize = 15f
                            setTextColor(
                                Color.parseColor("#A9A3B0")
                            )
                            setPadding(
                                0,
                                dp(18),
                                0,
                                dp(18)
                            )
                        }
                    )

                    return@launch
                }

                songs.forEach { t ->
                    known[t.id] = t
                    resultHolder.addView(
                        trackRow(
                            t,
                            songs,
                            q
                        )
                    )
                }
            }
        }

        button.setOnClickListener {
            runSearch()
        }

        input.setOnEditorActionListener { _, _, _ ->
            runSearch()
            true
        }
    }

    private fun trackRow(
        t: Track,
        queue: List<Track>,
        query: String
    ): View {

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))

            setOnClickListener {
                playFlow(t, queue, query)
            }
        }

        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(t.image)
        }

        row.addView(
            image,
            LinearLayout.LayoutParams(dp(58), dp(58))
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(7), 0)
        }

        info.addView(
            TextView(this).apply {
                text = t.name
                textSize = 15f
                maxLines = 1
                setTextColor(Color.WHITE)
                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }
        )

        info.addView(
            TextView(this).apply {
                text = t.artist
                textSize = 12f
                maxLines = 1
                setTextColor(Color.parseColor("#85818B"))
            }
        )

        row.addView(
            info,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val heart = TextView(this).apply {
            text = if (isLiked(t)) "♥" else "♡"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(
                if (isLiked(t))
                    Color.parseColor("#EB3FE8")
                else
                    Color.parseColor("#A5A0AA")
            )

            setOnClickListener {
                toggleLike(t)
                text = if (isLiked(t)) "♥" else "♡"
                setTextColor(
                    if (isLiked(t))
                        Color.parseColor("#EB3FE8")
                    else
                        Color.parseColor("#A5A0AA")
                )
            }
        }

        row.addView(
            heart,
            LinearLayout.LayoutParams(dp(50), dp(50))
        )

        return row
    }

    private fun playFlow(
        selected: Track,
        original: List<Track>,
        query: String
    ) {

        val c = controller ?: run {
            Toast.makeText(
                this,
                "Player is connecting…",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        currentQuery = relatedQuery(selected, query)
        pageCursor = 3

        val queue =
            listOf(selected) +
                original.filter {
                    it.id != selected.id
                }

        queue.forEach {
            known[it.id] = it
        }

        c.setMediaItems(
            queue
                .distinctBy { it.id }
                .map(PlaybackService::mediaItem)
        )

        c.prepare()
        c.play()

        addHistory(selected)
        mini.visibility = View.VISIBLE

        lifecycleScope.launch {
            refill()
        }
    }

    private fun relatedQuery(
        t: Track,
        original: String
    ): String {

        val text =
            "${t.name} ${t.album} $original"
                .lowercase()

        val language =
            when {
                t.language.isNotBlank() ->
                    t.language

                "bhojpuri" in text ->
                    "bhojpuri"

                "telugu" in text ->
                    "telugu"

                "tamil" in text ->
                    "tamil"

                "punjabi" in text ->
                    "punjabi"

                else ->
                    "hindi bollywood"
            }

        val mood =
            when {

                listOf(
                    "bhajan",
                    "bhakti",
                    "aarti",
                    "mahadev",
                    "hanuman",
                    "krishna"
                ).any { it in text } ->
                    "devotional bhakti songs"

                listOf(
                    "sad",
                    "dard",
                    "bewafa",
                    "judai"
                ).any { it in text } ->
                    "sad songs"

                listOf(
                    "love",
                    "romantic",
                    "pyaar",
                    "ishq",
                    "dil",
                    "awarapan"
                ).any { it in text } ->
                    "romantic songs"

                else ->
                    "popular songs"
            }

        return "$language $mood"
    }

    private fun nextSong() {

        val c = controller ?: return

        if (c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
            c.play()
        } else {
            lifecycleScope.launch {
                refill()

                if (c.hasNextMediaItem()) {
                    c.seekToNextMediaItem()
                    c.play()
                }
            }
        }
    }

    private suspend fun refill() {

        if (loadingMore) return
        loadingMore = true

        try {

            val c = controller ?: return

            val newTracks =
                StreamifyApi.search(
                    currentQuery,
                    pageCursor,
                    2
                )

            pageCursor += 2

            if (pageCursor > 19)
                pageCursor = 1

            val existing =
                (0 until c.mediaItemCount)
                    .map {
                        c.getMediaItemAt(it).mediaId
                    }
                    .toSet()

            val fresh =
                newTracks.filter {
                    it.id !in existing &&
                        it.play.isNotBlank()
                }

            fresh.forEach {
                known[it.id] = it
            }

            if (fresh.isNotEmpty()) {
                c.addMediaItems(
                    fresh.map(
                        PlaybackService::mediaItem
                    )
                )
            }

        } finally {
            loadingMore = false
        }
    }

    private fun connectPlayer() {

        val token =
            SessionToken(
                this,
                ComponentName(
                    this,
                    PlaybackService::class.java
                )
            )

        future =
            MediaController.Builder(
                this,
                token
            ).buildAsync()

        future?.addListener({

            controller =
                runCatching {
                    future?.get()
                }.getOrNull()

            controller?.addListener(
                object : Player.Listener {

                    override fun onIsPlayingChanged(
                        isPlaying: Boolean
                    ) {
                        miniPlay.text =
                            if (isPlaying) "⏸"
                            else "▶"
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {

                        updateMini()

                        mediaItem
                            ?.mediaId
                            ?.let { known[it] }
                            ?.let(::addHistory)

                        val c = controller ?: return

                        if (
                            c.mediaItemCount -
                                c.currentMediaItemIndex <= 6
                        ) {
                            lifecycleScope.launch {
                                refill()
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(
                        playbackState: Int
                    ) {

                        if (
                            playbackState ==
                                Player.STATE_ENDED
                        ) {
                            lifecycleScope.launch {
                                refill()
                                nextSong()
                            }
                        }
                    }
                }
            )

            updateMini()

        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateMini() {

        val c = controller ?: return
        val item = c.currentMediaItem ?: return

        mini.visibility = View.VISIBLE

        miniTitle.text =
            item.mediaMetadata.title
                ?.toString()
                ?: "Unknown Song"

        miniArtist.text =
            item.mediaMetadata.artist
                ?.toString()
                ?: ""

        item.mediaMetadata
            .artworkUri
            ?.let {
                miniArt.load(it)
            }
    }

    private fun showLiked() {
        content.removeAllViews()
        content.addView(title("Liked Songs"))

        if (liked.isEmpty()) {
            empty("No liked songs yet")
            return
        }

        liked.forEach {
            content.addView(
                trackRow(
                    it,
                    liked,
                    relatedQuery(it, "")
                )
            )
        }
    }

    private fun showHistory() {
        content.removeAllViews()
        content.addView(title("Recently Played"))

        if (history.isEmpty()) {
            empty("No listening history yet")
            return
        }

        history.forEach {
            content.addView(
                trackRow(
                    it,
                    history,
                    relatedQuery(it, "")
                )
            )
        }
    }

    private fun showLibrary() {
        content.removeAllViews()
        content.addView(title("Your Library"))

        val likedButton = bigTile(
            "♥  Liked Songs\n${liked.size} songs"
        ) {
            showLiked()
        }

        val historyButton = bigTile(
            "◷  Listening History\n${history.size} songs"
        ) {
            showHistory()
        }

        content.addView(likedButton)
        content.addView(historyButton)
    }

    private fun bigTile(
        text: String,
        click: () -> Unit
    ): View =
        TextView(this).apply {

            this.text = text
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(
                dp(20),
                dp(24),
                dp(20),
                dp(24)
            )

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#15121B"))
                cornerRadius = dp(18).toFloat()
                setStroke(
                    dp(1),
                    Color.parseColor("#35283E")
                )
            }

            setOnClickListener {
                click()
            }

            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
        }

    private fun toggleLike(t: Track) {

        if (isLiked(t)) {
            liked.removeAll {
                it.id == t.id
            }
        } else {
            liked.add(0, t)
        }

        saveList("liked", liked)
    }

    private fun isLiked(t: Track) =
        liked.any { it.id == t.id }

    private fun addHistory(t: Track) {

        history.removeAll {
            it.id == t.id
        }

        history.add(0, t)

        while (history.size > 60)
            history.removeLast()

        saveList("history", history)
    }

    private fun saveList(
        key: String,
        list: List<Track>
    ) {

        val arr = JSONArray()

        list.forEach { t ->

            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("artist", t.artist)
                    put("album", t.album)
                    put("image", t.image)
                    put("play", t.play)
                    put("language", t.language)
                    put("year", t.year)
                }
            )
        }

        prefs.edit()
            .putString(key, arr.toString())
            .apply()
    }

    private fun loadSaved() {

        liked += readList("liked")
        history += readList("history")

        liked.forEach {
            known[it.id] = it
        }

        history.forEach {
            known[it.id] = it
        }
    }

    private fun readList(key: String): List<Track> {

        val raw =
            prefs.getString(key, null)
                ?: return emptyList()

        return runCatching {

            val arr = JSONArray(raw)

            List(arr.length()) { i ->

                val s = arr.getJSONObject(i)

                Track(
                    id = s.getString("id"),
                    name = s.getString("name"),
                    artist = s.getString("artist"),
                    album = s.optString("album"),
                    image = s.optString("image"),
                    play = s.getString("play"),
                    language = s.optString("language"),
                    year = s.optString("year")
                )
            }

        }.getOrDefault(emptyList())
    }

    private fun empty(text: String) {
        content.addView(
            TextView(this).apply {
                this.text = text
                textSize = 15f
                setTextColor(Color.parseColor("#89848F"))
                gravity = Gravity.CENTER
                setPadding(0, dp(50), 0, dp(50))
            }
        )
    }

    private fun title(text: String) =
        TextView(this).apply {
            this.text = text
            textSize = 30f
            setTextColor(Color.WHITE)
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
            setPadding(0, dp(10), 0, dp(20))
        }

    private fun playerButton(symbol: String) =
        TextView(this).apply {
            text = symbol
            gravity = Gravity.CENTER
            textSize = 20f
            setTextColor(Color.WHITE)
        }.also {
            it.layoutParams =
                LinearLayout.LayoutParams(
                    dp(45),
                    dp(50)
                )
        }

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {

        future?.let {
            MediaController.releaseFuture(it)
        }

        controller = null
        super.onDestroy()
    }
}
