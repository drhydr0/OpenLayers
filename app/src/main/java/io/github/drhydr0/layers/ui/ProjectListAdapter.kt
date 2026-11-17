package io.github.drhydr0.layers.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.drhydr0.layers.R
import io.github.drhydr0.layers.persistence.format.ProjectInfo

class ProjectListAdapter(
    private val onOpen: (ProjectInfo) -> Unit,
    private val onRename: (ProjectInfo) -> Unit,
    private val onDelete: (ProjectInfo) -> Unit,
) : RecyclerView.Adapter<ProjectListAdapter.Row>() {

    private var projects: List<ProjectInfo> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun submit(newProjects: List<ProjectInfo>) {
        projects = newProjects
        // Lists are small and change rarely; a full refresh keeps this simple.
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount() = projects.size

    // Project ids are UUID strings; hashing them gives RecyclerView a stable row identity.
    override fun getItemId(position: Int) = projects[position].id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Row(LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false))

    override fun onBindViewHolder(holder: Row, position: Int) = holder.bind(projects[position])

    inner class Row(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.project_name)
        private val edited: TextView = view.findViewById(R.id.project_edited)
        private val more: ImageButton = view.findViewById(R.id.project_more)

        fun bind(info: ProjectInfo) {
            val context = itemView.context
            name.text = info.name
            edited.text = context.getString(
                R.string.projects_edited,
                DateUtils.getRelativeTimeSpanString(info.modifiedAtMillis),
            )
            itemView.contentDescription = context.getString(R.string.projects_open_description, info.name)
            itemView.setOnClickListener { onOpen(info) }
            more.contentDescription = context.getString(R.string.projects_more_description, info.name)
            more.setOnClickListener { showMenu(it, info) }
        }

        private fun showMenu(anchor: View, info: ProjectInfo) {
            PopupMenu(anchor.context, anchor).apply {
                menu.add(0, MENU_RENAME, 0, R.string.projects_rename)
                menu.add(0, MENU_DELETE, 1, R.string.projects_delete)
                setOnMenuItemClickListener {
                    when (it.itemId) {
                        MENU_RENAME -> onRename(info)
                        MENU_DELETE -> onDelete(info)
                    }
                    true
                }
            }.show()
        }
    }

    private companion object {
        const val MENU_RENAME = 1
        const val MENU_DELETE = 2
    }
}
