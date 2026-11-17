package io.github.drhydr0.layers.ui

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.document.Document
import io.github.drhydr0.layers.document.GroupLayer
import io.github.drhydr0.layers.document.ImageLayer
import io.github.drhydr0.layers.document.Layer
import io.github.drhydr0.layers.document.LayerId
import io.github.drhydr0.layers.document.ShapeLayer
import io.github.drhydr0.layers.document.TextLayer

/**
 * The layers list: top of the stack first, groups as expandable rows with their children
 * indented below. Selecting here works even for layers that are covered, off the canvas,
 * or inside a group. Reordering among siblings commits one command when the row is dropped.
 */
class LayersPanel(
    private val list: RecyclerView,
    private val onSelect: (LayerId) -> Unit,
    private val onMove: (LayerId, toDocIndex: Int) -> Unit,
    private val onToggleVisible: (Layer) -> Unit,
    private val onToggleLocked: (Layer) -> Unit,
    private val onRename: (Layer) -> Unit,
    private val onDelete: (Layer) -> Unit,
) {
    private var document: Document? = null
    private var rows: MutableList<LayerRow> = mutableListOf()
    private var selectedIds: Set<LayerId> = emptySet()
    private val expanded = mutableSetOf<LayerId>()
    private var dragFrom = -1
    private val adapter = Adapter()
    private val indentPx = (INDENT_DP * list.resources.displayMetrics.density).toInt()

    private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0,
    ) {
        // A row can only trade places with its own siblings; group membership changes
        // through Group and Ungroup.
        override fun canDropOver(rv: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val a = rows.getOrNull(current.bindingAdapterPosition) ?: return false
            val b = rows.getOrNull(target.bindingAdapterPosition) ?: return false
            val targetOpen = b.layer is GroupLayer && b.layer.id in expanded
            return a.parentId == b.parentId && a.depth == b.depth && !targetOpen
        }

        override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
            val a = from.bindingAdapterPosition
            val b = to.bindingAdapterPosition
            if (dragFrom < 0) dragFrom = a
            rows.add(b, rows.removeAt(a))
            adapter.notifyItemMoved(a, b)
            return true
        }

        override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit

        // Dragging starts from the handle only, so scrolling the list never reorders it.
        override fun isLongPressDragEnabled() = false

        override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
            super.clearView(rv, holder)
            val to = holder.bindingAdapterPosition
            val moved = dragFrom >= 0 && to >= 0 && to != dragFrom
            dragFrom = -1
            if (moved) onMove(rows[to].layer.id, LayerRows.documentIndex(rows, to)) else refresh()
        }
    })

    init {
        list.layoutManager = LinearLayoutManager(list.context)
        list.adapter = adapter
        touchHelper.attachToRecyclerView(list)
    }

    fun show(document: Document, selected: Set<LayerId>) {
        this.document = document
        selectedIds = selected
        // Open the groups around a selected layer, so the selection is always visible.
        selected.forEach { id -> document.ancestors(id)?.forEach { expanded += it.id } }
        refresh()
    }

    private fun refresh() {
        // Don't rebuild under a finger mid-drag; the drop commits and refreshes.
        if (dragFrom >= 0) return
        val doc = document ?: return
        rows = LayerRows.flatten(doc, expanded).toMutableList()
        @Suppress("NotifyDataSetChanged") // Layer lists are rebuilt from immutable snapshots.
        adapter.notifyDataSetChanged()
    }

    private fun toggleExpanded(id: LayerId) {
        if (!expanded.add(id)) expanded.remove(id)
        refresh()
    }

    private inner class Adapter : RecyclerView.Adapter<Row>() {
        init {
            setHasStableIds(true)
        }

        override fun getItemCount() = rows.size
        override fun getItemId(position: Int) = rows[position].layer.id.value.hashCode().toLong()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Row(LayoutInflater.from(parent.context).inflate(R.layout.item_layer, parent, false))
        override fun onBindViewHolder(holder: Row, position: Int) = holder.bind(rows[position])
    }

    private inner class Row(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageButton = view.findViewById(R.id.layer_icon)
        private val name: TextView = view.findViewById(R.id.layer_name)
        private val detail: TextView = view.findViewById(R.id.layer_detail)
        private val handle: ImageView = view.findViewById(R.id.layer_drag)
        private val more: ImageButton = view.findViewById(R.id.layer_more)
        private val eye: ImageButton = view.findViewById(R.id.layer_visible)
        private val lock: ImageButton = view.findViewById(R.id.layer_lock)
        private val basePadding = view.paddingStart

        @android.annotation.SuppressLint("ClickableViewAccessibility") // Move up/down in the menu is the accessible route.
        fun bind(row: LayerRow) {
            val layer = row.layer
            val context = itemView.context
            itemView.setPaddingRelative(basePadding + row.depth * indentPx, 0, 0, 0)
            name.text = layer.name
            bindKind(layer)
            // Hidden layers read as dimmed, so the list mirrors what the canvas shows.
            val dim = if (layer.visible) 1f else HIDDEN_ALPHA
            name.alpha = dim
            detail.alpha = dim
            icon.alpha = dim
            eye.setImageResource(if (layer.visible) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
            eye.contentDescription = context.getString(
                if (layer.visible) R.string.layers_hide else R.string.layers_show, layer.name,
            )
            eye.setOnClickListener { onToggleVisible(layer) }
            lock.setImageResource(if (layer.locked) R.drawable.ic_lock else R.drawable.ic_lock_open)
            lock.alpha = if (layer.locked) 1f else UNLOCKED_ALPHA
            lock.contentDescription = context.getString(
                if (layer.locked) R.string.layers_unlock else R.string.layers_lock, layer.name,
            )
            lock.setOnClickListener { onToggleLocked(layer) }
            itemView.isActivated = layer.id in selectedIds
            itemView.contentDescription = context.getString(R.string.layers_select_description, layer.name)
            itemView.setOnClickListener { onSelect(layer.id) }
            handle.contentDescription = context.getString(R.string.layers_drag_description, layer.name)
            handle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    // An open group's children would be left behind by the drag: close it first.
                    if (layer.id in expanded) toggleExpanded(layer.id) else touchHelper.startDrag(this)
                }
                false
            }
            more.contentDescription = context.getString(R.string.layers_more_description, layer.name)
            more.setOnClickListener { showMenu(it, row) }
        }

        private fun bindKind(layer: Layer) {
            val context = itemView.context
            icon.isClickable = layer is GroupLayer
            icon.setOnClickListener(null)
            icon.contentDescription = null
            icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            when (layer) {
                is ImageLayer -> {
                    icon.setImageResource(R.drawable.ic_layer_image)
                    detail.setText(R.string.layers_kind_image)
                }
                is TextLayer -> {
                    icon.setImageResource(R.drawable.ic_layer_text)
                    detail.text = layer.text.lineSequence().first().take(PREVIEW_CHARS)
                }
                is ShapeLayer -> {
                    icon.setImageResource(R.drawable.ic_layer_shape)
                    detail.setText(R.string.layers_kind_shape)
                }
                is GroupLayer -> {
                    val open = layer.id in expanded
                    icon.setImageResource(if (open) R.drawable.ic_group_open else R.drawable.ic_group)
                    detail.text = context.resources.getQuantityString(
                        R.plurals.layers_group_count, layer.children.size, layer.children.size,
                    )
                    icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    icon.contentDescription = context.getString(
                        if (open) R.string.layers_collapse else R.string.layers_expand, layer.name,
                    )
                    icon.setOnClickListener { toggleExpanded(layer.id) }
                }
            }
        }

        private fun showMenu(anchor: View, row: LayerRow) {
            val layer = row.layer
            val position = bindingAdapterPosition
            if (position < 0) return
            val index = LayerRows.documentIndex(rows, position)
            val siblingCount = document?.children(row.parentId)?.size ?: return
            PopupMenu(anchor.context, anchor).apply {
                if (index < siblingCount - 1) menu.add(0, MENU_UP, 0, R.string.layers_move_up)
                if (index > 0) menu.add(0, MENU_DOWN, 1, R.string.layers_move_down)
                menu.add(0, MENU_RENAME, 2, R.string.layers_rename)
                menu.add(0, MENU_DELETE, 3, R.string.layers_delete)
                setOnMenuItemClickListener {
                    when (it.itemId) {
                        MENU_UP -> onMove(layer.id, index + 1)
                        MENU_DOWN -> onMove(layer.id, index - 1)
                        MENU_RENAME -> onRename(layer)
                        MENU_DELETE -> onDelete(layer)
                    }
                    true
                }
            }.show()
        }
    }

    private companion object {
        const val PREVIEW_CHARS = 40
        const val HIDDEN_ALPHA = 0.4f
        const val UNLOCKED_ALPHA = 0.5f
        const val INDENT_DP = 20
        const val MENU_UP = 1
        const val MENU_DOWN = 2
        const val MENU_RENAME = 3
        const val MENU_DELETE = 4
    }
}
