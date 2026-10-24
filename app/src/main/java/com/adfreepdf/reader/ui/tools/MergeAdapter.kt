package com.adfreepdf.reader.ui.tools

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.adfreepdf.reader.databinding.ItemMergeBinding

class MergeAdapter(
    private val items: MutableList<Pair<Uri, String>>,
    private val onChange: () -> Unit
) : RecyclerView.Adapter<MergeAdapter.VH>() {

    class VH(val b: ItemMergeBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMergeBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (_, name) = items[position]
        holder.b.name.text = name
        holder.b.up.setOnClickListener { move(position, position - 1) }
        holder.b.down.setOnClickListener { move(position, position + 1) }
        holder.b.remove.setOnClickListener {
            if (position in items.indices) { items.removeAt(position); notifyDataSetChanged(); onChange() }
        }
    }

    private fun move(from: Int, to: Int) {
        if (to < 0 || to >= items.size) return
        val it = items.removeAt(from); items.add(to, it)
        notifyDataSetChanged(); onChange()
    }
}
