package com.tvparapobres.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
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
    val type: String = "live", // live | vod | series
    val pos: Long = 0, // historial: posición ms
    val dur: Long = 0, // historial: duración ms
    val hkey: String = "" // historial: clave para borrar
)

data class Episode(
    val id: String,
    val num: String,
    val title: String,
    val ext: String,
    val dur: String = ""
)

data class Season(val id: String, val eps: List<Episode>)

data class SeriesFull(
    val cover: String,
    val name: String,
    val year: String,
    val rating: String,
    val genre: String,
    val plot: String,
    val cast: String,
    val seasonNames: Map<String, String>,
    val seasons: List<Season>
)

object Xtream {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val ADULT = Regex("(?i)(adulto?s?|hentai|porn(?:o|ografia)?|x{3,}|erotic|18\\+|\\+18)")

    private suspend fun api(action: String, extra: Map<String, Any> = emptyMap(), stable: Boolean = false): JSONObject? =
        withContext(Dispatchers.IO) {
            val c = if (stable) Accounts.stable() else Accounts.next()
            val sb = StringBuilder("${Accounts.SERVER}/player_api.php?username=${c.user}&password=${c.pass}")
            if (action.isNotEmpty()) sb.append("&action=").append(action)
            for ((k, v) in extra) sb.append("&").append(k).append("=").append(v)
            val req = Request.Builder().url(sb.toString()).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.bytes() ?: return@withContext null
                val t = String(body, Charsets.UTF_8).trim()
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

    /** user_info con cuenta estable + fecha de vencimiento (epoch seg), como api.meta() de la web. */
    suspend fun userInfo(): JSONObject? = api("", stable = true)

    fun expDate(info: JSONObject?): Long =
        info?.optJSONObject("user_info")?.optString("exp_date")?.toLongOrNull() ?: -1

    suspend fun liveCategories(): List<Cat> = api("get_live_categories")?.cats() ?: emptyList()
    suspend fun liveStreams(catId: String): List<Strm> =
        api("get_live_streams", mapOf("category_id" to catId))?.streams("live") ?: emptyList()

    /** Todos los canales en vivo (lista única, sin categorías) como en la web. */
    suspend fun liveAll(): List<Strm> =
        api("get_live_streams")?.streams("live") ?: emptyList()

    data class EpgLine(val start: Long, val end: Long, val title: String)

    /** EPG corto de un canal; tolera epoch (seg) o fechas "yyyy-MM-dd HH:mm:ss". */
    suspend fun shortEpg(streamId: String, limit: Int = 6): List<EpgLine> =
        api("get_short_epg", mapOf("stream_id" to streamId, "limit" to limit))
            ?.arr()
            ?.mapNotNull { j ->
                val s = parseEpgTime(j.opt("start"))
                val e = parseEpgTime(j.opt("end"))
                if (s <= 0 || e <= 0) return@mapNotNull null
                EpgLine(s, e, epgTitle(j.optString("title", "Sin título")))
            }
            ?: emptyList()

    private val R_B64 = Regex("^[A-Za-z0-9+/]+={0,2}$")
    private val R_WS = Regex("\\s")

    /** Si el título viene base64 lo decodifica (igual que client/src/App.jsx) */
    private fun epgTitle(t: String): String {
        val s = t.trim()
        if (s.length < 8 || s.length % 4 != 0) return s
        if (!R_B64.matches(s)) return s
        return try {
            val dec = String(android.util.Base64.decode(s, android.util.Base64.DEFAULT)).trim()
            val printableAscii = dec.all { ch ->
                ch == '\u0009' || ch == '\u000A' || ch == '\u000D' || (ch.code in 0x20..0x7E)
            }
            if (dec.length >= 4 && printableAscii && R_WS.containsMatchIn(dec)) dec else s
        } catch (_: Exception) {
            s
        }
    }

    private fun parseEpgTime(v: Any?): Long {
        return when (v) {
            is Number -> v.toLong()
            is String -> {
                val n = v.toLongOrNull()
                if (n != null) return n
                try {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .parse(v.trim())?.time?.div(1000) ?: -1
                } catch (_: Exception) {
                    -1
                }
            }
            else -> -1
        }
    }

    /**
     * Orden natural alfabético-español (ignora acentos, dígitos numéricos),
     * equivalente a alphaCmp de la web.
     */
    fun liveSorter(list: List<Strm>): List<Strm> {
        val col = java.text.Collator.getInstance(java.util.Locale("es", "ES"))
        col.strength = java.text.Collator.PRIMARY
        return list.sortedWith(Comparator { a, b -> col.compare(NameCleaner.clean(a.name), NameCleaner.clean(b.name)) })
    }

    suspend fun vodCategories(): List<Cat> = api("get_vod_categories")?.cats() ?: emptyList()
    suspend fun vodStreams(catId: String): List<Strm> =
        api("get_vod_streams", mapOf("category_id" to catId))?.streams("vod") ?: emptyList()

    suspend fun seriesCategories(): List<Cat> = api("get_series_categories")?.cats() ?: emptyList()
    suspend fun series(catId: String): List<Strm> =
        api("get_series", mapOf("category_id" to catId))?.streams("series") ?: emptyList()

    suspend fun seriesInfo(seriesId: String): List<Episode> =
        seriesFull(seriesId)?.seasons?.flatMap { it.eps } ?: emptyList()

    /** Ficha completa de serie como el modal web: portada, meta, temporadas y episodios. */
    suspend fun seriesFull(seriesId: String): SeriesFull? =
        api("get_series_info", mapOf("series_id" to seriesId))?.let { obj ->
            val info = obj.optJSONObject("info") ?: JSONObject()
            val seasonNames = mutableMapOf<String, String>()
            obj.optJSONArray("seasons")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    seasonNames[s.optString("season_number")] = s.optString("name")
                }
            }
            val seasons = mutableListOf<Season>()
            obj.optJSONObject("episodes")?.let { epsObj ->
                val keys = epsObj.keys()
                val sk = mutableListOf<String>()
                while (keys.hasNext()) sk.add(keys.next())
                sk.sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
                    .forEach { season ->
                        val arr = epsObj.optJSONArray(season) ?: return@forEach
                        val eps = (0 until arr.length()).mapNotNull { i ->
                            val ep = arr.optJSONObject(i) ?: return@mapNotNull null
                            Episode(
                                ep.optString("id"),
                                ep.optString("episode_num"),
                                ep.optString("title"),
                                ep.optString("container_extension", "mp4"),
                                ep.optJSONObject("info")?.optString("duration").orEmpty()
                            )
                        }.sortedWith(compareBy<Episode> { it.num.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.title })
                        if (eps.isNotEmpty()) seasons.add(Season(season, eps))
                    }
            }
            SeriesFull(
                cover = info.optString("cover")
                    .ifEmpty { info.optString("movie_image").ifEmpty { info.optString("backdrop_path") } },
                name = info.optString("name"),
                year = info.optString("releaseDate").ifEmpty { info.optString("year") },
                rating = info.optString("rating").ifEmpty { info.optString("rating_5based") },
                genre = info.optString("genre"),
                plot = info.optString("plot"),
                cast = info.optString("cast"),
                seasonNames = seasonNames,
                seasons = seasons
            )
        }
}