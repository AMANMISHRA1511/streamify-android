package com.aman.streamify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

object StreamifyApi {
    private const val BASE = "https://streamify-fixed.onrender.com"
    private val client = OkHttpClient()

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val url = "$BASE/api/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string().orEmpty()
            val arr = JSONArray(body)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Track(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            artist = o.optString("artist"),
                            image = o.optString("image"),
                            play = absolute(o.optString("play")),
                            provider = o.optString("provider"),
                            raw = o.optString("raw")
                        )
                    )
                }
            }
        }
    }

    private fun absolute(path: String): String {
        if (path.startsWith("http")) return path
        return BASE + path
    }
}