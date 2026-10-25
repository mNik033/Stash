package com.stash.feature.nowplaying.listen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

/** The Android share sheet with the invite link (`https://…/l/{code}`, an App Link). */
fun shareInvite(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Listen with me on Stash: $url")
    context.startActivity(Intent.createChooser(send, "Invite friends").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Copies the invite link. Android 13+ shows its own clipboard confirmation; older versions get a toast. */
fun copyInvite(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Stash invite", url))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}
