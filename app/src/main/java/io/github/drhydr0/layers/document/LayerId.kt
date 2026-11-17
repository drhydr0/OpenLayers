/**
 * Layer tree, transforms, groups, styles, crops, masks, and asset references.
 *
 * Plain Kotlin only: no android.* or androidx.* imports, so this package runs in JVM tests.
 */
package io.github.drhydr0.layers.document

import java.util.UUID

/**
 * Stable identity of a layer. Survives reordering, regrouping, save, and reload,
 * so list indices are never used to identify layers.
 */
@JvmInline
value class LayerId(val value: String) {
    init {
        require(value.isNotBlank()) { "LayerId must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun random(): LayerId = LayerId(UUID.randomUUID().toString())
    }
}
