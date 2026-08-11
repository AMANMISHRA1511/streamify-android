package com.aman.streamify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

object StreamifyApi {

    const val BASE_URL = "https://streamify-fixed.onrender.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, provider: String = "mixed"): List<Track> =
        withContext(Dispatchers.IO) {

            val path = when (provider) {
                "jio" -> "/api/jio-search"
                "audius" -> "/api/audius-search"
                else -> "/api/search"
            }

            val url = BASE_URL + path +
                    "?q=" + java.net.URLEncoder.encode(query, "UTF-8")

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()

                    val array = JSONArray(response.body?.string().orEmpty())

                    buildList {
                        for (i in 0 until array.length()) {
                            val o = array.getJSONObject(i)

                            val play = absolute(o.optString("play"))
                            if (play.isBlank()) continue

                            add(
                                Track(
                                    id = o.optString("id"),
                                    name = o.optString("name", "Unknown"),
                                    artist = o.optString("artist", "Unknown artist"),
                                    image = o.optString("image"),
                                    play = play,
                                    provider = o.optString("provider"),
                                    raw = o.optString("raw")
                                )
                            )
                        }
                    }
                }
            }.getOrElse { emptyList() }
        }

    private fun absolute(path: String): String {
        if (path.isBlank()) return ""
        if (path.startsWith("http")) return path
        return BASE_URL + path
    }
}