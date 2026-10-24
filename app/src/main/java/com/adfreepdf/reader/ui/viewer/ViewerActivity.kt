package com.adfreepdf.reader.ui.viewer

import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.adfreepdf.reader.R
import com.adfreepdf.reader.data.PdfFile
import com.adfreepdf.reader.data.Prefs
import com.adfreepdf.reader.databinding.ActivityViewerBinding
import com.adfreepdf.reader.pdf.PdfTools
import com.adfreepdf.reader.ui.edit.EditActivity
import com.adfreepdf.reader.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ViewerActivity : AppCompatActivity() {

    private lateinit var b: ActivityViewerBinding
    private lateinit var prefs: Prefs
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var adapter: PageAdapter? = null
    private var uri: Uri? = null
    private var name: String = ""
    private var pageCount = 0
    private var current = 0
    private var cacheFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        uri = intent.data
        name = intent.getStringExtra(EXTRA_NAME) ?: uri?.let { FileUtils.queryName(this, it) } ?: "document.pdf"
        val startPage = intent.getIntExtra(EXTRA_PAGE, 0)

        b.toolbar.title = name
        b.toolbar.setNavigationOnClickListener { finish() }

        if (uri == null) { Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show(); finish(); return }

        openDocument(startPage)
        wireControls()
    }

    private fun openDocument(startPage: Int) {
        lifecycleScope.launch {
            val opened = withContext(Dispatchers.IO) {
                runCatching {
                    val descriptor = contentResolver.openFileDescriptor(uri!!, "r")!!
                    val r = PdfRenderer(descriptor)
                    cacheFile = runCatching { FileUtils.toCacheFile(this@ViewerActivity, uri!!, name) }.getOrNull()
                    descriptor to r
                }.getOrNull()
            }
            if (opened == null) {
                Toast.makeText(this@ViewerActivity, R.string.failed, Toast.LENGTH_SHORT).show(); finish(); return@launch
            }
            pfd = opened.first
            renderer = opened.second
            pageCount = opened.second.pageCount
            adapter = PageAdapter(opened.second, pageCount) { updateIndicator(it) }
            val lm = LinearLayoutManager(this@ViewerActivity, RecyclerView.HORIZONTAL, false)
            b.pager.layoutManager = lm
            b.pager.adapter = adapter
            PagerSnapHelper().attachToRecyclerView(b.pager)
            b.pager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val pos = lm.findFirstCompletelyVisibleItemPosition().takeIf { it >= 0 }
                            ?: lm.findFirstVisibleItemPosition()
                        current = pos; updateIndicator(pos)
                    }
                }
            })
            b.pager.scrollToPosition(startPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
            current = startPage
            updateIndicator(startPage)
        }
    }

    private fun wireControls() {
        b.prev.setOnClickListener { go(current - 1) }
        b.next.setOnClickListener { go(current + 1) }
        b.btnSearch.setOnClickListener { searchInDocument() }
        b.btnBookmark.setOnClickListener { toggleBookmark() }
        b.btnAnnotate.setOnClickListener { openEditor() }
    }

    private fun go(page: Int) {
        val p = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        b.pager.smoothScrollToPosition(p)
        current = p; updateIndicator(p)
    }

    private fun updateIndicator(page: Int) {
        if (page < 0) return
        b.pageIndicator.text = getString(R.string.page) + " ${page + 1} / $pageCount"
        b.btnBookmark.setImageResource(
            if (prefs.isBookmarked(bookmarkKey(page))) R.drawable.ic_bookmark_filled
            else R.drawable.ic_bookmark_outline
        )
    }

    private fun bookmarkKey(page: Int) = "${uri}#${page + 1}"

    private fun toggleBookmark() {
        val added = prefs.toggleBookmark(bookmarkKey(current))
        Toast.makeText(
            this,
            if (added) R.string.bookmark_added else R.string.bookmark_removed,
            Toast.LENGTH_SHORT
        ).show()
        updateIndicator(current)
    }

    private fun searchInDocument() {
        val file = cacheFile
        if (file == null) { Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show(); return }
        val input = android.widget.EditText(this).apply { hint = getString(R.string.search_in_pdf) }
        AlertDialog.Builder(this)
            .setTitle(R.string.search_in_pdf)
            .setView(input)
            .setPositiveButton(R.string.done) { _, _ -> runSearch(file, input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runSearch(file: File, q: String) {
        if (q.isBlank()) return
        lifecycleScope.launch {
            val pages = withContext(Dispatchers.IO) {
                runCatching {
                    PdfTools.extractText(file).mapIndexedNotNull { i, t ->
                        if (t.contains(q, true)) i else null
                    }
                }.getOrDefault(emptyList())
            }
            if (pages.isEmpty()) {
                Toast.makeText(this@ViewerActivity, R.string.no_matches, Toast.LENGTH_SHORT).show()
            } else if (pages.size == 1) {
                go(pages.first())
            } else {
                val labels = pages.map { "Page ${it + 1}" }.toTypedArray()
                AlertDialog.Builder(this@ViewerActivity)
                    .setTitle(R.string.search_in_pdf)
                    .setItems(labels) { _, which -> go(pages[which]) }
                    .show()
            }
        }
    }

    private fun openEditor() {
        val u = uri ?: return
        startActivity(
            Intent(this, EditActivity::class.java)
                .setData(u)
                .putExtra(EditActivity.EXTRA_NAME, name)
                .putExtra(EditActivity.EXTRA_PAGE, current)
        )
    }

    override fun onDestroy() {
        adapter?.shutdown()
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_PAGE = "page"
    }
}
