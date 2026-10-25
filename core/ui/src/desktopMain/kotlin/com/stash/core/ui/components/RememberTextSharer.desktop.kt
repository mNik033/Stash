package com.stash.core.ui.components

import androidx.compose.runtime.Composable
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
actual fun rememberTextSharer(): (text: String, title: String) -> Unit {
    return { text, _ ->
        try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        } catch (_: Exception) {}
    }
}
