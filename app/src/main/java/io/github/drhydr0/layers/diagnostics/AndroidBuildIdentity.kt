package io.github.drhydr0.layers.diagnostics

import android.os.Build
import io.github.drhydr0.layers.BuildConfig

fun currentBuildIdentity(): BuildIdentity = BuildIdentity(
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE,
    commitHash = BuildConfig.COMMIT_HASH,
    buildType = BuildConfig.BUILD_TYPE,
    device = "${Build.MANUFACTURER} ${Build.MODEL}",
    androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
)
