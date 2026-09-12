package com.tvparapobres.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Historial de reproducción persistente, aislado por perfil.
 * Cada entrada: {key, name, logo, type, url, position, duration, ts}
 * key = "live:{id}" | "vod:{id}" | "ser:{epId}"
 */
object HistoryManager {
    private const val MAX = 100
    private var appCtx: Context? = null
    private var sp: SharedPreferences? = null

    fun init(ctx: Context, profileId: String) {
        appCtx = ctx.applicationContext
        sp = appCtx?.getSharedPreferences("tvpp_history_$profileId", Context.MODE_PRIVATE)
    }

    fun setProfile(profileId: String) {
        sp = appCtx?.getSharedPreferences("tvpp_history_$profileId", Context.MODE_PRIVATE)
    }

    private fun prefs(): SharedPreferences = sp ?: throw IllegalStateException("HistoryManager.init() no llamado")

    fun save(entry: JSONObject) {
        val arr = loadArray()
        val key = entry.optString("key")
        val out = JSONArray()
        out.put(entry)
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optString("key") != key) out.put(e)
            if (out.length() >= MAX) break
        }
        prefs().edit().putString("hist", out.toString()).apply()
    }

    fun loadAll(): List<JSONObject> {
        val arr = loadArray()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    fun updatePosition(key: String, position: Long, duration: Long) {
        val arr = loadArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optString("key") == key) {
                e.put("position", position)
                e.put("duration", duration)
                e.put("ts", System.currentTimeMillis())
            }
            out.put(e)
        }
        if (out.length() == 0) return
        prefs().edit().putString("hist", out.toString()).apply()
    }

    /** Toca solo el timestamp (para canales en vivo al saltar de canal). */
    fun touchTs(key: String) {
        val arr = loadArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optString("key") == key) e.put("ts", System.currentTimeMillis())
            out.put(e)
        }
        if (out.length() == 0) return
        prefs().edit().putString("hist", out.toString()).apply()
    }

    fun remove(key: String) {
        val arr = loadArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optString("key") != key) out.put(e)
        }
        prefs().edit().putString("hist", out.toString()).apply()
    }

    private fun loadArray(): JSONArray {
        val raw = prefs().getString("hist", "[]") ?: "[]"
        return try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }
}