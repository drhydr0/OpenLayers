plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// CI sets LAYERS_COMMIT to the PR head commit, because a PR checkout's HEAD is a
// temporary merge commit that would not match anything the developer can look up.
val commitHash: String = providers.environmentVariable("LAYERS_COMMIT")
    .orElse(
        providers.exec {
            commandLine("git", "rev-parse", "--short=12", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim() },
    )
    .map { it.take(12).ifEmpty { "unknown" } }
    .getOrElse("unknown")

// Every build must have a higher versionCode than the one it updates, or Android refuses
// the install. The commit count on main only grows, so it works without stored state.
val versionCodeFromGit: Int = providers.environmentVariable("LAYERS_VERSION_CODE")
    .orElse(
        providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim() },
    )
    .map { it.toIntOrNull() ?: 1 }
    .getOrElse(1)

// The release key lives only in GitHub Actions secrets. CI decodes it to a temp file and
// passes its path here. Without it (local builds), debug falls back to the default debug key.
val releaseKeystore: String? = providers.environmentVariable("LAYERS_KEYSTORE_PATH").orNull

android {
    namespace = "io.github.drhydr0.layers"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        // Never change applicationId: a new ID installs as a separate app and strands existing projects.
        applicationId = "io.github.drhydr0.layers"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = versionCodeFromGit
        versionName = "0.1.0"

        buildConfigField("String", "COMMIT_HASH", "\"$commitHash\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("layers") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("LAYERS_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("LAYERS_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("LAYERS_KEY_PASSWORD").get()
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        // Debug and release share the one stable key, so any CI-built APK installs as an
        // update over any other instead of forcing an uninstall that wipes projects.
        debug {
            signingConfigs.findByName("layers")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("layers")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    testImplementation(libs.junit)
}
