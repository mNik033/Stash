package com.stash.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.stash.desktop.spike.DaggerSpikeComponent
import com.stash.desktop.spike.RoomSpike
import com.stash.desktop.spike.SpikeTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Stash — Desktop Spike (Phase 0)",
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF8B5CF6),
                background = Color(0xFF0F172A),
                surface = Color(0xFF1E293B),
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFF8FAFC),
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                var diStatus by remember { mutableStateOf("Initializing DI...") }
                var dbStatus by remember { mutableStateOf("Initializing Room DB...") }

                LaunchedEffect(Unit) {
                    try {
                        val component = DaggerSpikeComponent.create()
                        diStatus = component.service().greeting
                    } catch (e: Throwable) {
                        diStatus = "DI Error: ${e.message}"
                    }

                    try {
                        withContext(Dispatchers.IO) {
                            val db = RoomSpike.createInMemoryDb()
                            val dao = db.dao()
                            dao.insert(SpikeTrack(id = 1L, title = "Spike Song", artist = "Stash Desktop"))
                            val track = dao.findById(1L)
                            dbStatus = "Room KMP + BundledSQLite: read '${track?.title}' by '${track?.artist}'"
                            db.close()
                        }
                    } catch (e: Throwable) {
                        dbStatus = "Room Error: ${e.message}"
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Stash Desktop",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Compose Multiplatform Spike · Phase 0",
                        fontSize = 16.sp,
                        color = Color(0xFF94A3B8),
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    StatusCard(
                        title = "Platform",
                        detail = "JVM ${System.getProperty("java.version")} on ${System.getProperty("os.name")}",
                        color = Color(0xFFA78BFA),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    StatusCard(
                        title = "Dependency Injection (Dagger)",
                        detail = diStatus,
                        color = Color(0xFF34D399),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    StatusCard(
                        title = "Database (Room KMP)",
                        detail = dbStatus,
                        color = Color(0xFF38BDF8),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, detail: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column {
            Text(
                text = title,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
            )
        }
    }
}
