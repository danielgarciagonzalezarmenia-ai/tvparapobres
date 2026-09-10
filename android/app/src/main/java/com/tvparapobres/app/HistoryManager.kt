package com.tvparapobres.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Historial de reproducción persistente.
 * Cada entrada: {key, name, logo, type, url, position, duration, ts}
 * key = "live:{id}" | "vod:{id}" | "ser:{epId}"
 */
object HistoryManager {
    private const val MAX = 100
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("tvpp_history", Context.MODE_PRIVATE)
    }

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
        sp.edit().putString("hist", out.toString()).apply()
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
        sp.edit().putString("hist", out.toString()).apply()
    }

    fun remove(key: String) {
        val arr = loadArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optString("key") != key) out.put(e)
        }
        sp.edit().putString("hist", out.toString()).apply()
    }

    private fun loadArray(): JSONArray {
        val raw = sp.getString("hist", "[]") ?: "[]"
        return try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }
}