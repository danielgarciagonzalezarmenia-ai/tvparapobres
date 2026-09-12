package com.tvparapobres.app

import android.content.Context
import android.graphics.Color
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Perfiles tipo Netflix: nombre, color de acento y PIN opcional.
 * Persistencia: tvp_profiles (lista) + tvp_active_profile + tvp_session.
 * Mismo esquema que la web (client/src/profiles.js) para paridad de funciones.
 */
object Profiles {
    const val DEFAULT_COLOR = "#ff4d2e"

    data class Profile(
        val id: String,
        val name: String,
        val color: String,
        val pin: String,
        val avatar: Int = 0,
        val photo: String = ""
    )

    private const val KEY_LIST = "tvp_profiles"
    private const val KEY_ACTIVE = "tvp_active_profile"
    private const val KEY_SESSION = "tvp_session_pid"

    private lateinit var sp: SharedPreferences
    private var cached: List<Profile>? = null

    val PALETTE = listOf(
        "#ff4d2e", "#e5484d", "#e93d82", "#8e4ec6",
        "#3e63dd", "#12b5a5", "#3fb950", "#f5a623"
    )

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("tvpp_profiles", Context.MODE_PRIVATE)
    }

    private fun loadRaw(): List<JSONObject> {
        val raw = sp.getString(KEY_LIST, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun profiles(): List<Profile> {
        cached?.let { if (cached!!.isNotEmpty() || loadRaw().isEmpty()) return it }
        val list = loadRaw().mapNotNull { j ->
            val id = j.optString("id", "")
            val name = j.optString("name", "").take(18)
            if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
            val av = j.optInt("avatar", 0).coerceIn(0, 3)
            val ph = j.optString("photo", "")
            Profile(
                id, name, j.optString("color", DEFAULT_COLOR),
                j.optString("pin", ""), av,
                if (ph.startsWith("data:image")) ph.take(300000) else ""
            )
        }
        cached = list
        return list
    }

    fun save(profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("color", p.color)
                put("pin", p.pin)
                put("avatar", p.avatar)
                put("photo", p.photo)
            })
        }
        sp.edit().putString(KEY_LIST, arr.toString()).apply()
        cached = profiles
    }

    fun add(name: String, color: String, pin: String, avatar: Int, photo: String): String {
        val p = Profile(newId(), name, color, pin, avatar.coerceIn(0, 3), photo)
        save(profiles() + p)
        return p.id
    }

    fun update(id: String, name: String, color: String, pin: String, avatar: Int, photo: String) {
        save(profiles().map { x ->
            if (x.id == id) x.copy(name = name, color = color, pin = pin, avatar = avatar.coerceIn(0, 3), photo = photo)
            else x
        })
    }

    fun remove(id: String) {
        save(profiles().filter { it.id != id })
        if (activeId() == id) sp.edit().remove(KEY_ACTIVE).apply()
        if (sessionId() == id) sp.edit().remove(KEY_SESSION).apply()
    }

    fun detail(id: String): Profile? = profiles().find { it.id == id }

    private var seq = System.currentTimeMillis()
    private fun newId() = "p${(seq++).toString(36).replace(Regex("[^a-z0-9]"), "")}"

    fun activeId(): String = sp.getString(KEY_ACTIVE, "") ?: ""
    fun setActive(id: String) = sp.edit().putString(KEY_ACTIVE, id).apply()

    fun sessionId(): String {
        val id = sp.getString(KEY_SESSION, "") ?: ""
        if (id.isEmpty()) return ""
        return if (detail(id) != null) id else ""
    }

    fun remember(id: String) = sp.edit().putString(KEY_SESSION, id).apply()
    fun forget() = sp.edit().remove(KEY_SESSION).apply()

    fun accentArgb(color: String?): Int {
        val c = color ?: DEFAULT_COLOR
        return try {
            Color.parseColor(if (c.startsWith("#") && c.length == 7) c else DEFAULT_COLOR)
        } catch (_: Exception) {
            Color.parseColor(DEFAULT_COLOR)
        }
    }

    fun accentArgb(): Int = accentArgb(detail(sessionId())?.color)
}