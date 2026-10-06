package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.network.MediaUrlValidator
import java.io.File

@OptIn(UnstableApi::class)
object PlayerFactory {

    const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Synchronized
    private fun getCache(context: Context): SimpleCache {
        return simpleCache ?: run {
            val cacheDir = File(context.applicationContext.cacheDir, "exoplayer_media_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(64L * 1024L * 1024L) // 64MB LRU ring cache
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            SimpleCache(cacheDir, evictor, databaseProvider).also { simpleCache = it }
        }
    }

    fun createPlayer(context: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_000, // Faster 1.0s instant startup
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setAllowedVideoJoiningTimeMs(4000)

        // Viewport-aware track selector for GPU and battery optimization
        val metrics = context.resources.displayMetrics
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(
                    metrics.widthPixels.coerceAtLeast(1920),
                    metrics.heightPixels.coerceAtLeast(1080)
                )
                .setViewportSize(metrics.widthPixels, metrics.heightPixels, false)
                .build()
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.EXACT)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
                playWhenReady = true
            }
    }

    fun buildMediaSource(context: Context, rawUrl: String, headers: Map<String, String> = emptyMap()): MediaSource {
        val url = rawUrl.trim()
        val userAgent = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value ?: DEFAULT_UA

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(15000)
            .setKeepPostFor302Redirects(true)

        val customHeaders = HashMap<String, String>()
        headers.forEach { (k, v) ->
            if (!k.equals("User-Agent", ignoreCase = true)) customHeaders[k] = v
        }
        if (customHeaders.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(customHeaders)
        }

        val upstreamFactory = DefaultDataSource.Factory(context, httpFactory)

        // Wrap with CacheDataSource for instant scrubbing and bandwidth preservation
        val cache = getCache(context)
        val cachedDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(cachedDataSourceFactory)
        val mediaItemBuilder = MediaItem.Builder().setUri(url)

        val ext = MediaUrlValidator.mediaExtensionOf(url)
        when {
            ext == "mpd" || url.contains(".mpd", ignoreCase = true) -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            ext == "m3u8" || url.contains(".m3u8", ignoreCase = true) -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            ext == "mkv" || url.contains(".mkv", ignoreCase = true) -> mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MATROSKA)
            ext == "webm" || url.contains(".webm", ignoreCase = true) -> mediaItemBuilder.setMimeType(MimeTypes.VIDEO_WEBM)
            ext == "ts" || url.contains(".ts", ignoreCase = true) -> mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
            ext == "mp4" -> mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
        }

        return mediaSourceFactory.createMediaSource(mediaItemBuilder.build())
    }

    fun openInExternalPlayer(context: Context, url: String, title: String? = null, headers: Map<String, String> = emptyMap()) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            Toast.makeText(context, "No stream URL available", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(trimmed), "video/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION

                if (!title.isNullOrBlank()) {
                    putExtra("title", title)
                    putExtra(Intent.EXTRA_TITLE, title)
                }

                if (headers.isNotEmpty()) {
                    val bundle = Bundle()
                    headers.forEach { (k, v) -> bundle.putString(k, v) }
                    putExtra("headers", bundle)
                    putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
                }
            }
            context.startActivity(Intent.createChooser(intent, "Play with external player..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to open external player: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
