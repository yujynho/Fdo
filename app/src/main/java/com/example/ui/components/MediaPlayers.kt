package com.example.ui.components

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.network.StreamQuality
import com.example.network.SubtitleTrack
import com.example.ui.theme.LocalAccentColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlayerMode {
    FULLSCREEN,
    INLINE_CARD
}

/**
 * Unified Modern High-Performance Media Player Engine
 */
@OptIn(UnstableApi::class)
@Composable
fun CoreMediaPlayer(
    mode: PlayerMode,
    title: String,
    qualities: List<StreamQuality>,
    defaultHeaders: Map<String, String> = emptyMap(),
    initialPositionMs: Long = 0L,
    startInLandscape: Boolean = false,
    exoPlayer: ExoPlayer? = null,
    enableGestures: Boolean = true,
    onClose: () -> Unit,
    onFullscreen: ((currentPositionMs: Long) -> Unit)? = null,
    onFullscreenWithMode: ((currentPositionMs: Long, startInLandscape: Boolean) -> Unit)? = null,
    onEnterPip: ((currentPositionMs: Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isFullscreen = mode == PlayerMode.FULLSCREEN

    val fallbackPlayer = remember(context) {
        if (exoPlayer == null) PlayerFactory.createPlayer(context) else null
    }
    val activeExoPlayer = exoPlayer ?: fallbackPlayer!!

    DisposableEffect(fallbackPlayer) {
        onDispose { fallbackPlayer?.release() }
    }

    val primaryQuality = remember(qualities) {
        qualities.firstOrNull { it.isDefault } ?: qualities.firstOrNull()
    }

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPos by remember(primaryQuality?.url) {
        mutableLongStateOf(initialPositionMs.coerceAtLeast(0L))
    }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPos by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }

    if (isFullscreen) {
        LaunchedEffect(startInLandscape) {
            if (startInLandscape) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var errorDetails by remember { mutableStateOf<String?>(null) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }

    var isGestureSeeking by remember { mutableStateOf(false) }

    val audioManager = remember(context) {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    val maxAudioVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    var gestureVolumePercent by remember { mutableStateOf<Int?>(null) }
    var gestureBrightnessPercent by remember { mutableStateOf<Int?>(null) }
    var volumeHideJob by remember { mutableStateOf<Job?>(null) }
    var brightnessHideJob by remember { mutableStateOf<Job?>(null) }

    var isInPipMode by remember {
        mutableStateOf(activity?.isInPictureInPictureMode == true)
    }

    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
            if (info.isInPictureInPictureMode) {
                showControls = false
            }
        }
        val compAct = activity as? ComponentActivity
        compAct?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            compAct?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    val handleBackAction = {
        if (isFullscreen && isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else if (!isInPipMode) {
            onClose()
        }
    }

    if (isFullscreen) {
        BackHandler { handleBackAction() }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4500)
            showControls = false
        }
    }

    var isRealHdrStream by remember { mutableStateOf(false) }

    // Platform effects (System bars, brightness, wide color gamut)
    ManageFullscreenWindowEffects(
        activity = activity,
        isFullscreen = isFullscreen,
        isRealHdrStream = isRealHdrStream
    )

    // Lifecycle binding
    val lifecycleOwner = LocalLifecycleOwner.current
    BindPlayerLifecycle(lifecycleOwner, activeExoPlayer)

    val activeHeaders = remember(primaryQuality, defaultHeaders) {
        val streamHeaders = primaryQuality?.headers ?: emptyMap()
        defaultHeaders + streamHeaders
    }

    // Player state & format listener
    BindPlayerStateListener(
        player = activeExoPlayer,
        onStateChanged = { buffering, playing, dur ->
            isBuffering = buffering
            isPlaying = playing
            if (dur > 0) duration = dur
        },
        onFormatChanged = {
            isRealHdrStream = checkIsVideoRealHdr(activeExoPlayer)
        },
        onError = { title, details ->
            isBuffering = false
            errorMessage = title
            errorDetails = details
        },
        onClearError = {
            errorMessage = null
            errorDetails = null
        }
    )

    // Adaptive position tracking
    TrackPlayerPositions(
        player = activeExoPlayer,
        showControls = showControls,
        isScrubbing = isScrubbing,
        isGestureSeeking = isGestureSeeking,
        lastSeekTime = lastSeekTime,
        primaryQuality = primaryQuality,
        onTick = { dur, buf, pos ->
            if (dur > 0) duration = dur
            bufferedPos = buf
            if (pos >= 0) currentPos = pos
        }
    )

    // Media preparation
    PreparePlayerMediaSource(
        context = context,
        player = activeExoPlayer,
        primaryQuality = primaryQuality,
        activeHeaders = activeHeaders,
        initialPositionMs = initialPositionMs,
        onBufferingChanged = { isBuffering = it },
        onDurationChanged = { duration = it },
        onPositionChanged = { currentPos = it },
        onError = { title, details ->
            isBuffering = false
            errorMessage = title
            errorDetails = details
        },
        onClearError = {
            errorMessage = null
            errorDetails = null
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .playerTouchGestures(
                duration = duration,
                enableGestures = enableGestures,
                currentPositionProvider = { activeExoPlayer.currentPosition },
                audioManager = audioManager,
                maxAudioVolume = maxAudioVolume,
                activity = activity,
                isScrubbing = isScrubbing,
                onGestureSeekingChanged = { isGestureSeeking = it },
                onSeekLive = { target ->
                    currentPos = target
                    lastSeekTime = System.currentTimeMillis()
                },
                onSeekFinal = { target ->
                    currentPos = target
                    lastSeekTime = System.currentTimeMillis()
                    isBuffering = true
                    activeExoPlayer.seekTo(target)
                },
                onVolumeChanged = { percent ->
                    gestureVolumePercent = percent
                    if (percent == null) {
                        volumeHideJob = coroutineScope.launch {
                            delay(800)
                            gestureVolumePercent = null
                        }
                    } else {
                        volumeHideJob?.cancel()
                    }
                },
                onBrightnessChanged = { percent ->
                    gestureBrightnessPercent = percent
                    if (percent == null) {
                        brightnessHideJob = coroutineScope.launch {
                            delay(800)
                            gestureBrightnessPercent = null
                        }
                    } else {
                        brightnessHideJob?.cancel()
                    }
                },
                onDoubleTap = { isForward ->
                    if (isForward) {
                        val target = (activeExoPlayer.currentPosition + 10000).coerceAtMost(duration).coerceAtLeast(0L)
                        isBuffering = true
                        activeExoPlayer.seekTo(target)
                        currentPos = target
                    } else {
                        val target = (activeExoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                        isBuffering = true
                        activeExoPlayer.seekTo(target)
                        currentPos = target
                    }
                    lastSeekTime = System.currentTimeMillis()
                },
                onSingleTap = {
                    showControls = !showControls
                }
            )
            .testTag(if (isFullscreen) "video_player_overlay" else "inline_video_player")
    ) {
        // Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = activeExoPlayer
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                if (playerView.player != activeExoPlayer) {
                    playerView.player = activeExoPlayer
                }
                playerView.keepScreenOn = true
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Animation - Prominent centered Lottie loader
        if (isBuffering && errorMessage == null) {
            AppLottieLoadingAnimation(
                modifier = Modifier
                    .size(if (isFullscreen) 92.dp else 70.dp)
                    .align(Alignment.Center)
            )
        }

        // Error Dialog
        if (errorMessage != null && !isInPipMode) {
            PlayerErrorDialog(
                errorMessage = errorMessage ?: "Playback Error",
                errorDetails = errorDetails,
                onRetry = {
                    errorMessage = null
                    errorDetails = null
                    isBuffering = true
                    activeExoPlayer.prepare()
                    activeExoPlayer.play()
                },
                onOpenExternal = {
                    openExternalVideoPlayer(context, primaryQuality?.url, title, activeHeaders)
                }
            )
        }

        // Side Indicators (Volume & Brightness)
        val overlaySidePadding = if (isFullscreen && isLandscape) 32.dp else 16.dp
        val volumePercent = gestureVolumePercent ?: 0
        VerticalSideBarIndicator(
            visible = gestureVolumePercent != null,
            percent = volumePercent,
            painter = painterResource(
                id = if (volumePercent == 0) R.drawable.ic_gesture_volume_mute else R.drawable.ic_gesture_volume_up
            ),
            barHeight = if (isFullscreen && isLandscape) 160.dp else 110.dp,
            sidePadding = overlaySidePadding,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        VerticalSideBarIndicator(
            visible = gestureBrightnessPercent != null,
            percent = gestureBrightnessPercent ?: 0,
            painter = painterResource(id = R.drawable.ic_gesture_brightness),
            barHeight = if (isFullscreen && isLandscape) 160.dp else 110.dp,
            sidePadding = overlaySidePadding,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // Overlay Controls
        PlayerControlsOverlay(
            visible = showControls && errorMessage == null && !isInPipMode,
            isFullscreen = isFullscreen,
            isLandscape = isLandscape,
            title = title,
            isPlaying = isPlaying,
            currentPos = currentPos,
            bufferedPos = bufferedPos,
            duration = duration,
            isBuffering = isBuffering,
            is4kOrHdr = isRealHdrStream,
            onBack = handleBackAction,
            onRewind10s = {
                if (duration > 0 && duration != C.TIME_UNSET) {
                    val target = (activeExoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                    isBuffering = true
                    activeExoPlayer.seekTo(target)
                    currentPos = target
                } else {
                    activeExoPlayer.seekBack()
                }
                lastSeekTime = System.currentTimeMillis()
            },
            onTogglePlayPause = {
                if (activeExoPlayer.isPlaying) {
                    activeExoPlayer.pause()
                    isPlaying = false
                    isBuffering = false
                } else {
                    activeExoPlayer.play()
                    isPlaying = true
                    if (activeExoPlayer.playbackState != androidx.media3.common.Player.STATE_READY) {
                        isBuffering = true
                    }
                }
            },
            onForward10s = {
                if (duration > 0 && duration != C.TIME_UNSET) {
                    val target = (activeExoPlayer.currentPosition + 10000).coerceAtMost(duration).coerceAtLeast(0L)
                    isBuffering = true
                    activeExoPlayer.seekTo(target)
                    currentPos = target
                } else {
                    activeExoPlayer.seekForward()
                }
                lastSeekTime = System.currentTimeMillis()
            },
            onEnterPip = {
                triggerPipMode(
                    context = context,
                    activity = activity,
                    player = activeExoPlayer,
                    currentPos = currentPos,
                    onCustomPip = onEnterPip,
                    onBeforeEnter = { showControls = false }
                )
            },
            onOpenExternal = {
                openExternalVideoPlayer(context, primaryQuality?.url, title, activeHeaders)
            },
            onToggleFullscreen = {
                if (isFullscreen) {
                    toggleScreenOrientation(activity, isLandscape)
                } else {
                    if (onFullscreenWithMode != null) {
                        onFullscreenWithMode(currentPos, true)
                    } else {
                        onFullscreen?.invoke(currentPos)
                    }
                }
            },
            onScrubbingChanged = { isScrubbing = it },
            onSeekLive = { target ->
                currentPos = target
                lastSeekTime = System.currentTimeMillis()
            },
            onSeekFinal = { target ->
                currentPos = target
                lastSeekTime = System.currentTimeMillis()
                isBuffering = true
                activeExoPlayer.seekTo(target)
            }
        )
    }
}

/**
 * Fullscreen Video Player Overlay
 */
@Composable
fun GoPlayer(
    title: String,
    qualities: List<StreamQuality>,
    subtitles: List<SubtitleTrack> = emptyList(),
    defaultHeaders: Map<String, String> = emptyMap(),
    initialPositionMs: Long = 0L,
    startInLandscape: Boolean = false,
    exoPlayer: ExoPlayer? = null,
    onClose: () -> Unit
) {
    CoreMediaPlayer(
        mode = PlayerMode.FULLSCREEN,
        title = title,
        qualities = qualities,
        defaultHeaders = defaultHeaders,
        initialPositionMs = initialPositionMs,
        startInLandscape = startInLandscape,
        exoPlayer = exoPlayer,
        onClose = onClose
    )
}

@Composable
fun ExoPlayerOverlay(
    title: String,
    qualities: List<StreamQuality>,
    subtitles: List<SubtitleTrack> = emptyList(),
    defaultHeaders: Map<String, String> = emptyMap(),
    initialPositionMs: Long = 0L,
    startInLandscape: Boolean = false,
    exoPlayer: ExoPlayer? = null,
    onClose: () -> Unit
) {
    GoPlayer(
        title = title,
        qualities = qualities,
        subtitles = subtitles,
        defaultHeaders = defaultHeaders,
        initialPositionMs = initialPositionMs,
        startInLandscape = startInLandscape,
        exoPlayer = exoPlayer,
        onClose = onClose
    )
}

/**
 * Embedded 16:9 Inline Video Player for Card Covers
 */
@Composable
fun InlineCardPlayer(
    title: String,
    qualities: List<StreamQuality>,
    subtitles: List<SubtitleTrack> = emptyList(),
    defaultHeaders: Map<String, String> = emptyMap(),
    exoPlayer: ExoPlayer? = null,
    enableGestures: Boolean = true,
    onClose: () -> Unit,
    onFullscreen: (currentPositionMs: Long) -> Unit,
    onFullscreenWithMode: ((currentPositionMs: Long, startInLandscape: Boolean) -> Unit)? = null,
    onEnterPip: ((currentPositionMs: Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    CoreMediaPlayer(
        mode = PlayerMode.INLINE_CARD,
        title = title,
        qualities = qualities,
        defaultHeaders = defaultHeaders,
        exoPlayer = exoPlayer,
        enableGestures = enableGestures,
        onClose = onClose,
        onFullscreen = onFullscreen,
        onFullscreenWithMode = onFullscreenWithMode,
        onEnterPip = onEnterPip,
        modifier = modifier
    )
}
