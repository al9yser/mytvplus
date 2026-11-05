package com.mytv.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Section<T> {
    var cats by mutableStateOf<List<Category>>(emptyList())
    var items by mutableStateOf<List<T>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loaded = false

    fun reset() {
        cats = emptyList()
        items = emptyList()
        loading = false
        error = null
        loaded = false
    }
}

class MainVm(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    var account by mutableStateOf(prefs.load())
    var info by mutableStateOf<AccountInfo?>(null)
    var loginBusy by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)

    val live = Section<Channel>()
    val movies = Section<Movie>()
    val series = Section<Series>()

    var play by mutableStateOf<PlayRequest?>(null)
    var seriesOpen by mutableStateOf<Series?>(null)

    init {
        val a = account
        if (a != null) {
            viewModelScope.launch {
                try {
                    info = withContext(Dispatchers.IO) { XtreamApi(a).login() }
                } catch (e: Exception) {
                    // تجاهل: سيظهر الخطأ عند تحميل القوائم
                }
            }
        }
    }

    fun login(acc: Account) {
        if (loginBusy) return
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            try {
                val i = withContext(Dispatchers.IO) { XtreamApi(acc).login() }
                prefs.save(acc)
                live.reset(); movies.reset(); series.reset()
                info = i
                account = acc
            } catch (e: Exception) {
                loginError = e.message ?: e.javaClass.simpleName
            } finally {
                loginBusy = false
            }
        }
    }

    fun logout() {
        prefs.clear()
        live.reset(); movies.reset(); series.reset()
        info = null
        account = null
    }

    fun loadLive() = load(live, "get_live_categories") { it.liveChannels() }
    fun loadMovies() = load(movies, "get_vod_categories") { it.movies() }
    fun loadSeries() = load(series, "get_series_categories") { it.seriesList() }

    private fun <T> load(s: Section<T>, catAction: String, items: (XtreamApi) -> List<T>) {
        val a = account ?: return
        if (s.loaded || s.loading) return
        s.loading = true
        s.error = null
        viewModelScope.launch {
            try {
                val api = XtreamApi(a)
                val result = withContext(Dispatchers.IO) { Pair(api.categories(catAction), items(api)) }
                s.cats = result.first
                s.items = result.second
                s.loaded = true
            } catch (e: Exception) {
                s.error = e.message ?: e.javaClass.simpleName
            } finally {
                s.loading = false
            }
        }
    }
}
