package io.github.drhydr0.layers.document

/** Pure helpers for grouping, ungrouping, and copying layers. */
object Grouping {

    /**
     * Wraps [layers] (bottom first, all positioned in the same space) in a group placed at
     * [center]. Children are shifted so they stay exactly where they were.
     */
    fun group(id: LayerId, name: String, layers: List<Layer>, center: Vec2): GroupLayer = GroupLayer(
        id = id,
        name = name,
        children = layers.map { it.withTransform(it.transform.translatedBy(Vec2.ZERO - center)) },
        transform = Transform(position = center),
    )

    /**
     * The group's children as they appear from outside it, so removing the group leaves
     * them in place. Exact while the group is scaled evenly and not flipped, which is all
     * the editor's gestures produce.
     */
    fun ungroup(group: GroupLayer): List<Layer> = group.children.map { child ->
        val g = group.transform
        val c = child.transform
        val placed = child.withTransform(
            c.copy(
                position = g.toMatrix().map(c.position),
                scaleX = c.scaleX * g.scaleX,
                scaleY = c.scaleY * g.scaleY,
                rotationDegrees = normalize(c.rotationDegrees + g.rotationDegrees),
            ),
        )
        placed.withOpacity(child.opacity * group.opacity)
            .withVisible(child.visible && group.visible)
    }

    /**
     * An independent copy: every layer in the tree gets a new id from [newId]. Image bytes
     * are shared through their asset ids, which is safe because assets never change.
     */
    fun copy(layer: Layer, newId: () -> LayerId): Layer = when (layer) {
        is ImageLayer -> layer.copy(id = newId())
        is TextLayer -> layer.copy(id = newId())
        is GroupLayer -> layer.copy(id = newId(), children = layer.children.map { copy(it, newId) })
    }

    private fun normalize(degrees: Double): Double {
        var d = degrees % 360.0
        if (d <= -180.0) d += 360.0
        if (d > 180.0) d -= 360.0
        return d
    }
}
