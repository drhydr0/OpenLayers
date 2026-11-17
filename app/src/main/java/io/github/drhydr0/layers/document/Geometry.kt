package io.github.drhydr0.layers.document

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A point or offset in canvas pixels. */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(factor: Double) = Vec2(x * factor, y * factor)

    companion object {
        val ZERO = Vec2(0.0, 0.0)
    }
}

/** Axis-aligned rectangle. Edges are inclusive for hit testing. */
data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width get() = right - left
    val height get() = bottom - top
    val center get() = Vec2((left + right) / 2, (top + bottom) / 2)
    val isEmpty get() = width <= 0 || height <= 0

    fun contains(point: Vec2) = point.x in left..right && point.y in top..bottom

    fun union(other: Rect) = Rect(
        min(left, other.left), min(top, other.top),
        max(right, other.right), max(bottom, other.bottom),
    )

    companion object {
        /** Rectangle of the given size centered on the origin: a layer's local content box. */
        fun centered(width: Double, height: Double) = Rect(-width / 2, -height / 2, width / 2, height / 2)

        fun bounding(points: List<Vec2>): Rect {
            require(points.isNotEmpty()) { "need at least one point" }
            return Rect(
                points.minOf { it.x }, points.minOf { it.y },
                points.maxOf { it.x }, points.maxOf { it.y },
            )
        }
    }
}

/**
 * 2D affine transform, mapping (x, y) to (a*x + c*y + tx, b*x + d*y + ty).
 * The same layout as android.graphics.Matrix's affine part, so the renderer copies it directly.
 */
data class Matrix(
    val a: Double = 1.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 1.0,
    val tx: Double = 0.0,
    val ty: Double = 0.0,
) {
    /** `this * other`: applies [other] first, then this. */
    operator fun times(other: Matrix) = Matrix(
        a = a * other.a + c * other.b,
        b = b * other.a + d * other.b,
        c = a * other.c + c * other.d,
        d = b * other.c + d * other.d,
        tx = a * other.tx + c * other.ty + tx,
        ty = b * other.tx + d * other.ty + ty,
    )

    fun map(p: Vec2) = Vec2(a * p.x + c * p.y + tx, b * p.x + d * p.y + ty)

    fun mapRect(r: Rect): Rect = Rect.bounding(
        listOf(
            map(Vec2(r.left, r.top)), map(Vec2(r.right, r.top)),
            map(Vec2(r.right, r.bottom)), map(Vec2(r.left, r.bottom)),
        ),
    )

    val determinant get() = a * d - b * c

    /** Null when the matrix collapses space (e.g. zero scale), so callers must handle it. */
    fun inverted(): Matrix? {
        val det = determinant
        if (det == 0.0 || det.isNaN()) return null
        return Matrix(
            a = d / det,
            b = -b / det,
            c = -c / det,
            d = a / det,
            tx = (c * ty - d * tx) / det,
            ty = (b * tx - a * ty) / det,
        )
    }

    companion object {
        val IDENTITY = Matrix()

        fun translate(dx: Double, dy: Double) = Matrix(tx = dx, ty = dy)

        fun scale(sx: Double, sy: Double) = Matrix(a = sx, d = sy)

        /** Clockwise on screen, because canvas y points down. */
        fun rotateDegrees(degrees: Double): Matrix {
            val rad = Math.toRadians(degrees)
            val cos = cos(rad)
            val sin = sin(rad)
            return Matrix(a = cos, b = sin, c = -sin, d = cos)
        }
    }
}

/** ARGB color packed like android.graphics.Color, so the renderer passes it through. */
@JvmInline
value class Color(val argb: Int) {
    val alpha get() = (argb ushr 24) and 0xFF
    val red get() = (argb shr 16) and 0xFF
    val green get() = (argb shr 8) and 0xFF
    val blue get() = argb and 0xFF

    companion object {
        val TRANSPARENT = Color(0)
        val BLACK = Color(0xFF000000.toInt())
        val WHITE = Color(0xFFFFFFFF.toInt())

        fun argb(alpha: Int, red: Int, green: Int, blue: Int): Color {
            listOf(alpha, red, green, blue).forEach { require(it in 0..255) { "component out of range: $it" } }
            return Color((alpha shl 24) or (red shl 16) or (green shl 8) or blue)
        }
    }
}
