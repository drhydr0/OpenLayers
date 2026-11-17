package io.github.drhydr0.layers.persistence

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class AssetStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dir by lazy { File(temp.root, "assets") }
    private val store by lazy { AssetStore(dir) }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun storesBytesUnderTheirHash() {
        // Larger than the copy buffer, to cover multi-chunk hashing.
        val bytes = ByteArray(200_000) { (it * 31).toByte() }
        val id = store.put(bytes.inputStream())
        assertEquals(sha256(bytes), id.sha256)
        assertArrayEquals(bytes, store.file(id).readBytes())
    }

    @Test
    fun identicalImportsShareOneFile() {
        val a = store.put("same".byteInputStream())
        val b = store.put("same".byteInputStream())
        val c = store.put("different".byteInputStream())
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(2, dir.listFiles()!!.size)
    }

    @Test
    fun failedImportLeavesNothingBehind() {
        val failing = object : InputStream() {
            var count = 0
            override fun read(): Int = if (count++ < 10) 1 else throw IOException("source vanished")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (count++ > 2) throw IOException("source vanished")
                b[off] = 1
                return 1
            }
        }
        try {
            store.put(failing)
            throw AssertionError("expected failure")
        } catch (_: IOException) {
        }
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun startupCleanupRemovesOnlyTempFiles() {
        val id = store.put("keep".byteInputStream())
        File(dir, "import-crashed.tmp").writeText("partial")
        store.deleteTempFiles()
        assertEquals(listOf(id.sha256), dir.list()!!.toList())
    }
}
