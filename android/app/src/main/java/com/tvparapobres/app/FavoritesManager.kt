package com.tvparapobres.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favoritos aislados por perfil: tvpp_favs_{profileId}
 * Entrada: {id, name, logo, type, ext, ts}
 */
object FavoritesManager {
    private var appCtx: Context? = null
    private var sp: SharedPreferences? = null

    fun init(ctx: Context, profileId: String) {
        appCtx = ctx.applicationContext
        sp = appCtx?.getSharedPreferences("tvpp_favs_$profileId", Context.MODE_PRIVATE)
    }

    fun setProfile(profileId: String) {
        sp = appCtx?.getSharedPreferences("tvpp_favs_$profileId", Context.MODE_PRIVATE)
    }

    private fun prefs(): SharedPreferences = sp ?: throw IllegalStateException("FavoritesManager.init() no llamado")

    fun load(): List<JSONObject> {
        val raw = prefs().getString("favs", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isFav(id: String, type: String): Boolean =
        load().any { it.optString("type") == type && it.optString("id") == id }

    /** Devuelve (listaNueva, esFavoritoAhora). */
    fun toggle(item: JSONObject): Pair<List<JSONObject>, Boolean> {
        val arr = load()
        val id = item.optString("id")
        val type = item.optString("type", "live")
        val exists = arr.any { it.optString("type") == type && it.optString("id") == id }
        val out = if (exists) {
            arr.filterNot { it.optString("type") == type && it.optString("id") == id }
        } else {
            listOf(item) + arr
        }
        val jarr = JSONArray()
        out.forEach { jarr.put(it) }
        prefs().edit().putString("favs", jarr.toString()).apply()
        return out to !exists
    }
}