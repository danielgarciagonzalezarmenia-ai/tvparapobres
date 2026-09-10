package com.tvparapobres.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Cat(
    val id: String,
    val name: String
)

data class Strm(
    val id: String,
    val name: String,
    val icon: String,
    val ext: String = "",
    val type: String = "live" // live | vod | series
)

data class Episode(
    val id: String,
    val num: String,
    val title: String,
    val ext: String
)

object Xtream {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val ADULT = Regex("(?i)(adulto?s?|hentai|porn(?:o|ografia)?|x{3,}|erotic|18\\+|\\+18)")

    private suspend fun api(action: String, extra: Map<String, Any> = emptyMap()): JSONObject? =
        withContext(Dispatchers.IO) {
            val c = Accounts.next()
            val sb = StringBuilder("${Accounts.SERVER}/player_api.php?username=${c.user}&password=${c.pass}")
            if (action.isNotEmpty()) sb.append("&action=").append(action)
            for ((k, v) in extra) sb.append("&").append(k).append("=").append(v)
            val req = Request.Builder().url(sb.toString()).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val t = body.trim()
                when {
                    t.startsWith("{") -> try { return@withContext JSONObject(t) } catch (e: Exception) { null }
                    t.startsWith("[") -> return@withContext JSONObject().put("_arr", JSONArray(t))
                    else -> null
                }
            }
        }

    private fun JSONObject.arr(): List<JSONObject> =
        if (has("_arr")) (0 until getJSONArray("_arr").length()).map { getJSONArray("_arr").getJSONObject(it) }
        else emptyList()

    private fun JSONObject.cats(): List<Cat> =
        arr().mapNotNull { j ->
            val id = j.optString("category_id")
            val name = j.optString("category_name")
            if (id.isEmpty() || name.isEmpty() || ADULT.containsMatchIn(name)) null else Cat(id, name)
        }

    private fun JSONObject.streams(type: String): List<Strm> =
        arr().mapNotNull { j ->
            val id = j.optString("stream_id").ifEmpty { j.optString("series_id") }
            val name = j.optString("name").ifEmpty { return@mapNotNull null }
            if (ADULT.containsMatchIn(name)) return@mapNotNull null
            Strm(
                id = id,
                name = name,
                icon = j.optString("stream_icon"),
                ext = j.optString("container_extension", ""),
                type = type
            )
        }

    suspend fun liveCategories(): List<Cat> = api("get_live_categories")?.cats() ?: emptyList()
    suspend fun liveStreams(catId: String): List<Strm> =
        api("get_live_streams", mapOf("category_id" to catId))?.streams("live") ?: emptyList()

    suspend fun vodCategories(): List<Cat> = api("get_vod_categories")?.cats() ?: emptyList()
    suspend fun vodStreams(catId: String): List<Strm> =
        api("get_vod_streams", mapOf("category_id" to catId))?.streams("vod") ?: emptyList()

    suspend fun seriesCategories(): List<Cat> = api("get_series_categories")?.cats() ?: emptyList()
    suspend fun series(catId: String): List<Strm> =
        api("get_series", mapOf("category_id" to catId))?.streams("series") ?: emptyList()

    suspend fun seriesInfo(seriesId: String): List<Episode> =
        api("get_series_info", mapOf("series_id" to seriesId))?.let { obj ->
            obj.optJSONObject("episodes")?.let { epsObj ->
                val out = mutableListOf<Episode>()
                val keys = epsObj.keys()
                while (keys.hasNext()) {
                    val season = keys.next()
                    val arr = epsObj.optJSONArray(season) ?: continue
                    for (i in 0 until arr.length()) {
                        val ep = arr.getJSONObject(i)
                        out.add(
                            Episode(
                                id = ep.optString("id"),
                                num = ep.optString("episode_num"),
                                title = ep.optString("title"),
                                ext = ep.optString("container_extension", "mp4")
                            )
                        )
                    }
                }
                out
            } ?: emptyList()
        } ?: emptyList()
}