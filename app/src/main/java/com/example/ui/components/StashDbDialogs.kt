package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette

/**
 * Progress Dialog for StashDB batch scene saving with torrent resolution.
 */
@Composable
fun StashBatchSaveProgressDialog(
    current: Int,
    total: Int,
    currentTitle: String,
    currentPhase: String,
    savedWithTorrentsCount: Int,
    onCancel: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current

    val percentage = if (total > 0) ((current.toFloat() / total) * 100).toInt().coerceIn(0, 100) else 0
    val progressFraction = if (total > 0) (current.toFloat() / total).coerceIn(0f, 1f) else 0f

    AlertDialog(
        onDismissRequest = { /* Non-dismissable on touch outside */ },
        shape = RoundedCornerShape(28.dp),
        containerColor = palette.cardBg,
        title = {
            Text(
                text = "Saving Scenes...",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = palette.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accent,
                    trackColor = palette.border
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scene $current of $total ($percentage%)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary
                    )
                    if (savedWithTorrentsCount > 0) {
                        Text(
                            text = "Torrents: $savedWithTorrentsCount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }

                if (currentTitle.isNotBlank()) {
                    Text(
                        text = currentTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (currentPhase.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = accent
                        )
                        Text(
                            text = currentPhase,
                            fontSize = 11.5.sp,
                            color = palette.textMuted
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onCancel,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.testTag("cancel_batch_save_button")
            ) {
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

/**
 * Confirmation dialog for blocking a studio from StashDB search results.
 */
@Composable
fun StashBlockStudioDialog(
    targetName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalVaultPalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = palette.cardBg,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings_filter),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "Block Studio?",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = palette.textPrimary,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "Do you want to block \"$targetName\"? Scenes from this studio will be filtered out from StashDB results.",
                color = palette.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Block Studio", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Cancel", color = palette.textSecondary, fontWeight = FontWeight.Medium)
            }
        }
    )
}
