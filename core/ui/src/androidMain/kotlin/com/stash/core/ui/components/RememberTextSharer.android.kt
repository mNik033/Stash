package com.stash.core.ui.components

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberTextSharer(): (text: String, title: String) -> Unit {
    val context = LocalContext.current
    return { text, title ->
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
