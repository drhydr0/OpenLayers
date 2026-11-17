/**
 * Screens, toolbars, sheets, layers panel, and accessibility.
 */
package io.github.drhydr0.layers.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import io.github.drhydr0.layers.BuildConfig
import io.github.drhydr0.layers.R

/** Start screen. Shows the build identity so a screenshot pins down the exact code. */
class StartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        findViewById<TextView>(R.id.version).text =
            getString(R.string.start_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        findViewById<TextView>(R.id.commit).text =
            getString(R.string.start_commit, BuildConfig.COMMIT_HASH)

        applySystemBarInsets(findViewById(R.id.root))
    }

    // targetSdk 35+ draws edge to edge, so content must be padded away from the system bars.
    private fun applySystemBarInsets(root: View) {
        val base = root.paddingLeft
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            view.setPadding(base + bars.left, base + bars.top, base + bars.right, base + bars.bottom)
            insets
        }
    }
}
