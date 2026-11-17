package io.github.drhydr0.layers.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore

/** Opens Android's photo picker, which needs no storage permission. */
object ImagePicker {

    fun launch(activity: Activity, requestCode: Int) {
        val intent = if (hasPhotoPicker()) {
            Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
        } else {
            // Older Android 11/12 builds without the picker update: the system file chooser.
            Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        }
        activity.startActivityForResult(intent, requestCode)
    }

    fun result(resultCode: Int, data: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) data?.data else null

    private fun hasPhotoPicker() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2
}
