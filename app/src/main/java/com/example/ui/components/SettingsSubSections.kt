package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.SettingsEntity
import com.example.ui.SettingsSection
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalVaultPalette

@Composable
fun SettingsMainMenu(
    modifier: Modifier = Modifier,
    currentSettings: SettingsEntity,
    onNavigateTo: (SettingsSection) -> Unit
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        GroupedCard {
            // 1. Display
            SettingsRow(
                title = "Display",
                subtitle = "Theme, accent & display options",
                icon = painterResource(id = R.drawable.ic_settings_display),
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.DISPLAY) }
            )
            SettingsDivider()
            // 2. Privacy
            SettingsRow(
                title = "Privacy",
                subtitle = "Content privacy blur",
                icon = painterResource(id = R.drawable.ic_settings_privacy),
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.PRIVACY) }
            )
            SettingsDivider()
            // 3. Integrations
            SettingsRow(
                title = "Integrations",
                subtitle = "Real-Debrid, Torbox & StashDB",
                icon = painterResource(id = R.drawable.ic_settings_integrations),
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.INTEGRATIONS) }
            )
            SettingsDivider()
            // 4. Filter
            SettingsRow(
                title = "Filter",
                subtitle = if (currentSettings.blockedStudioNames.isNotEmpty()) {
                    "${currentSettings.blockedStudioNames.size} studio(s) blocked"
                } else {
                    "Blocked studios & content filters"
                },
                icon = painterResource(id = R.drawable.ic_settings_filter),
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (currentSettings.blockedStudioNames.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = accent.copy(alpha = 0.15f),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${currentSettings.blockedStudioNames.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accent
                                    )
                                }
                            }
                        }
                        SettingsNavigationChevron()
                    }
                },
                onClick = { onNavigateTo(SettingsSection.FILTER) }
            )
            SettingsDivider()
            // 5. Data & Backup
            SettingsRow(
                title = "Data & Backup",
                subtitle = "Export & restore your vault",
                icon = painterResource(id = R.drawable.ic_settings_backup),
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.DATA_BACKUP) }
            )
            SettingsDivider()
            // 6. Sample Dataset
            SettingsRow(
                title = "Sample Dataset",
                subtitle = "Demo data for testing",
                icon = painterResource(id = R.drawable.ic_settings_sample_data),
                trailing = { SettingsNavigationChevron() },
                onClick = { onNavigateTo(SettingsSection.SAMPLE_DATA) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Goony Vault • Version 1.0.0 (Build 42)",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = palette.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SettingsDisplaySection(
    modifier: Modifier = Modifier,
    themeName: String,
    onThemeChange: (String) -> Unit,
    accentHex: String,
    onAccentChange: (String) -> Unit,
    showManagementCards: Boolean,
    onShowManagementCardsChange: (Boolean) -> Unit,
    enableVideoPlayerGestures: Boolean,
    onEnableVideoPlayerGesturesChange: (Boolean) -> Unit
) {
    val palette = LocalVaultPalette.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Theme selection in Grouped Card
        GroupedCard {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = palette.textPrimary
                )

                NativeThemeSelector(
                    selectedTheme = themeName,
                    onSelectTheme = onThemeChange
                )
            }
        }

        // Color Palette in Grouped Card
        GroupedCard {
            Box(modifier = Modifier.padding(20.dp)) {
                ColorPalettePicker(
                    selectedId = accentHex,
                    onSelectPalette = onAccentChange
                )
            }
        }

        // Cards layout toggle in Grouped Card
        GroupedCard {
            SettingsRow(
                title = "Cards Layout",
                subtitle = "Display cards for Actors and Studios management",
                trailing = {
                    Switch(
                        checked = showManagementCards,
                        onCheckedChange = onShowManagementCardsChange,
                        colors = museSwitchColors(),
                        modifier = Modifier.testTag("cards_management_switch")
                    )
                },
                onClick = { onShowManagementCardsChange(!showManagementCards) }
            )
        }

        // Player Gestures toggle in Grouped Card
        GroupedCard {
            SettingsRow(
                title = "Player Gestures",
                subtitle = "Control volume and brightness by vertical swipes in the video overlay player. When turned off, vertical scrolling over the video passes through smoothly.",
                trailing = {
                    Switch(
                        checked = enableVideoPlayerGestures,
                        onCheckedChange = onEnableVideoPlayerGesturesChange,
                        colors = museSwitchColors(),
                        modifier = Modifier.testTag("player_gestures_switch")
                    )
                },
                onClick = { onEnableVideoPlayerGesturesChange(!enableVideoPlayerGestures) }
            )
        }
    }
}

@Composable
fun SettingsPrivacySection(
    modifier: Modifier = Modifier,
    betaTestPrivacy: Boolean,
    onBetaTestPrivacyChange: (Boolean) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GroupedCard {
            SettingsRow(
                title = "Beta Test Privacy",
                subtitle = "Loads all media seamlessly in the app while applying a smart privacy blur to obscure image content across all screens.",
                trailing = {
                    Switch(
                        checked = betaTestPrivacy,
                        onCheckedChange = onBetaTestPrivacyChange,
                        colors = museSwitchColors(),
                        modifier = Modifier.testTag("beta_test_privacy_switch")
                    )
                },
                onClick = { onBetaTestPrivacyChange(!betaTestPrivacy) }
            )
        }
    }
}

@Composable
fun SettingsSampleDataSection(
    modifier: Modifier = Modifier,
    sampleDataStatus: String,
    accentColor: Color,
    onLoadSample: () -> Unit,
    onClearSample: () -> Unit
) {
    val palette = LocalVaultPalette.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GroupedCard {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Sample dataset management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary
                )
                Text(
                    "Load realistic sample data (studios, actors, scenes with magnets) or clean them completely from the database",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onLoadSample,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_app_add), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load Sample")
                    }

                    OutlinedButton(
                        onClick = onClearSample,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All")
                    }
                }

                if (sampleDataStatus.isNotEmpty()) {
                    Text(
                        sampleDataStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}
