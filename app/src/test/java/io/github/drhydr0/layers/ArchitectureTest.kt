package io.github.drhydr0.layers

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Enforces CLAUDE.md: these packages stay plain Kotlin so they run in JVM tests. */
class ArchitectureTest {

    private val root = File("src/main/java/io/github/drhydr0/layers")
    private val pureDirs = listOf("document", "history", "persistence/format")
    private val forbidden = Regex("""^\s*import\s+(android|androidx)\.""", RegexOption.MULTILINE)

    @Test
    fun purePackagesHaveNoAndroidImports() {
        // Gradle runs unit tests from the module directory.
        assertTrue("source root not found: ${root.absolutePath}", root.isDirectory)
        val offenders = pureDirs
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { forbidden.containsMatchIn(it.readText()) }
            .map { it.relativeTo(root).path }
        assertTrue("Android imports in pure packages: $offenders", offenders.isEmpty())
    }

    @Test
    fun onlyAppLogUsesAndroidLog() {
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "AppLog.kt" }
            .filter { Regex("""^\s*import\s+android\.util\.Log\b""", RegexOption.MULTILINE).containsMatchIn(it.readText()) }
            .map { it.relativeTo(root).path }
            .toList()
        assertTrue("Use AppLog instead of android.util.Log: $offenders", offenders.isEmpty())
    }
}
