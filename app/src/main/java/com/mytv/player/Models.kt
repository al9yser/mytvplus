package com.mytv.player

const val DEFAULT_UA = "com.smart.boxtv/53"

data class Account(val host: String, val user: String, val pass: String, val ua: String)

data class AccountInfo(val status: String, val expires: String, val maxConn: String)

data class Category(val id: String, val name: String)

data class Channel(val id: String, val name: String, val icon: String?, val categoryId: String)

data class Movie(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String,
    val ext: String,
)

data class Series(val id: String, val name: String, val cover: String?, val categoryId: String)

data class Episode(val id: String, val title: String, val season: Int, val num: Int, val ext: String)

data class EpgItem(val title: String, val start: Long, val end: Long)

/** urls: عناوين بديلة تُجرَّب بالترتيب عند الفشل. liveId: رقم القناة لعرض EPG */
data class PlayRequest(val title: String, val urls: List<String>, val liveId: String? = null)

fun normalizeHost(h: String): String {
    var s = h.trim().trimEnd('/')
    if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
    return s
}
