package com.aman.streamify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object StreamifyApi {

    private const val DIRECT =
        "https://streamify-catalog-aman-1kg8.onrender.com/api"

    private const val APPDEPLOY =
        "https://streamify-india-5dyhat.v2.appdeploy.ai"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun request(url: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Streamify-Native/10.0")
                .build()

            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) null
                else r.body?.string()
            }
        }.getOrNull()
    }

    private fun best(arr: JSONArray?): String {
        if (arr == null) return ""

        var best = ""
        var quality = -1

        for (i in 0 until arr.length()) {
            val x = arr.optJSONObject(i) ?: continue
            val u = x.optString("url")
            if (u.isBlank()) continue

            val q = Regex("\\d+")
                .find(x.optString("quality"))
                ?.value
                ?.toIntOrNull() ?: 0

            if (q >= quality) {
                quality = q
                best = u
            }
        }

        return best
    }

    private fun artists(s: JSONObject): String {
        val arr = s
            .optJSONObject("artists")
            ?.optJSONArray("primary")
            ?: return s.optString("artist", "Unknown Artist")

        val out = mutableListOf<String>()

        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)
                ?.optString("name")
                ?.takeIf { it.isNotBlank() }
                ?.let(out::add)
        }

        return out.joinToString(", ")
            .ifBlank { "Unknown Artist" }
    }

    private fun directTrack(
        s: JSONObject,
        index: Int
    ): Track? {

        val stream =
            best(s.optJSONArray("downloadUrl"))
                .ifBlank {
                    best(s.optJSONArray("download_url"))
                }

        if (stream.isBlank()) return null

        val image =
            best(s.optJSONArray("image"))
                .ifBlank {
                    best(
                        s.optJSONObject("album")
                            ?.optJSONArray("image")
                    )
                }

        return Track(
            id = s.optString("id")
                .ifBlank { "track-$index" },

            name = s.optString("name")
                .ifBlank { s.optString("title") }
                .ifBlank { "Unknown Song" },

            artist = artists(s),

            album = s
                .optJSONObject("album")
                ?.optString("name")
                .orEmpty(),

            image = image,
            play = stream,
            language = s.optString("language").lowercase(),
            year = s.optString("year"),
            raw = s.toString()
        )
    }

    private fun parseDirect(text: String): List<Track> {

        val root = JSONObject(text)

        val arr =
            root.optJSONObject("data")
                ?.optJSONArray("results")
                ?: root.optJSONArray("data")
                ?: root.optJSONArray("results")
                ?: JSONArray()

        val list = mutableListOf<Track>()

        for (i in 0 until arr.length()) {
            directTrack(
                arr.optJSONObject(i) ?: continue,
                i
            )?.let(list::add)
        }

        return list
    }

    private fun parseAppDeploy(text: String): List<Track> {

        val root = JSONObject(text)
        val arr = root.optJSONArray("tracks") ?: JSONArray()

        val list = mutableListOf<Track>()

        for (i in 0 until arr.length()) {

            val s = arr.optJSONObject(i) ?: continue
            val stream = s.optString("stream")

            if (stream.isBlank()) continue

            list += Track(
                id = s.optString("id")
                    .ifBlank { "fallback-$i" },

                name = s.optString("title")
                    .ifBlank { "Unknown Song" },

                artist = s.optString("artist")
                    .ifBlank { "Unknown Artist" },

                album = s.optString("album"),
                image = s.optString("image"),
                play = stream,
                language = s.optString("language").lowercase(),
                year = s.optString("year"),
                raw = s.toString()
            )
        }

        return list
    }

    suspend fun search(
        query: String,
        startPage: Int = 1,
        pages: Int = 2
    ): List<Track> = withContext(Dispatchers.IO) {

        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()

        val encoded = URLEncoder.encode(q, "UTF-8")
        val all = mutableListOf<Track>()
        val seen = mutableSetOf<String>()

        for (page in startPage until startPage + pages) {

            val body = request(
                "$DIRECT/search/songs?query=$encoded&page=$page&limit=30"
            )

            if (!body.isNullOrBlank()) {
                runCatching {
                    parseDirect(body)
                }.getOrDefault(emptyList()).forEach { t ->

                    val key =
                        "${t.name.lowercase()}|${t.artist.lowercase()}"

                    if (t.play.isNotBlank() && seen.add(key)) {
                        all += t
                    }
                }
            }
        }

        if (all.isEmpty()) {

            for (page in startPage until startPage + pages) {

                val body = request(
                    "$APPDEPLOY/api/search?q=$encoded&page=$page"
                )

                if (!body.isNullOrBlank()) {
                    runCatching {
                        parseAppDeploy(body)
                    }.getOrDefault(emptyList()).forEach { t ->

                        val key =
                            "${t.name.lowercase()}|${t.artist.lowercase()}"

                        if (t.play.isNotBlank() && seen.add(key)) {
                            all += t
                        }
                    }
                }
            }
        }

        all.take(60)
    }
}
