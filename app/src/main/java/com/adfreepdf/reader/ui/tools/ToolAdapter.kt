package com.adfreepdf.reader.ui.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.adfreepdf.reader.databinding.ItemToolBinding

data class Tool(val icon: Int, val label: String, val desc: String, val action: () -> Unit)

class ToolAdapter(private val tools: List<Tool>) : RecyclerView.Adapter<ToolAdapter.VH>() {
    class VH(val b: ItemToolBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemToolBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = tools.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = tools[position]
        holder.b.icon.setImageResource(t.icon)
        holder.b.label.text = t.label
        holder.b.desc.text = t.desc
        holder.b.root.setOnClickListener { t.action() }
    }
}
