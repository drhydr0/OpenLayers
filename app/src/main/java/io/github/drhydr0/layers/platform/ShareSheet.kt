/**
 * Photo picker, files, gallery, share sheet, and app lifecycle.
 */
package io.github.drhydr0.layers.platform

import android.app.Activity
import android.content.Intent

/** Opens Android's share sheet with plain text, e.g. a crash or diagnostics report. */
fun Activity.shareText(subject: String, text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, chooserTitle))
}
