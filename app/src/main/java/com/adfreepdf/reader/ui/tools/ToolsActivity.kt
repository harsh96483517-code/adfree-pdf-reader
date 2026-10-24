package com.adfreepdf.reader.ui.tools

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.adfreepdf.reader.R
import com.adfreepdf.reader.databinding.ActivityToolsBinding
import com.adfreepdf.reader.ui.edit.EditActivity
import com.adfreepdf.reader.util.FileUtils

class ToolsActivity : AppCompatActivity() {

    private val pickForEdit = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { u ->
        if (u != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    u, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            startActivity(
                Intent(this, EditActivity::class.java)
                    .setData(u)
                    .putExtra(EditActivity.EXTRA_NAME, FileUtils.queryName(this, u))
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityToolsBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setNavigationOnClickListener { finish() }

        val tools = listOf(
            Tool(R.drawable.ic_merge, getString(R.string.merge_pdfs), "Combine multiple PDFs") {
                startActivity(Intent(this, MergeActivity::class.java))
            },
            Tool(R.drawable.ic_split, getString(R.string.split_pdf), "Extract selected pages") {
                startActivity(Intent(this, SplitActivity::class.java))
            },
            Tool(R.drawable.ic_compress, getString(R.string.compress_pdf), "Reduce file size") {
                startActivity(Intent(this, CompressActivity::class.java))
            },
            Tool(R.drawable.ic_image_to_pdf, getString(R.string.image_to_pdf), "JPG/PNG to PDF") {
                startActivity(Intent(this, ImageToPdfActivity::class.java))
            },
            Tool(R.drawable.ic_edit, getString(R.string.edit_pdf), "Annotate & sign a page") {
                pickForEdit.launch(arrayOf("application/pdf"))
            }
        )
        b.toolList.layoutManager = GridLayoutManager(this, 2)
        b.toolList.adapter = ToolAdapter(tools)
    }
}
