package io.github.drhydr0.layers.diagnostics

/** Which code and device produced a report, so it can be matched to a commit. */
data class BuildIdentity(
    val versionName: String,
    val versionCode: Int,
    val commitHash: String,
    val buildType: String,
    val device: String,
    val androidVersion: String,
) {
    fun lines(): List<String> = listOf(
        "Version: $versionName ($versionCode) $buildType",
        "Commit: $commitHash",
        "Device: $device",
        "Android: $androidVersion",
    )
}
