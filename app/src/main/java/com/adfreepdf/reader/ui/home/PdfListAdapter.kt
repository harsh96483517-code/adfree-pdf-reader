package com.adfreepdf.reader.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.adfreepdf.reader.R
import com.adfreepdf.reader.data.PdfFile
import com.adfreepdf.reader.databinding.ItemPdfBinding
import com.adfreepdf.reader.util.FileUtils

class PdfListAdapter(
    private val onClick: (PdfFile) -> Unit,
    private val onMore: (PdfFile, android.view.View) -> Unit
) : ListAdapter<PdfFile, PdfListAdapter.VH>(DIFF) {

    class VH(val b: ItemPdfBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPdfBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = getItem(position)
        with(holder.b) {
            name.text = f.name
            val meta = buildString {
                append(FileUtils.humanSize(f.size))
                if (f.lastModified > 0) { append("  \u2022  "); append(FileUtils.date(f.lastModified)) }
            }
            this.meta.text = meta
            thumb.setImageResource(R.drawable.ic_pdf)
            root.setOnClickListener { onClick(f) }
            more.setOnClickListener { onMore(f, it) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PdfFile>() {
            override fun areItemsTheSame(a: PdfFile, b: PdfFile) = a.uri == b.uri
            override fun areContentsTheSame(a: PdfFile, b: PdfFile) = a == b
        }
    }
}
