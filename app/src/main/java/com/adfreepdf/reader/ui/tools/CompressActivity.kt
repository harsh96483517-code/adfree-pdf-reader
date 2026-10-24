package com.adfreepdf.reader.ui.tools

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.adfreepdf.reader.R
import com.adfreepdf.reader.databinding.ActivityCompressBinding
import com.adfreepdf.reader.pdf.PdfTools
import com.adfreepdf.reader.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CompressActivity : AppCompatActivity() {

    private lateinit var b: ActivityCompressBinding
    private var uri: Uri? = null
    private var name = "document.pdf"

    private val pick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            uri = u; name = FileUtils.queryName(this, u); b.fileName.text = name
            runCatching { contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCompressBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.btnCompress.setOnClickListener { compress() }
        pick.launch(arrayOf("application/pdf"))
    }

    private fun compress() {
        val u = uri ?: return
        val q = b.quality.value.toInt()
        lifecycleScope.launch {
            Toast.makeText(this@CompressActivity, R.string.processing, Toast.LENGTH_SHORT).show()
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    val src = FileUtils.toCacheFile(this@CompressActivity, u, name)
                    val outName = "compressed_${System.currentTimeMillis()}.pdf"
                    FileUtils.saveToDownloads(this@CompressActivity, outName) { os ->
                        val tmp = java.io.File(cacheDir, "compressed_tmp.pdf")
                        PdfTools.compress(src, tmp, q)
                        tmp.inputStream().use { it.copyTo(os) }
                        tmp.delete()
                    }
                }.getOrNull()
            }
            if (out == null) Toast.makeText(this@CompressActivity, R.string.failed, Toast.LENGTH_LONG).show()
            else {
                Toast.makeText(this@CompressActivity, "Saved: ${out.name} (${FileUtils.humanSize(out.length())})", Toast.LENGTH_LONG).show()
                ToolResult.share(this@CompressActivity, out)
            }
        }
    }
}
