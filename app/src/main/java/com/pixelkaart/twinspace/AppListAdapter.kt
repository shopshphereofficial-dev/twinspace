package com.pixelkaart.twinspace

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val items: MutableList<AppInfo>,
    var clonedMode: Boolean,
    private val onAction: (AppInfo, String) -> Unit
) : RecyclerView.Adapter<AppListAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val pkg: TextView = view.findViewById(R.id.app_package)
        val more: View = view.findViewById(R.id.app_more)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.icon.setImageDrawable(item.icon)
        holder.name.text = item.label
        holder.pkg.text = item.packageName
        holder.itemView.alpha = if (item.frozen) 0.45f else 1f

        holder.itemView.setOnClickListener { if (clonedMode && !item.frozen) onAction(item, "open") }
        holder.more.setOnClickListener { anchor ->
            val popup = PopupMenu(anchor.context, anchor)
            if (clonedMode) {
                popup.menu.add(0, 1, 0, "Open")
                popup.menu.add(0, 2, 1, if (item.frozen) "Unfreeze" else "Freeze")
                popup.menu.add(0, 3, 2, "Uninstall")
            } else {
                popup.menu.add(0, 4, 0, "Clone to work space")
            }
            popup.setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    1 -> onAction(item, "open")
                    2 -> onAction(item, if (item.frozen) "unfreeze" else "freeze")
                    3 -> onAction(item, "uninstall")
                    4 -> onAction(item, "clone")
                }
                true
            }
            popup.show()
        }
    }

    fun submit(newItems: List<AppInfo>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
