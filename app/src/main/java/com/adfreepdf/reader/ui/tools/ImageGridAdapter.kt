package com.adfreepdf.reader.ui.tools

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.adfreepdf.reader.databinding.ItemImageBinding

class ImageGridAdapter(
    private val items: MutableList<Uri>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ImageGridAdapter.VH>() {

    class VH(val b: ItemImageBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.b.image.load(items[position])
        holder.b.root.setOnClickListener { onRemove(position) }
    }
}
