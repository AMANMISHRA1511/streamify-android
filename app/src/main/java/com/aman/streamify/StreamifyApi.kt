package com.aman.streamify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object StreamifyApi {

    const val BASE_URL = "https://streamify-india-5dyhat.v2.appdeploy.ai"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        query: String,
        provider: String = "streamify"
    ): List<Track> = withContext(Dispatchers.IO) {

        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()

        val url =
            "$BASE_URL/api/search?q=${URLEncoder.encode(q, "UTF-8")}&page=1"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    return@withContext emptyList()
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext emptyList()
                }

                val root = JSONObject(body)

                val songs = root.optJSONArray("tracks")
                    ?: return@withContext emptyList()

                buildList {

                    for (i in 0 until songs.length()) {

                        val song = songs.optJSONObject(i)
                            ?: continue

                        val stream = song
                            .optString("stream")
                            .trim()

                        if (stream.isBlank()) continue

                        add(
                            Track(
                                id = song.optString(
                                    "id",
                                    "streamify-$i"
                                ),

                                name = song.optString(
                                    "title",
                                    "Unknown Song"
                                ),

                                artist = song.optString(
                                    "artist",
                                    "Unknown Artist"
                                ),

                                image = song.optString(
                                    "image"
                                ),

                                play = stream,

                                provider = "Streamify",

                                raw = song.toString()
                            )
                        )
                    }
                }
            }

        }.getOrElse {
            emptyList()
        }
    }
}
