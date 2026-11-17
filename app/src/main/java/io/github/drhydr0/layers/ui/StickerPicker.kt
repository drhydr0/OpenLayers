package io.github.drhydr0.layers.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import io.github.drhydr0.layers.LayersApp
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.diagnostics.AppLog
import io.github.drhydr0.layers.document.Sticker

/** The sticker collection: tap a sticker to insert it; rename or delete from its menu. */
class StickerPicker(private val activity: Activity, private val onInsert: (Sticker) -> Unit) {

    private val app = activity.application as LayersApp
    private val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var dialog: AlertDialog? = null

    fun show() {
        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.stickers_title)
            .setView(ScrollView(activity).apply { addView(rows) })
            .setNegativeButton(R.string.stickers_close, null)
            .show()
        reload()
    }

    private fun reload() {
        app.io.execute {
            val stickers = app.stickers.list()
            // Small decode: rows show 56 dp thumbnails.
            val thumbnails = stickers.associate { s ->
                val file = app.stickers.thumbnailFile(s.id)
                s.id to if (file.isFile) BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 2 }) else null
            }
            activity.runOnUiThread {
                if (dialog?.isShowing != true) return@runOnUiThread
                rows.removeAllViews()
                if (stickers.isEmpty()) rows.addView(emptyMessage())
                stickers.forEach { sticker ->
                    val row = activity.layoutInflater.inflate(R.layout.item_sticker, rows, false)
                    row.findViewById<ImageView>(R.id.sticker_thumbnail).setImageBitmap(thumbnails[sticker.id])
                    row.findViewById<TextView>(R.id.sticker_name).text = sticker.name
                    row.contentDescription = activity.getString(R.string.sticker_insert_description, sticker.name)
                    row.setOnClickListener {
                        dialog?.dismiss()
                        onInsert(sticker)
                    }
                    row.findViewById<ImageButton>(R.id.sticker_more).apply {
                        contentDescription = activity.getString(R.string.sticker_more_description, sticker.name)
                        setOnClickListener { showMenu(it, sticker) }
                    }
                    rows.addView(row)
                }
            }
        }
    }

    private fun emptyMessage() = TextView(activity).apply {
        setText(R.string.stickers_empty)
        gravity = Gravity.CENTER
        val pad = (PADDING_DP * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
    }

    private fun showMenu(anchor: View, sticker: Sticker) {
        PopupMenu(activity, anchor).apply {
            menu.add(0, MENU_RENAME, 0, R.string.sticker_rename)
            menu.add(0, MENU_DELETE, 1, R.string.sticker_delete)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    MENU_RENAME -> askRename(sticker)
                    MENU_DELETE -> askDelete(sticker)
                }
                true
            }
        }.show()
    }

    private fun askRename(sticker: Sticker) {
        val input = EditText(activity).apply {
            setText(sticker.name)
            setSelectAllOnFocus(true)
            contentDescription = activity.getString(R.string.sticker_name_hint)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.sticker_rename_title)
            .setView(input)
            .setPositiveButton(R.string.sticker_rename) { _, _ ->
                val name = input.text.toString().trim().ifEmpty { sticker.name }
                change("rename") { app.stickers.rename(sticker.id, name) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun askDelete(sticker: Sticker) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.sticker_delete_title, sticker.name))
            .setMessage(R.string.sticker_delete_message)
            .setPositiveButton(R.string.sticker_delete) { _, _ -> change("delete") { app.stickers.delete(sticker.id) } }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun change(what: String, work: () -> Unit) {
        app.io.execute {
            runCatching(work).onFailure { AppLog.e(TAG, "Sticker $what failed", it) }
            reload()
        }
    }

    private companion object {
        const val TAG = "StickerPicker"
        const val MENU_RENAME = 1
        const val MENU_DELETE = 2
        const val PADDING_DP = 24
    }
}
