package com.adfreepdf.reader.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.adfreepdf.reader.databinding.ItemPageBinding
import java.util.concurrent.Executors

/**
 * Renders one PDF page per item. PdfRenderer is not thread-safe, so all
 * rendering is funnelled through a single-thread executor.
 */
class PageAdapter(
    private val renderer: PdfRenderer,
    private val pageCount: Int,
    private val onPageSettled: (Int) -> Unit
) : RecyclerView.Adapter<PageAdapter.VH>() {

    private val executor = Executors.newSingleThreadExecutor()

    inner class VH(val b: ItemPageBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = pageCount

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = holder.b
        b.progress.isVisible = true
        b.pageImage.setImageBitmap(null)
        executor.execute {
            val bmp = runCatching { renderPage(renderer, position) }.getOrNull()
            b.root.post {
                if (holder.bindingAdapterPosition == position && bmp != null) {
                    b.pageImage.setImageBitmap(bmp)
                    b.progress.isVisible = false
                }
            }
        }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.b.pageImage.setImageBitmap(null)
    }

    fun shutdown() { executor.shutdownNow() }

    private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap {
        synchronized(renderer) {
            renderer.openPage(index).use { page ->
                val targetW = 1240
                val ratio = page.height.toFloat() / page.width.toFloat()
                val w = targetW
                val h = (targetW * ratio).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            }
        }
    }

    init {
        onPageSettled(-1)
    }
}
