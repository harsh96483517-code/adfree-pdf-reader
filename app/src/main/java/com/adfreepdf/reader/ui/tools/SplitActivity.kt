package com.adfreepdf.reader.ui.tools

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.adfreepdf.reader.R
import com.adfreepdf.reader.databinding.ActivitySplitBinding
import com.adfreepdf.reader.pdf.PdfTools
import com.adfreepdf.reader.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplitActivity : AppCompatActivity() {

    private lateinit var b: ActivitySplitBinding
    private var uri: Uri? = null
    private var name = "document.pdf"

    private val pick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            uri = u; name = FileUtils.queryName(this, u)
            b.fileName.text = name
            runCatching { contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySplitBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.btnSplit.setOnClickListener { split() }
        pick.launch(arrayOf("application/pdf"))
    }

    private fun split() {
        val u = uri ?: return
        val pages = parseRange(b.range.text?.toString().orEmpty())
        if (pages.isEmpty()) { Toast.makeText(this, "Enter pages like 1-3,5", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    val src = FileUtils.toCacheFile(this@SplitActivity, u, name)
                    val outName = "split_${System.currentTimeMillis()}.pdf"
                    FileUtils.saveToDownloads(this@SplitActivity, outName) { os ->
                        val tmp = java.io.File(cacheDir, "split_tmp.pdf")
                        PdfTools.split(src, tmp, pages)
                        tmp.inputStream().use { it.copyTo(os) }
                        tmp.delete()
                    }
                }.getOrNull()
            }
            if (out == null) Toast.makeText(this@SplitActivity, R.string.failed, Toast.LENGTH_LONG).show()
            else { Toast.makeText(this@SplitActivity, "Saved: ${out.name}", Toast.LENGTH_LONG).show(); ToolResult.share(this@SplitActivity, out) }
        }
    }

    private fun parseRange(text: String): List<Int> {
        val set = LinkedHashSet<Int>()
        text.split(",").forEach { part ->
            val p = part.trim()
            if (p.contains("-")) {
                val (a, b2) = p.split("-").mapNotNull { it.trim().toIntOrNull() }.let {
                    if (it.size == 2) it[0] to it[1] else return@forEach
                }
                if (a <= b2) for (i in a..b2) if (i > 0) set.add(i)
            } else p.toIntOrNull()?.let { if (it > 0) set.add(it) }
        }
        return set.toList().sorted()
    }
}
