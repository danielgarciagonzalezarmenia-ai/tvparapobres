package com.tvparapobres.app

import java.util.concurrent.atomic.AtomicInteger

/**
 * Cuentas Xtream con rotación round-robin para repartir las peticiones
 * y evitar que el proveedor bloquee una sola cuenta por exceso de uso.
 */
object Accounts {
    val SERVER = "http://superxlatino.com:8880"

    private val list = listOf(
        "iuxaqxztom|WZj8wSMmqrHq",
        "yosoy1818|Tn4TbmANNf8H",
        "iocbrxekmj|JEU5r6cpkmHD",
        "YCf3YYJN38NT|bUGjmfRRraJm",
        "franco92372028|dbm9YKwtTwe4",
        "Manuel123|EjDk8kXpvYcm",
        "TSLHchEW792w|kXUnURxsKxSQ",
        "Barber|UcxVLfq8MNcg",
        "7Mnrg2AbeVwf|zQnj2mZ2YFYk",
        "cala|Ms2b4TXRbk4f",
        "demosanri|LjmPehJmmn2Q"
    )

    private val idx = AtomicInteger(0)

    data class Cred(val user: String, val pass: String)

    fun next(): Cred {
        val i = Math.floorMod(idx.getAndIncrement(), list.size)
        val parts = list[i].split("|")
        return Cred(parts[0], parts[1])
    }

    /** Cuenta estable para chequear servicio/vencimiento (no rota). */
    fun stable(): Cred {
        val parts = list[0].split("|")
        return Cred(parts[0], parts[1])
    }

    fun liveUrl(id: String): String {
        val c = next()
        return "$SERVER/${c.user}/${c.pass}/$id"
    }

    fun movieUrl(id: String, ext: String): String {
        val c = next()
        return "$SERVER/movie/${c.user}/${c.pass}/$id${ext.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""}"
    }

    fun seriesUrl(id: String, ext: String): String {
        val c = next()
        return "$SERVER/series/${c.user}/${c.pass}/$id.$ext"
    }
}