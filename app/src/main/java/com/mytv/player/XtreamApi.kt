package com.mytv.player

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class XtreamApi(private val acc: Account) {
    val base: String = normalizeHost(acc.host)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

    private fun fetch(action: String?, extra: String = ""): String {
        val sb = StringBuilder("$base/player_api.php?username=${enc(acc.user)}&password=${enc(acc.pass)}")
        if (action != null) sb.append("&action=").append(action)
        sb.append(extra)
        val conn = URL(sb.toString()).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 45000
        conn.setRequestProperty("User-Agent", acc.ua)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("رد السيرفر: HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun array(text: String): JSONArray =
        try {
            JSONArray(text)
        } catch (e: Exception) {
            JSONArray()
        }

    fun login(): AccountInfo {
        val text = fetch(null)
        val o = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IOException("رد غير متوقع من السيرفر")
        }
        val ui = o.optJSONObject("user_info") ?: throw IOException("بيانات الدخول غير صحيحة")
        if (ui.str("auth") != "1") throw IOException("بيانات الدخول غير صحيحة")
        val expSec = ui.str("exp_date").toLongOrNull()
        val exp = if (expSec == null) "غير محدد"
        else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(expSec * 1000))
        return AccountInfo(ui.str("status"), exp, ui.str("max_connections"))
    }

    fun categories(action: String): List<Category> {
        val a = array(fetch(action))
        val out = ArrayList<Category>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(Category(o.str("category_id"), o.str("category_name")))
        }
        return out
    }

    fun liveChannels(): List<Channel> {
        val a = array(fetch("get_live_streams"))
        val out = ArrayList<Channel>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(
                Channel(
                    o.str("stream_id"),
                    o.str("name"),
                    o.str("stream_icon").ifBlank { null },
                    o.str("category_id"),
                )
            )
        }
        return out
    }

    fun movies(): List<Movie> {
        val a = array(fetch("get_vod_streams"))
        val out = ArrayList<Movie>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(
                Movie(
                    o.str("stream_id"),
                    o.str("name"),
                    o.str("stream_icon").ifBlank { null },
                    o.str("category_id"),
                    o.str("container_extension").ifBlank { "mp4" },
                )
            )
        }
        return out
    }

    fun seriesList(): List<Series> {
        val a = array(fetch("get_series"))
        val out = ArrayList<Series>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(
                Series(
                    o.str("series_id"),
                    o.str("name"),
                    o.str("cover").ifBlank { null },
                    o.str("category_id"),
                )
            )
        }
        return out
    }

    fun seriesEpisodes(seriesId: String): List<Episode> {
        val o = JSONObject(fetch("get_series_info", "&series_id=$seriesId"))
        val out = ArrayList<Episode>()

        fun addList(season: Int, a: JSONArray) {
            for (i in 0 until a.length()) {
                val e = a.optJSONObject(i) ?: continue
                val num = e.optInt("episode_num", i + 1)
                val title = e.str("title").ifBlank { "حلقة $num" }
                out.add(
                    Episode(
                        e.str("id"),
                        title,
                        e.optInt("season", season),
                        num,
                        e.str("container_extension").ifBlank { "mp4" },
                    )
                )
            }
        }

        val eps = o.opt("episodes")
        if (eps is JSONObject) {
            val keys = eps.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                addList(k.toIntOrNull() ?: 0, eps.optJSONArray(k) ?: JSONArray())
            }
        } else if (eps is JSONArray) {
            for (i in 0 until eps.length()) {
                val s = eps.optJSONArray(i) ?: continue
                addList(i + 1, s)
            }
        }
        return out.sortedWith(compareBy<Episode>({ it.season }, { it.num }))
    }

    fun epg(streamId: String): List<EpgItem> {
        val o = JSONObject(fetch("get_short_epg", "&stream_id=$streamId&limit=3"))
        val a = o.optJSONArray("epg_listings") ?: return emptyList()
        val out = ArrayList<EpgItem>()
        for (i in 0 until a.length()) {
            val e = a.optJSONObject(i) ?: continue
            out.add(
                EpgItem(
                    decode(e.str("title")),
                    e.str("start_timestamp").toLongOrNull() ?: 0L,
                    e.str("stop_timestamp").toLongOrNull() ?: 0L,
                )
            )
        }
        return out
    }

    private fun decode(s: String): String =
        try {
            String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            s
        }

    fun liveUrls(id: String): List<String> = listOf(
        "$base/live/${enc(acc.user)}/${enc(acc.pass)}/$id.ts",
        "$base/live/${enc(acc.user)}/${enc(acc.pass)}/$id.m3u8",
    )

    fun movieUrl(id: String, ext: String): List<String> =
        listOf("$base/movie/${enc(acc.user)}/${enc(acc.pass)}/$id.$ext")

    fun episodeUrl(id: String, ext: String): List<String> =
        listOf("$base/series/${enc(acc.user)}/${enc(acc.pass)}/$id.$ext")
}
