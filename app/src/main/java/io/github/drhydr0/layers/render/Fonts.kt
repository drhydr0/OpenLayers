package io.github.drhydr0.layers.render

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.diagnostics.AppLog
import io.github.drhydr0.layers.document.FontFamily
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a saved font choice into a typeface. One place for the editor, export, captures,
 * and thumbnails, so text looks the same everywhere. Thread-safe.
 */
object Fonts {

    // Resources, not a Context: all that's needed, and it can't hold on to a screen.
    @Volatile
    private var resources: Resources? = null
    private val cache = ConcurrentHashMap<Triple<FontFamily, Boolean, Boolean>, Typeface>()

    /** Called once at startup, so bundled fonts can be loaded from any thread later. */
    fun init(appContext: Context) {
        resources = appContext.applicationContext.resources
    }

    fun typeface(family: FontFamily, bold: Boolean = false, italic: Boolean = false): Typeface =
        cache.getOrPut(Triple(family, bold, italic)) {
            val style = when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            // Fonts with one file get a synthesized bold and slant, as most editors do.
            Typeface.create(base(family), style)
        }

    private fun base(family: FontFamily): Typeface {
        val system = when (family) {
            FontFamily.SANS -> "sans-serif"
            FontFamily.SANS_LIGHT -> "sans-serif-light"
            FontFamily.SANS_CONDENSED -> "sans-serif-condensed"
            FontFamily.SANS_BLACK -> "sans-serif-black"
            FontFamily.SERIF -> "serif"
            FontFamily.MONOSPACE -> "monospace"
            FontFamily.CASUAL -> "casual"
            FontFamily.CURSIVE -> "cursive"
            else -> null
        }
        if (system != null) return Typeface.create(system, Typeface.NORMAL)
        val res = when (family) {
            FontFamily.LATO -> R.font.lato
            FontFamily.LOBSTER -> R.font.lobster
            FontFamily.PACIFICO -> R.font.pacifico
            FontFamily.BEBAS_NEUE -> R.font.bebas_neue
            FontFamily.PERMANENT_MARKER -> R.font.permanent_marker
            FontFamily.PRESS_START -> R.font.press_start_2p
            FontFamily.BANGERS -> R.font.bangers
            FontFamily.ABRIL_FATFACE -> R.font.abril_fatface
            FontFamily.SPECIAL_ELITE -> R.font.special_elite
            FontFamily.CREEPSTER -> R.font.creepster
            else -> R.font.great_vibes
        }
        return try {
            resources?.getFont(res) ?: Typeface.DEFAULT
        } catch (e: Exception) {
            // A missing font must never stop a project from opening or exporting.
            AppLog.w("Fonts", "Font $family could not load", e)
            Typeface.DEFAULT
        }
    }
}
