package com.adfreepdf.reader.ui.tools

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.adfreepdf.reader.R
import com.adfreepdf.reader.databinding.ActivityImageToPdfBinding
import com.adfreepdf.reader.pdf.PdfTools
import com.adfreepdf.reader.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageToPdfActivity : AppCompatActivity() {

    private lateinit var b: ActivityImageToPdfBinding
    private val images = mutableListOf<Uri>()
    private lateinit var adapter: ImageGridAdapter

    private val pick = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        images.addAll(uris)
        adapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityImageToPdfBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setNavigationOnClickListener { finish() }
        adapter = ImageGridAdapter(images) { pos ->
            if (pos in images.indices) { images.removeAt(pos); adapter.notifyDataSetChanged() }
        }
        b.grid.layoutManager = GridLayoutManager(this, 3)
        b.grid.adapter = adapter
        b.fabCreate.setOnClickListener { create() }
        pick.launch(arrayOf("image/*"))
    }

    private fun create() {
        if (images.isEmpty()) { Toast.makeText(this, "Pick at least one image", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    val files = images.map { FileUtils.toCacheFile(this@ImageToPdfActivity, it) }
                    val outName = "images_${System.currentTimeMillis()}.pdf"
                    FileUtils.saveToDownloads(this@ImageToPdfActivity, outName) { os ->
                        val tmp = java.io.File(cacheDir, "img_tmp.pdf")
                        PdfTools.imagesToPdf(files, tmp)
                        tmp.inputStream().use { it.copyTo(os) }
                        tmp.delete()
                    }
                }.getOrNull()
            }
            if (out == null) Toast.makeText(this@ImageToPdfActivity, R.string.failed, Toast.LENGTH_LONG).show()
            else { Toast.makeText(this@ImageToPdfActivity, "Saved: ${out.name}", Toast.LENGTH_LONG).show(); ToolResult.share(this@ImageToPdfActivity, out) }
        }
    }
}
