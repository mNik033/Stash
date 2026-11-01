package com.stash.desktop.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stash.core.auth.model.AuthService
import com.stash.core.auth.model.AuthState
import com.stash.core.model.DownloadMode
import com.stash.core.model.QualityTier
import com.stash.core.ui.theme.StashCyan
import com.stash.core.ui.theme.StashPurple
import com.stash.data.download.lossless.RoutingState
import com.stash.data.download.lossless.qbdlx.QobuzLoginResult
import com.stash.desktop.di.DesktopComponent
import kotlinx.coroutines.launch

@Composable
fun DesktopSettingsScreen(
    component: DesktopComponent,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    isAmoledTheme: Boolean = false,
    onThemeChanged: (dark: Boolean, amoled: Boolean) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val appPaths = component.appPaths()
    val platformInfo = component.platformInfo()
    val tokenManager = component.tokenManager()
    val streamingPreference = component.streamingPreference()
    val qualityPreference = component.qualityPreference()
    val qbdlxCredentialStore = component.qbdlxCredentialStore()
    val qobuzAccountConnector = component.qobuzAccountConnector()
    val losslessSourcePreferences = component.losslessSourcePreferences()
    val losslessAvailability = component.losslessAvailability()
    val losslessConfigFetcher = component.losslessConfigFetcher()
    val syncViewModel = component.syncViewModel()

    val mpvStatusMessage = if (component.mpvPlaybackEngine().isNativeEngineAvailable) {
        "libmpv initialized and active"
    } else {
        "libmpv not detected (fallback mode)"
    }

    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var showSpotifyDialog by remember { mutableStateOf(false) }
    var showYouTubeDialog by remember { mutableStateOf(false) }
    var showQobuzDialog by remember { mutableStateOf(false) }
    var showCustomQobuzEndpointDialog by remember { mutableStateOf(false) }
    var showBitChordDialog by remember { mutableStateOf(false) }

    // Reactive states
    val isStreamingEnabled by streamingPreference.enabled.collectAsState(initial = true)
    val isLosslessStreamingEnabled by losslessSourcePreferences.enabled.collectAsState(initial = true)
    val currentQualityTier by qualityPreference.qualityTier.collectAsState(initial = QualityTier.MAX)
    val currentDownloadMode by losslessSourcePreferences.downloadMode.collectAsState(initial = DownloadMode.LOSSLESS)
    val youtubeFallback by losslessSourcePreferences.youtubeFallbackEnabled.collectAsState(initial = false)
    val customLosslessEndpoint by losslessSourcePreferences.customLosslessEndpoint.collectAsState(initial = null)
    val bitchordEndpoint by losslessSourcePreferences.bitchordEndpoint.collectAsState(initial = null)
    val bitchordAccessToken by losslessSourcePreferences.bitchordAccessToken.collectAsState(initial = null)
    val routingRows by losslessAvailability.routingRows.collectAsState(initial = emptyList())
    val configTidalRelay by losslessConfigFetcher.tidalRelay.collectAsState()

    val spotifyAuthState by tokenManager.spotifyAuthState.collectAsState()
    val youTubeAuthState by tokenManager.youTubeAuthState.collectAsState()

    val isSpotifyConnected = spotifyAuthState is AuthState.Connected
    val isYouTubeConnected = youTubeAuthState is AuthState.Connected

    var qobuzConnectedEmail by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showQobuzDialog) {
        qobuzConnectedEmail = qobuzAccountConnector.connectedEmail()
    }

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
            text = "Playback mode, audio quality, streaming credentials, and system settings",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(28.dp))

        // ── 1. Playback & Library Mode (Streaming vs Download) ────────────
        SettingsSectionHeader(title = "Playback & Library Mode", icon = Icons.Default.MusicNote)
        SettingsGroupContainer {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ModeCard(
                    title = "Streaming Mode (Online)",
                    subtitle = "Stream entire library on-demand. Requires active internet connection.",
                    icon = Icons.Default.Cloud,
                    isSelected = isStreamingEnabled,
                    onClick = { syncViewModel.setStreamingEnabled(true) },
                    modifier = Modifier.weight(1f),
                )
                ModeCard(
                    title = "Download Mode (Offline)",
                    subtitle = "Play saved music locally from disk. Hides un-downloaded tracks from library.",
                    icon = Icons.Default.Download,
                    isSelected = !isStreamingEnabled,
                    onClick = { syncViewModel.setStreamingEnabled(false) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // ── 2. Audio & Download Quality ────────────────────────────────────
        SettingsSectionHeader(title = "Audio & Download Quality", icon = Icons.Default.Audiotrack)
        SettingsGroupContainer {
            Text(
                text = "Audio Quality Tier",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Governs maximum bitrates for downloads and stream resolution",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QualityTier.entries.forEach { tier ->
                    FilterChip(
                        selected = currentQualityTier == tier,
                        onClick = { scope.launch { qualityPreference.setQualityTier(tier) } },
                        label = { Text("${tier.label} (${tier.bitrateKbps}k)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            SettingsDivider()

            Text(
                text = "Download Mode Target",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "What downloads save to local storage: lossless FLAC or high-bitrate lossy",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DownloadMode.entries.forEach { mode ->
                    val label = when (mode) {
                        DownloadMode.LOSSLESS -> "Lossless (FLAC)"
                        DownloadMode.AAC -> "Tidal AAC (320k)"
                        DownloadMode.OPUS -> "YouTube (Opus)"
                    }
                    FilterChip(
                        selected = currentDownloadMode == mode,
                        onClick = { scope.launch { losslessSourcePreferences.setDownloadMode(mode) } },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            SettingsDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lossless FLAC Streaming",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Prioritize studio-quality 16-bit/24-bit FLAC from Qobuz & Tidal when streaming online",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isLosslessStreamingEnabled,
                    onCheckedChange = { scope.launch { losslessSourcePreferences.setEnabled(it) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            SettingsDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lossy Fallback when FLAC Unavailable",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Falls back to 320kbps lossy audio if the song cannot be found on Qobuz/Tidal",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = youtubeFallback,
                    onCheckedChange = { scope.launch { losslessSourcePreferences.setYoutubeFallbackEnabled(it) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            if (routingRows.isNotEmpty()) {
                SettingsDivider()
                Text(
                    text = "Active Lossless Routing Status",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    routingRows.forEach { row ->
                        val isActive = row.state != RoutingState.NOT_CONFIGURED
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (isActive) Color(0xFF10B981) else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(4.dp),
                                        ),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = row.label,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = row.detail,
                                fontSize = 12.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val tidalActive = !bitchordEndpoint.isNullOrBlank() || configTidalRelay != null
                    val tidalDetail = when {
                        !bitchordEndpoint.isNullOrBlank() -> "custom ($bitchordEndpoint)"
                        configTidalRelay != null -> "configured (Stash relay)"
                        else -> "not configured"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (tidalActive) Color(0xFF10B981) else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(4.dp),
                                    ),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tidal Lossless (BitChord)",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = tidalDetail,
                            fontSize = 12.sp,
                            fontWeight = if (tidalActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (tidalActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // ── 3. Connected Services & Credentials ────────────────────────────
        SettingsSectionHeader(title = "Connected Services & Credentials", icon = Icons.Default.Cloud)
        SettingsGroupContainer {
            // Spotify
            SettingsServiceRow(
                serviceName = "Spotify",
                detail = if (isSpotifyConnected) "sp_dc cookie connected (encrypted at rest with AES-256)" else "Connect sp_dc cookie to sync liked songs and playlists",
                status = if (isSpotifyConnected) "Connected" else "Connect",
                accentColor = Color(0xFF1DB954),
                isConnected = isSpotifyConnected,
                onAction = {
                    if (isSpotifyConnected) {
                        scope.launch { tokenManager.clearAuth(AuthService.SPOTIFY) }
                    } else {
                        showSpotifyDialog = true
                    }
                },
            )
            SettingsDivider()

            // YouTube Music
            SettingsServiceRow(
                serviceName = "YouTube Music",
                detail = if (isYouTubeConnected) "YouTube session cookie connected (encrypted at rest)" else "Connect cookies for age-restricted & premium streaming/downloads",
                status = if (isYouTubeConnected) "Connected" else "Connect",
                accentColor = Color(0xFFFF0000),
                isConnected = isYouTubeConnected,
                onAction = {
                    if (isYouTubeConnected) {
                        scope.launch { tokenManager.clearAuth(AuthService.YOUTUBE_MUSIC) }
                    } else {
                        showYouTubeDialog = true
                    }
                },
            )
            SettingsDivider()

            // Qobuz Lossless BYO
            val isQobuzConnected = !qobuzConnectedEmail.isNullOrBlank()
            SettingsServiceRow(
                serviceName = "Qobuz (Lossless FLAC)",
                detail = if (isQobuzConnected) "Logged in as $qobuzConnectedEmail (encrypted credentials)" else "Bring your own Qobuz subscription for studio-quality 24-bit FLAC",
                status = if (isQobuzConnected) "Connected" else "Connect",
                accentColor = Color(0xFF2563EB),
                isConnected = isQobuzConnected,
                onAction = {
                    if (isQobuzConnected) {
                        scope.launch {
                            qobuzAccountConnector.disconnect()
                            qobuzConnectedEmail = null
                        }
                    } else {
                        showQobuzDialog = true
                    }
                },
            )
            SettingsDivider()

            // Custom Qobuz Relay Endpoint
            val isCustomQobuzConfigured = !customLosslessEndpoint.isNullOrBlank()
            SettingsServiceRow(
                serviceName = "Custom Qobuz Lossless Endpoint",
                detail = if (isCustomQobuzConfigured) "Custom Qobuz relay configured: $customLosslessEndpoint" else "Optional: Connect a custom self-hosted Qobuz lossless relay URL",
                status = if (isCustomQobuzConfigured) "Configured" else "Configure",
                accentColor = Color(0xFF6366F1),
                isConnected = isCustomQobuzConfigured,
                onAction = { showCustomQobuzEndpointDialog = true },
            )
            SettingsDivider()

            // BitChord / Tidal Relay
            val isBitChordConfigured = !bitchordEndpoint.isNullOrBlank() || configTidalRelay != null
            val bitChordDetail = when {
                !bitchordEndpoint.isNullOrBlank() -> "Custom relay configured: $bitchordEndpoint"
                configTidalRelay != null -> "Stash Tidal relay active (click to override with self-hosted BitChord)"
                else -> "Connect self-hosted BitChord instance for Tidal HiFi lossless streaming"
            }
            SettingsServiceRow(
                serviceName = "BitChord / Tidal Lossless Relay",
                detail = bitChordDetail,
                status = if (!bitchordEndpoint.isNullOrBlank()) "Custom" else if (configTidalRelay != null) "Active" else "Configure",
                accentColor = Color(0xFF00B4D8),
                isConnected = isBitChordConfigured,
                onAction = { showBitChordDialog = true },
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        // ── 4. Appearance & Theme ─────────────────────────────────────────
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
                    title = "AMOLED Black",
                    subtitle = "Pure black background",
                    icon = Icons.Default.CheckCircle,
                    isSelected = isDarkTheme && isAmoledTheme,
                    onClick = { onThemeChanged(true, true) },
                    modifier = Modifier.weight(1f),
                )
                ThemeCard(
                    title = "Light Theme",
                    subtitle = "Clean slate & white",
                    icon = Icons.Default.LightMode,
                    isSelected = !isDarkTheme,
                    onClick = { onThemeChanged(false, false) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // ── 5. Storage & Cache ─────────────────────────────────────────────
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

        // ── 6. System Information ──────────────────────────────────────────
        SettingsSectionHeader(title = "System & Environment", icon = Icons.Default.Computer)
        SettingsGroupContainer {
            SettingsDetailRow(
                label = "Operating System",
                value = "${platformInfo.osName} (${System.getProperty("os.arch")})",
            )
            SettingsDivider()
            SettingsDetailRow(
                label = "Playback Engine",
                value = mpvStatusMessage,
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

    // ── Dialogs ────────────────────────────────────────────────────────────

    // Spotify Cookie Dialog
    if (showSpotifyDialog) {
        var spDcInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSpotifyDialog = false },
            title = { Text("Connect Spotify Account") },
            text = {
                Column {
                    Text(
                        text = "Paste your 'sp_dc' cookie from open.spotify.com. It is encrypted at rest using OS-level AES-256 (DPAPI on Windows / Keychain on macOS).",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = spDcInput,
                        onValueChange = { spDcInput = it },
                        label = { Text("sp_dc Cookie") },
                        placeholder = { Text("AQB...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cookie = spDcInput.trim()
                        if (cookie.isNotBlank()) {
                            scope.launch {
                                tokenManager.connectSpotifyWithCookie(cookie)
                                showSpotifyDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StashPurple),
                ) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpotifyDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // YouTube Cookie Dialog
    if (showYouTubeDialog) {
        var ytCookieInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showYouTubeDialog = false },
            title = { Text("Connect YouTube Music Cookies") },
            text = {
                Column {
                    Text(
                        text = "Paste cookies.txt content or raw Cookie header from music.youtube.com for age-restricted / premium stream access. Encrypted at rest.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ytCookieInput,
                        onValueChange = { ytCookieInput = it },
                        label = { Text("Cookie Content") },
                        placeholder = { Text("SAPISID=...; HSID=... or # Netscape HTTP Cookie File") },
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cookie = ytCookieInput.trim()
                        if (cookie.isNotBlank()) {
                            scope.launch {
                                tokenManager.connectYouTubeWithCookie(cookie)
                                showYouTubeDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StashPurple),
                ) {
                    Text("Save Cookies")
                }
            },
            dismissButton = {
                TextButton(onClick = { showYouTubeDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Qobuz Account Dialog
    if (showQobuzDialog) {
        var emailInput by remember { mutableStateOf("") }
        var passwordInput by remember { mutableStateOf("") }
        var isLoggingIn by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isLoggingIn) showQobuzDialog = false },
            title = { Text("Qobuz Account Login") },
            text = {
                Column {
                    Text(
                        text = "Sign in to your Qobuz account to enable direct Hi-Res 24-bit/192kHz FLAC streaming and downloads. Tokens are encrypted at rest with AES-256.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Qobuz Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Qobuz Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            errorMessage = null
                            isLoggingIn = true
                            val res = qobuzAccountConnector.connect(emailInput.trim(), passwordInput)
                            isLoggingIn = false
                            when (res) {
                                is QobuzLoginResult.Success -> {
                                    qobuzConnectedEmail = emailInput.trim()
                                    showQobuzDialog = false
                                }
                                is QobuzLoginResult.InvalidCredentials -> {
                                    errorMessage = "Invalid email or password"
                                }
                                is QobuzLoginResult.FreeAccount -> {
                                    errorMessage = "This Qobuz account does not have a lossless subscription"
                                }
                                is QobuzLoginResult.Error -> {
                                    errorMessage = "Network or server error connecting to Qobuz"
                                }
                            }
                        }
                    },
                    enabled = !isLoggingIn && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = StashPurple),
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Sign In")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showQobuzDialog = false }, enabled = !isLoggingIn) {
                    Text("Cancel")
                }
            },
        )
    }

    // Custom Qobuz Endpoint Dialog
    if (showCustomQobuzEndpointDialog) {
        var customEndpointInput by remember { mutableStateOf(customLosslessEndpoint.orEmpty()) }

        AlertDialog(
            onDismissRequest = { showCustomQobuzEndpointDialog = false },
            title = { Text("Configure Custom Qobuz Endpoint") },
            text = {
                Column {
                    Text(
                        text = "Enter the HTTPS base URL of a custom Qobuz lossless relay (e.g., https://my-qobuz-relay.workers.dev). Leave blank to clear.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customEndpointInput,
                        onValueChange = { customEndpointInput = it },
                        label = { Text("Custom Qobuz Relay URL") },
                        placeholder = { Text("https://relay.example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            losslessSourcePreferences.setCustomLosslessEndpoint(customEndpointInput.trim().ifBlank { null })
                            showCustomQobuzEndpointDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StashPurple),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomQobuzEndpointDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // BitChord Relay Dialog
    if (showBitChordDialog) {
        var endpointInput by remember { mutableStateOf(bitchordEndpoint.orEmpty()) }
        var tokenInput by remember { mutableStateOf(bitchordAccessToken.orEmpty()) }

        AlertDialog(
            onDismissRequest = { showBitChordDialog = false },
            title = { Text("Configure BitChord / Lossless Relay") },
            text = {
                Column {
                    Text(
                        text = "Enter the URL of your self-hosted BitChord Tidal relay and optional access token. Token is encrypted at rest.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        label = { Text("BitChord Server URL") },
                        placeholder = { Text("https://bitchord.myhost.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Access Token (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            losslessSourcePreferences.setBitchordEndpoint(endpointInput.trim().ifBlank { null })
                            losslessSourcePreferences.setBitchordAccessToken(tokenInput.trim().ifBlank { null })
                            showBitChordDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StashPurple),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBitChordDialog = false }) {
                    Text("Cancel")
                }
            },
        )
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
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
    isConnected: Boolean = false,
    onAction: () -> Unit = {},
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        OutlinedButton(
            onClick = onAction,
            shape = RoundedCornerShape(8.dp),
            colors = if (isConnected) {
                androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            } else {
                androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            },
        ) {
            Text(if (isConnected) "Disconnect" else status, fontSize = 12.sp)
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
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    )
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerBg,
        border = BorderStroke(1.5.dp, borderColor),
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
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active Mode",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
    val containerBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerBg,
        border = BorderStroke(1.5.dp, borderColor),
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
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
