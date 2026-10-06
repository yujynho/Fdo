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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.example.network.MediaUrlValidator

@OptIn(UnstableApi::class)
object PlayerFactory {

    const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    fun createPlayer(context: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setAllowedVideoJoiningTimeMs(5000)

        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setLoadControl(loadControl)
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
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)

        val customHeaders = HashMap<String, String>()
        headers.forEach { (k, v) ->
            if (!k.equals("User-Agent", ignoreCase = true)) customHeaders[k] = v
        }
        if (customHeaders.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(customHeaders)
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
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
