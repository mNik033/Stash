package com.stash.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stash.core.common.platform.AppPaths
import com.stash.core.common.platform.PlatformInfo
import com.stash.core.ui.theme.StashCyan
import com.stash.core.ui.theme.StashGlassBorder
import com.stash.core.ui.theme.StashPurple
import com.stash.core.ui.theme.StashSurface

@Composable
fun DesktopSettingsScreen(
    appPaths: AppPaths,
    platformInfo: PlatformInfo,
    mpvStatusMessage: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    isAmoledTheme: Boolean = false,
    onThemeChanged: (dark: Boolean, amoled: Boolean) -> Unit = { _, _ -> },
) {
    val scrollState = rememberScrollState()
    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 28.dp)
            .verticalScroll(scrollState),
    ) {
        Text(
            text = "Settings",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Audio engine, connected streaming services, and desktop preferences",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
        )
        Spacer(modifier = Modifier.height(28.dp))

        // ── 1. Appearance & Theme ─────────────────────────────────────────
        SettingsSectionHeader(title = "Appearance", icon = Icons.Default.Palette)
        SettingsGroupContainer {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ThemeCard(
                    title = "Dark Theme",
                    subtitle = "Deep purple & dark slate",
                    icon = Icons.Default.DarkMode,
                    isSelected = isDarkTheme && !isAmoledTheme,
                    onClick = { onThemeChanged(true, false) },
                    modifier = Modifier.weight(1f),
                )
                ThemeCard(
                    title = "AMOLED",
                    subtitle = "Pure black pixels #000000",
                    icon = Icons.Default.DarkMode,
                    isSelected = isDarkTheme && isAmoledTheme,
                    onClick = { onThemeChanged(true, true) },
                    modifier = Modifier.weight(1f),
                )
                ThemeCard(
                    title = "Light Theme",
                    subtitle = "Crisp daytime contrast",
                    icon = Icons.Default.LightMode,
                    isSelected = !isDarkTheme,
                    onClick = { onThemeChanged(false, false) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // ── 2. Playback & Audio Engine ─────────────────────────────────────
        SettingsSectionHeader(title = "Audio Playback Engine", icon = Icons.Default.Audiotrack)
        SettingsGroupContainer {
            SettingsDetailRow(
                label = "Native Audio Engine",
                value = "libmpv (High-Performance Audio Pipeline)",
                description = mpvStatusMessage,
            )
            SettingsDivider()
            SettingsDetailRow(
                label = "Bit-Perfect & Hi-Res Output",
                value = "Automatic sample rate & bit depth switching",
                description = "FLAC up to 24-bit / 192kHz supported with gapless playback",
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        // ── 3. Streaming Accounts ──────────────────────────────────────────
        SettingsSectionHeader(title = "Connected Services", icon = Icons.Default.Cloud)
        SettingsGroupContainer {
            SettingsServiceRow(
                serviceName = "Spotify",
                detail = "Web Player sp_dc cookie authentication with auto-refresh",
                status = "Ready to connect",
                accentColor = Color(0xFF1DB954),
            )
            SettingsDivider()
            SettingsServiceRow(
                serviceName = "YouTube Music",
                detail = "InnerTube API with SAPISID authentication",
                status = "Ready to connect",
                accentColor = Color(0xFFFF0000),
            )
            SettingsDivider()
            SettingsServiceRow(
                serviceName = "Discord Rich Presence",
                detail = "Displays current song, artist, and album in your Discord status",
                status = "Desktop RPC active",
                accentColor = Color(0xFF5865F2),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        // ── 4. Storage & Cache ─────────────────────────────────────────────
        SettingsSectionHeader(title = "Storage & Cache", icon = Icons.Default.Folder)
        SettingsGroupContainer {
            SettingsDetailRow(
                label = "Application Data Directory",
                value = appPaths.filesDir.absolutePath,
            )
            SettingsDivider()
            SettingsDetailRow(
                label = "Cache Directory",
                value = appPaths.cacheDir.absolutePath,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        try {
                            appPaths.cacheDir.deleteRecursively()
                            appPaths.cacheDir.mkdirs()
                            cacheClearedMessage = "Cache cleared successfully."
                        } catch (e: Exception) {
                            cacheClearedMessage = "Failed to clear cache: ${e.message}"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StashPurple),
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Temporary Cache")
                }
                if (cacheClearedMessage != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = cacheClearedMessage!!,
                        color = StashCyan,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // ── 5. System Information ──────────────────────────────────────────
        SettingsSectionHeader(title = "System & Environment", icon = Icons.Default.Computer)
        SettingsGroupContainer {
            SettingsDetailRow(
                label = "Operating System",
                value = "${platformInfo.osName} (${System.getProperty("os.arch")})",
            )
            SettingsDivider()
            SettingsDetailRow(
                label = "Java Runtime Environment",
                value = "JVM ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})",
            )
            SettingsDivider()
            SettingsDetailRow(
                label = "Stash Desktop Version",
                value = "1.0.0 (Compose Multiplatform Desktop)",
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = StashCyan,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SettingsGroupContainer(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = StashSurface,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, StashGlassBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsDetailRow(
    label: String,
    value: String,
    description: String? = null,
) {
    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            color = Color(0xFF94A3B8),
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = StashCyan.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun SettingsServiceRow(
    serviceName: String,
    detail: String,
    status: String,
    accentColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accentColor, shape = RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = serviceName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
            )
        }
        OutlinedButton(
            onClick = {},
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(status, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(1.dp)
            .background(StashGlassBorder),
    )
}

@Composable
private fun ThemeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) StashPurple.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isSelected) StashPurple else StashGlassBorder,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) StashCyan else Color(0xFF94A3B8),
                    modifier = Modifier.size(24.dp),
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = StashPurple,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
            )
        }
    }
}
