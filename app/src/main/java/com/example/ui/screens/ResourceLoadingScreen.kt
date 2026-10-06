package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.AppLottieLoadingAnimation
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette

/**
 * Minimalist Resource Loading Screen (واجهة تحميل الموارد).
 * 1. Centered app icon.
 * 2. Lottie loading animation directly beneath it.
 * ONLY these two elements in the center of the screen!
 */
@Composable
fun ResourceLoadingScreen(
    statusText: String = "",
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Circular cropped app icon matching the launcher icon exactly
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "App Icon",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Uniform Lottie loading animation (92.dp matching video player)
            AppLottieLoadingAnimation(
                modifier = Modifier.size(92.dp)
            )
        }
    }
}
