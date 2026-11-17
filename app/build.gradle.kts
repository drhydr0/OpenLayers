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

android {
    namespace = "io.github.drhydr0.layers"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        // Never change applicationId: a new ID installs as a separate app and strands existing projects.
        applicationId = "io.github.drhydr0.layers"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "COMMIT_HASH", "\"$commitHash\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
