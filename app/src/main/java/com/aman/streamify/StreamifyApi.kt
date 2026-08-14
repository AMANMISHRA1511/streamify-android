package com.aman.streamify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object StreamifyApi {

    /*
     * Streamify backend proxies the JioSaavn-compatible catalog source.
     * Keeping it behind one backend endpoint avoids Android CORS/API
     * shape changes and lets full playable URLs be normalized server-side.
     */
    const val BASE_URL =
        "https://streamify-india-5dyhat.v2.appdeploy.ai"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun search(
        query: String,
        provider: String = "jio"
    ): List<Track> = withContext(Dispatchers.IO) {

        val q = query.trim()

        if (q.length < 2)
            return@withContext emptyList()

        val encoded =
            URLEncoder.encode(q, "UTF-8")

        val urls = listOf(
            "$BASE_URL/api/search?q=$encoded&page=1",
            "$BASE_URL/api/search?q=$encoded&page=2"
        )

        val output = mutableListOf<Track>()
        val seen = mutableSetOf<String>()

        for (url in urls) {

            try {

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header(
                        "User-Agent",
                        "Streamify-Android/7.0"
                    )
                    .build()

                client.newCall(request)
                    .execute()
                    .use { response ->

                        if (!response.isSuccessful)
                            return@use

                        val text =
                            response.body
                                ?.string()
                                .orEmpty()

                        if (text.isBlank())
                            return@use

                        val root =
                            JSONObject(text)

                        val array =
                            root.optJSONArray("tracks")
                                ?: return@use

                        for (i in 0 until array.length()) {

                            val s =
                                array.optJSONObject(i)
                                    ?: continue

                            val id =
                                s.optString("id")
                                    .ifBlank {
                                        "song-$i-${q.hashCode()}"
                                    }

                            val title =
                                s.optString("title")
                                    .ifBlank {
                                        s.optString("name")
                                    }
                                    .ifBlank {
                                        "Unknown Song"
                                    }

                            val artist =
                                s.optString("artist")
                                    .ifBlank {
                                        "Unknown Artist"
                                    }

                            val image =
                                s.optString("image")

                            val stream =
                                s.optString("stream")
                                    .trim()

                            if (stream.isBlank())
                                continue

                            val key =
                                title.lowercase()
                                    .replace(
                                        Regex("[^a-z0-9]"),
                                        ""
                                    )

                            if (key.isBlank() ||
                                !seen.add(key)
                            ) continue

                            output += Track(
                                id = id,
                                name = title,
                                artist = artist,
                                image = image,
                                play = stream,
                                provider = "Streamify",
                                raw = s.toString()
                            )
                        }
                    }

            } catch (_: Exception) {
                // Try the next page/fallback automatically.
            }

            if (output.size >= 20)
                break
        }

        output
    }
}
