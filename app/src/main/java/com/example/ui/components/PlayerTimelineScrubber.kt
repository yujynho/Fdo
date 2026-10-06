package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import com.example.ui.theme.LocalAccentColor

fun formatPlayerDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun PlayerTimelineScrubber(
    currentPos: Long,
    bufferedPos: Long,
    duration: Long,
    isFullscreen: Boolean,
    onScrubbingChanged: (Boolean) -> Unit,
    onSeekLive: (targetMs: Long) -> Unit,
    onSeekFinal: (targetMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var scrubberWidthPx by remember { mutableFloatStateOf(1f) }

    val effectiveFraction = if (isScrubbing) scrubProgress else {
        if (duration > 0) (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    }
    val bufferedFraction = if (duration > 0) (bufferedPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val scrubTimeMs = (effectiveFraction * duration).toLong()

    Column(
        modifier = modifier.padding(vertical = if (isFullscreen) 4.dp else 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Scrubbing Time Preview Tooltip
        if (isScrubbing && duration > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .shadow(6.dp, RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = formatPlayerDuration(scrubTimeMs),
                    color = Color.White,
                    fontSize = if (isFullscreen) 12.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Custom Interactive Track Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFullscreen) 32.dp else 26.dp)
                .onSizeChanged { scrubberWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(duration) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isScrubbing = true
                        onScrubbingChanged(true)
                        scrubProgress = (down.position.x / scrubberWidthPx).coerceIn(0f, 1f)
                        if (duration > 0 && duration != C.TIME_UNSET) {
                            val initialTarget = (scrubProgress * duration).toLong().coerceIn(0L, duration)
                            onSeekLive(initialTarget)
                        }

                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            change.consume()
                            if (change.pressed) {
                                scrubProgress = (change.position.x / scrubberWidthPx).coerceIn(0f, 1f)
                                if (duration > 0 && duration != C.TIME_UNSET) {
                                    val liveTarget = (scrubProgress * duration).toLong().coerceIn(0L, duration)
                                    onSeekLive(liveTarget)
                                }
                            } else {
                                if (duration > 0 && duration != C.TIME_UNSET) {
                                    val finalTarget = (scrubProgress * duration).toLong().coerceIn(0L, duration)
                                    onSeekFinal(finalTarget)
                                }
                                isScrubbing = false
                                onScrubbingChanged(false)
                                break
                            }
                        }
                    }
                }
                .testTag(if (isFullscreen) "video_seekbar" else "video_seekbar_inline"),
            contentAlignment = Alignment.CenterStart
        ) {
            val trackHeight = if (isScrubbing) 6.dp else 4.dp

            // Background Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.22f))
            )

            // Buffered Progress Track
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = bufferedFraction)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.40f))
            )

            // Active Progress Track
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = effectiveFraction)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(LocalAccentColor.current)
            )

            // Scrubber Thumb
            val thumbDiameter = if (isScrubbing) 16.dp else 10.dp
            val thumbRadiusPx = with(LocalDensity.current) { (thumbDiameter / 2).toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = ((scrubberWidthPx * effectiveFraction) - thumbRadiusPx).toInt().coerceIn(
                                0,
                                (scrubberWidthPx - thumbRadiusPx * 2).toInt().coerceAtLeast(0)
                            ),
                            y = 0
                        )
                    }
                    .size(thumbDiameter)
                    .shadow(4.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .clip(CircleShape)
            )
        }
    }
}
