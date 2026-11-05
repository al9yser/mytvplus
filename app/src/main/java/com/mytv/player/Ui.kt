package com.mytv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Bg = Color(0xFF0E0F14)
val CardBg = Color(0xFF1B1D27)
val Gold = Color(0xFFFFC107)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            onPrimary = Color.Black,
            background = Bg,
            surface = CardBg,
        ),
    ) {
        Surface(color = Bg, contentColor = Color.White, content = content)
    }
}

/** إطار ذهبي عند التركيز بالريموت */
@Composable
fun Modifier.tvFocus(onFocus: () -> Unit = {}): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocus()
        }
        .border(
            width = if (focused) 3.dp else 0.dp,
            color = if (focused) Gold else Color.Transparent,
            shape = RoundedCornerShape(8.dp),
        )
}

@Composable
fun App(vm: MainVm) {
    val acc = vm.account
    Box(Modifier.fillMaxSize().background(Bg)) {
        if (acc == null) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("مشغّلي", fontSize = 32.sp, color = Gold)
                Spacer(Modifier.height(16.dp))
                AccountForm(vm, null, "دخول")
            }
        } else {
            val api = remember(acc) { XtreamApi(acc) }
            MainScreen(vm, api)
            val s = vm.seriesOpen
            if (s != null) SeriesDetail(vm, api, s)
            val p = vm.play
            if (p != null) PlayerScreen(p, acc.ua, api) { vm.play = null }
        }
    }
}

@Composable
fun AccountForm(vm: MainVm, initial: Account?, buttonText: String) {
    var host by remember(initial) { mutableStateOf(initial?.host ?: "http://") }
    var user by remember(initial) { mutableStateOf(initial?.user ?: "") }
    var pass by remember(initial) { mutableStateOf(initial?.pass ?: "") }
    var ua by remember(initial) { mutableStateOf(initial?.ua ?: DEFAULT_UA) }

    Column(Modifier.fillMaxWidth().widthIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = host, onValueChange = { host = it }, singleLine = true,
            label = { Text("عنوان السيرفر") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it }, singleLine = true,
            label = { Text("اسم المستخدم") }, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it }, singleLine = true,
            label = { Text("كلمة المرور") }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = ua, onValueChange = { ua = it }, singleLine = true,
            label = { Text("User-Agent") }, modifier = Modifier.fillMaxWidth(),
        )
        val err = vm.loginError
        if (err != null) Text(err, color = Color(0xFFFF6B6B))
        Button(
            onClick = {
                vm.login(Account(normalizeHost(host), user.trim(), pass.trim(), ua.trim().ifBlank { DEFAULT_UA }))
            },
            enabled = !vm.loginBusy && host.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.loginBusy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
            else Text(buttonText)
        }
    }
}

@Composable
fun MainScreen(vm: MainVm, api: XtreamApi) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("مباشر", "أفلام", "مسلسلات", "الإعدادات")

    LaunchedEffect(tab, vm.account) {
        when (tab) {
            0 -> vm.loadLive()
            1 -> vm.loadMovies()
            2 -> vm.loadSeries()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            titles.forEachIndexed { i, t ->
                Button(
                    onClick = { tab = i },
                    colors = if (tab == i) ButtonDefaults.buttonColors()
                    else ButtonDefaults.buttonColors(containerColor = CardBg, contentColor = Color.White),
                ) { Text(t) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> Browse(vm.live, { vm.loadLive() }, { it.categoryId }, { it.name }) { list ->
                    LiveList(list, api) { ch -> vm.play = PlayRequest(ch.name, api.liveUrls(ch.id), ch.id) }
                }
                1 -> Browse(vm.movies, { vm.loadMovies() }, { it.categoryId }, { it.name }) { list ->
                    PosterGrid(list.map { Triple(it.name, it.icon, it) }) { m ->
                        vm.play = PlayRequest(m.name, api.movieUrl(m.id, m.ext))
                    }
                }
                2 -> Browse(vm.series, { vm.loadSeries() }, { it.categoryId }, { it.name }) { list ->
                    PosterGrid(list.map { Triple(it.name, it.cover, it) }) { s -> vm.seriesOpen = s }
                }
                else -> SettingsTab(vm)
            }
        }
    }
}

@Composable
fun <T> Browse(
    section: Section<T>,
    onLoad: () -> Unit,
    catOf: (T) -> String,
    nameOf: (T) -> String,
    content: @Composable (List<T>) -> Unit,
) {
    var cat by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    if (section.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
        return
    }
    val err = section.error
    if (err != null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("تعذر التحميل", fontSize = 20.sp)
            Text(err, color = Gold, modifier = Modifier.padding(8.dp))
            Button(onClick = onLoad) { Text("إعادة المحاولة") }
        }
        return
    }

    val filtered = remember(section.items, cat, query) {
        section.items.filter {
            (cat.isEmpty() || catOf(it) == cat) &&
                (query.isBlank() || nameOf(it).contains(query, ignoreCase = true))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val categoryUi: @Composable () -> Unit = {
            val all = listOf(Category("", "الكل")) + section.cats
            if (wide) {
                LazyColumn(Modifier.width(200.dp).fillMaxHeight().padding(4.dp)) {
                    items(all) { c -> CatItem(c.name, cat == c.id) { cat = c.id } }
                }
            } else {
                LazyRow(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(all) { c -> CatItem(c.name, cat == c.id) { cat = c.id } }
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                categoryUi()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    SearchBox(query) { query = it }
                    Box(Modifier.weight(1f)) { content(filtered) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                categoryUi()
                SearchBox(query) { query = it }
                Box(Modifier.weight(1f)) { content(filtered) }
            }
        }
    }
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text("بحث...") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun CatItem(name: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        name,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (selected) Color.Black else Color.White,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .background(if (selected) Gold else CardBg, RoundedCornerShape(8.dp))
            .tvFocus()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
fun LiveList(list: List<Channel>, api: XtreamApi, onPlay: (Channel) -> Unit) {
    var focused by remember { mutableStateOf<Channel?>(null) }
    var epg by remember { mutableStateOf<List<EpgItem>>(emptyList()) }

    LaunchedEffect(focused) {
        epg = emptyList()
        val c = focused ?: return@LaunchedEffect
        delay(500)
        epg = try {
            withContext(Dispatchers.IO) { api.epg(c.id) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(list) { ch ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .background(CardBg, RoundedCornerShape(8.dp))
                        .tvFocus(onFocus = { focused = ch })
                        .clickable { onPlay(ch) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = ch.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                }
            }
        }
        val c = focused
        if (c != null) {
            Column(Modifier.fillMaxWidth().background(CardBg).padding(10.dp)) {
                Text(c.name, color = Gold, fontSize = 14.sp)
                val fmt = SimpleDateFormat("HH:mm", Locale.US)
                epg.forEachIndexed { i, e ->
                    val label = if (i == 0) "الآن" else "التالي"
                    val time = "${fmt.format(Date(e.start * 1000))} - ${fmt.format(Date(e.end * 1000))}"
                    Text("$label: ${e.title}  ($time)", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun <T> PosterGrid(entries: List<Triple<String, String?, T>>, onClick: (T) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(130.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
    ) {
        items(entries) { (name, img, obj) ->
            Column(
                Modifier
                    .padding(5.dp)
                    .tvFocus()
                    .clickable { onClick(obj) }
                    .padding(3.dp)
            ) {
                AsyncImage(
                    model = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardBg),
                )
                Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun SeriesDetail(vm: MainVm, api: XtreamApi, series: Series) {
    var episodes by remember(series) { mutableStateOf<List<Episode>?>(null) }
    var error by remember(series) { mutableStateOf<String?>(null) }
    var season by remember(series) { mutableIntStateOf(-1) }

    LaunchedEffect(series) {
        try {
            val list = withContext(Dispatchers.IO) { api.seriesEpisodes(series.id) }
            episodes = list
            season = list.firstOrNull()?.season ?: 0
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    BackHandler { vm.seriesOpen = null }

    Column(Modifier.fillMaxSize().background(Bg).padding(12.dp)) {
        Text(series.name, fontSize = 22.sp, color = Gold)
        Spacer(Modifier.height(8.dp))
        val eps = episodes
        when {
            error != null -> Text("تعذر التحميل: $error")
            eps == null -> CircularProgressIndicator(color = Gold)
            eps.isEmpty() -> Text("لا توجد حلقات")
            else -> {
                val seasons = eps.map { it.season }.distinct()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(seasons) { s ->
                        CatItem("الموسم $s", season == s) { season = s }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(eps.filter { it.season == season }) { e ->
                        Text(
                            "${e.num}. ${e.title}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(CardBg, RoundedCornerShape(8.dp))
                                .tvFocus()
                                .clickable {
                                    vm.play = PlayRequest(
                                        "${series.name} - ${e.title}",
                                        api.episodeUrl(e.id, e.ext),
                                    )
                                }
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(vm: MainVm) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val i = vm.info
        if (i != null) {
            Text("حالة الحساب: ${i.status}")
            Text("ينتهي في: ${i.expires}")
            Text("عدد الاتصالات المسموح: ${i.maxConn}")
        }
        AccountForm(vm, vm.account, "حفظ وإعادة التحميل")
        Button(
            onClick = { vm.logout() },
            colors = ButtonDefaults.buttonColors(containerColor = CardBg, contentColor = Color.White),
        ) { Text("تسجيل الخروج") }
    }
}
