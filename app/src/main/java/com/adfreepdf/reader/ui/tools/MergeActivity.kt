package com.adfreepdf.reader.ui.tools

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.adfreepdf.reader.R
import com.adfreepdf.reader.databinding.ActivityMergeBinding
import com.adfreepdf.reader.pdf.PdfTools
import com.adfreepdf.reader.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MergeActivity : AppCompatActivity() {

    private lateinit var b: ActivityMergeBinding
    private val picked = mutableListOf<Pair<Uri, String>>()
    private lateinit var adapter: MergeAdapter

    private val pick = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u ->
            runCatching {
                contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            picked.add(u to FileUtils.queryName(this, u))
        }
        adapter.notifyDataSetChanged()
        b.hint.text = if (picked.isEmpty()) getString(R.string.select_files)
        else "${picked.size} file(s) selected"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMergeBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setNavigationOnClickListener { finish() }
        adapter = MergeAdapter(picked) { }
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.btnMerge.setOnClickListener { merge() }
        pick.launch(arrayOf("application/pdf"))
    }

    private fun merge() {
        if (picked.size < 2) { Toast.makeText(this, "Pick at least 2 PDFs", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    val files = picked.map { FileUtils.toCacheFile(this@MergeActivity, it.first, it.second) }
                    val outName = "merged_${System.currentTimeMillis()}.pdf"
                    FileUtils.saveToDownloads(this@MergeActivity, outName) { os ->
                        // merge writes to a File, so merge to a temp then copy
                        val tmp = java.io.File(cacheDir, "merged_tmp.pdf")
                        PdfTools.merge(files, tmp)
                        tmp.inputStream().use { it.copyTo(os) }
                        tmp.delete()
                    }
                }.getOrNull()
            }
            if (out == null) Toast.makeText(this@MergeActivity, R.string.failed, Toast.LENGTH_LONG).show()
            else { Toast.makeText(this@MergeActivity, "Saved: ${out.name}", Toast.LENGTH_LONG).show(); ToolResult.share(this@MergeActivity, out) }
        }
    }
}
