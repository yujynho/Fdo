package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun PlayerControlsOverlay(
    visible: Boolean,
    isFullscreen: Boolean,
    isLandscape: Boolean,
    title: String,
    isPlaying: Boolean,
    currentPos: Long,
    bufferedPos: Long,
    duration: Long,
    isBuffering: Boolean = false,
    is4kOrHdr: Boolean = false,
    onBack: () -> Unit,
    onRewind10s: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onForward10s: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenExternal: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onSeekLive: (targetMs: Long) -> Unit,
    onSeekFinal: (targetMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.75f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        ) {
            // Top Bar
            if (isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (is4kOrHdr) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.75f))
                            ) {
                                Text(
                                    text = "4K HDR",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.20f), CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = onBack)
                            .testTag("back_player_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_back),
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(38.dp)
                        .background(Color.White.copy(alpha = 0.20f), CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("back_inline_player"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_back),
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Center 3-Button Controls (10s Rewind, Play/Pause, 10s Forward) - Hidden during Buffering
            AnimatedVisibility(
                visible = !isBuffering,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                val centerIconSize = if (isFullscreen) 52.dp else 42.dp
                val centerGap = if (isFullscreen) 40.dp else 18.dp

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(centerGap)
                ) {
                    IconButton(
                        onClick = onRewind10s,
                        modifier = Modifier.size(centerIconSize)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_rewind),
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(if (isFullscreen) 36.dp else 28.dp)
                        )
                    }

                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(centerIconSize)
                            .testTag(if (isFullscreen) "play_pause_button" else "inline_play_pause_button")
                    ) {
                        Icon(
                            painter = painterResource(id = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(if (isFullscreen) 38.dp else 30.dp)
                        )
                    }

                    IconButton(
                        onClick = onForward10s,
                        modifier = Modifier.size(centerIconSize)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_forward),
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(if (isFullscreen) 36.dp else 28.dp)
                        )
                    }
                }
            }

            // Bottom Timeline & Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                    .padding(horizontal = if (isFullscreen) 16.dp else 12.dp, vertical = if (isFullscreen) 8.dp else 6.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatPlayerDuration(currentPos),
                            color = Color.White,
                            fontSize = if (isFullscreen) 13.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = " / ${formatPlayerDuration(duration)}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = if (isFullscreen) 13.sp else 12.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 8.dp else 6.dp)
                    ) {
                        // Pop-Up Window / PiP Button
                        IconButton(
                            onClick = onEnterPip,
                            modifier = Modifier
                                .size(if (isFullscreen) 32.dp else 28.dp)
                                .testTag(if (isFullscreen) "popup_player_button" else "popup_inline_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_player_pip),
                                contentDescription = "Picture in Picture",
                                tint = Color.White,
                                modifier = Modifier.size(if (isFullscreen) 20.dp else 18.dp)
                            )
                        }

                        // External Player Button
                        IconButton(
                            onClick = onOpenExternal,
                            modifier = Modifier
                                .size(if (isFullscreen) 32.dp else 28.dp)
                                .testTag(if (isFullscreen) "open_external_player_button" else "external_player_inline_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_player_external),
                                contentDescription = "Open External",
                                tint = Color.White,
                                modifier = Modifier.size(if (isFullscreen) 20.dp else 18.dp)
                            )
                        }

                        // Fullscreen / Rotate Button
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier
                                .size(if (isFullscreen) 32.dp else 28.dp)
                                .testTag(if (isFullscreen) "fullscreen_toggle_button" else "fullscreen_inline_button")
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isFullscreen) {
                                        if (isLandscape) R.drawable.ic_player_fullscreen_exit else R.drawable.ic_player_fullscreen
                                    } else {
                                        R.drawable.ic_player_fullscreen
                                    }
                                ),
                                contentDescription = "Fullscreen Mode",
                                tint = Color.White,
                                modifier = Modifier.size(if (isFullscreen) 22.dp else 20.dp)
                            )
                        }
                    }
                }

                PlayerTimelineScrubber(
                    currentPos = currentPos,
                    bufferedPos = bufferedPos,
                    duration = duration,
                    isFullscreen = isFullscreen,
                    onScrubbingChanged = onScrubbingChanged,
                    onSeekLive = onSeekLive,
                    onSeekFinal = onSeekFinal,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
