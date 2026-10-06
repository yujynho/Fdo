package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.privacyImageBlur

val PrivacyScrim = Color.Black.copy(alpha = 0.75f)

@Composable
fun isAppLightTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

@Composable
fun SleekSlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 4.dp,
    thumbDiameter: Dp = 16.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentValueRange by rememberUpdatedState(valueRange)
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val thumbRadius = thumbDiameter / 2

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val thumbRadiusPx = with(density) { thumbRadius.toPx() }
        val usableWidth = (widthPx - thumbRadiusPx * 2).coerceAtLeast(1f)

        fun updateFromX(touchX: Float) {
            val clamped = (touchX - thumbRadiusPx).coerceIn(0f, usableWidth)
            val newFraction = clamped / usableWidth
            val newValue = currentValueRange.start + newFraction * (currentValueRange.endInclusive - currentValueRange.start)
            currentOnValueChange(newValue)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(usableWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        updateFromX(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            updateFromX(change.position.x)
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Inactive slim track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(inactiveColor)
            )
            // Active slim track
            val activeTrackWidth = with(density) { (thumbRadiusPx + fraction * usableWidth).toDp() }
            Box(
                modifier = Modifier
                    .width(activeTrackWidth)
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(activeColor)
            )
            // Sleek, clean circular thumb
            val thumbOffset = with(density) { (fraction * usableWidth).toDp() }
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbDiameter)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(activeColor)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    currentColor: Color,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 8.dp,
    thumbDiameter: Dp = 18.dp
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val fraction = value.coerceIn(0f, 1f)
    val thumbRadius = thumbDiameter / 2

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val thumbRadiusPx = with(density) { thumbRadius.toPx() }
        val usableWidth = (widthPx - thumbRadiusPx * 2).coerceAtLeast(1f)

        fun updateFromX(touchX: Float) {
            val clamped = (touchX - thumbRadiusPx).coerceIn(0f, usableWidth)
            val newFraction = clamped / usableWidth
            currentOnValueChange(newFraction)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(usableWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        updateFromX(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            updateFromX(change.position.x)
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // The colored gradient track itself - slim and sleek
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black,
                                Color(0xFF333333),
                                Color(0xFF666666),
                                Color(0xFF999999),
                                Color(0xFFCCCCCC),
                                Color.White
                            )
                        )
                    )
                    .border(0.75.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
            )

            // Dynamic Thumb with current color fill and crisp contrasting border
            val thumbOffset = with(density) { (fraction * usableWidth).toDp() }
            val thumbBorderColor = if (currentColor.luminance() > 0.5f) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbDiameter)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(2.dp, thumbBorderColor, CircleShape)
            )
        }
    }
}

/**
 * Lightweight, GPU-accelerated horizontal fade mask for smooth gradient edge aesthetic.
 */
fun Modifier.horizontalFadeEdge(fadeWidth: Dp = 20.dp): Modifier = this.then(
    Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadePx = fadeWidth.toPx()
            if (size.width > fadePx * 2 && fadePx > 0f) {
                val leftFraction = (fadePx / size.width).coerceIn(0f, 0.49f)
                val rightFraction = 1f - leftFraction
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        leftFraction to Color.Black,
                        rightFraction to Color.Black,
                        1f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
        }
)

/**
 * Smooth & Lightweight Photo Option Circle with Fade + Subtle Scale In reveal effect.
 */
@Composable
fun SelectPhotoCircleItem(
    imgUrl: String,
    isSelected: Boolean,
    skeletonBg: Color,
    skeletonBorder: Color,
    isBetaTest: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isImageLoaded by remember(imgUrl) { mutableStateOf(false) }

    val photoAlpha by animateFloatAsState(
        targetValue = if (isImageLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "circle_photo_alpha"
    )
    val photoScale by animateFloatAsState(
        targetValue = if (isImageLoaded) 1f else 0.88f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "circle_photo_scale"
    )
    val circleSelectScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "circle_select_scale"
    )

    Box(
        modifier = modifier
            .size(50.dp)
            .graphicsLayer {
                scaleX = circleSelectScale
                scaleY = circleSelectScale
            }
            .clip(CircleShape)
            .background(skeletonBg)
            .border(
                BorderStroke(
                    if (isSelected) 2.5.dp else 1.2.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else skeletonBorder
                ),
                CircleShape
            )
            .clickable { onSelect() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imgUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Photo option",
            contentScale = ContentScale.Crop,
            onSuccess = { isImageLoaded = true },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = photoAlpha
                    scaleX = photoScale
                    scaleY = photoScale
                }
                .privacyImageBlur(isBetaTest)
        )
    }
}
