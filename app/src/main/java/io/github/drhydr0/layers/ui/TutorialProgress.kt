package io.github.drhydr0.layers.ui

import android.content.Context

/**
 * Which project the tour runs in and how far it got, so leaving the editor (the photo
 * picker, a rotation, closing the app) resumes the tour where it was.
 */
class TutorialProgress(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The tour's project, or null when no tour is running. */
    val projectId: String? get() = prefs.getString(KEY_PROJECT, null)

    val step: Int get() = prefs.getInt(KEY_STEP, 0)

    /** True once the tour has been offered, so first launch asks only once. */
    var offered: Boolean
        get() = prefs.getBoolean(KEY_OFFERED, false)
        set(value) = prefs.edit().putBoolean(KEY_OFFERED, value).apply()

    fun start(projectId: String) = prefs.edit().putString(KEY_PROJECT, projectId).putInt(KEY_STEP, 0).apply()

    fun saveStep(step: Int) = prefs.edit().putInt(KEY_STEP, step).apply()

    fun end() = prefs.edit().remove(KEY_PROJECT).remove(KEY_STEP).apply()

    private companion object {
        const val PREFS = "tutorial"
        const val KEY_PROJECT = "project"
        const val KEY_STEP = "step"
        const val KEY_OFFERED = "offered"
    }
}
