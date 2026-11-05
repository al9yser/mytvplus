package com.mytv.player

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(req: PlayRequest, ua: String, api: XtreamApi, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var idx by remember(req) { mutableIntStateOf(0) }
    var attempt by remember(req) { mutableIntStateOf(0) }
    var error by remember(req) { mutableStateOf<String?>(null) }
    var epgText by remember(req) { mutableStateOf("") }
    var showInfo by remember(req) { mutableStateOf(true) }

    val exo = remember(req) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
    }

    LaunchedEffect(exo, idx, attempt) {
        error = null
        exo.setMediaItem(MediaItem.fromUri(req.urls[idx]))
        exo.prepare()
        exo.playWhenReady = true
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                val cause = e.cause
                val msg = if (cause is HttpDataSource.InvalidResponseCodeException) {
                    "رد السيرفر: ${cause.responseCode}"
                } else {
                    e.errorCodeName
                }
                if (idx + 1 < req.urls.size) idx += 1 else error = msg
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    LaunchedEffect(req) {
        delay(6000)
        showInfo = false
    }

    LaunchedEffect(req) {
        val id = req.liveId ?: return@LaunchedEffect
        try {
            val list = withContext(Dispatchers.IO) { api.epg(id) }
            epgText = list.firstOrNull()?.title ?: ""
        } catch (e: Exception) {
            // لا يوجد دليل برامج لهذه القناة
        }
    }

    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    player = exo
                    useController = true
                    keepScreenOn = true
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    isFocusable = true
                    requestFocus()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (showInfo) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color(0x99000000))
                    .padding(10.dp)
            ) {
                Text(req.title, color = Color.White, fontSize = 18.sp)
                if (epgText.isNotBlank()) Text(epgText, color = Color(0xFFFFC107), fontSize = 14.sp)
            }
        }

        val err = error
        if (err != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("تعذر التشغيل", color = Color.White, fontSize = 20.sp)
                Text(err, color = Color(0xFFFFC107), fontSize = 16.sp, modifier = Modifier.padding(8.dp))
                Row {
                    Button(onClick = { idx = 0; attempt += 1 }) { Text("إعادة المحاولة") }
                    Box(Modifier.width(12.dp))
                    Button(onClick = onClose) { Text("رجوع") }
                }
            }
        }
    }
}
