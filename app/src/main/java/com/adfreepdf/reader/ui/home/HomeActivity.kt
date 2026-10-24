package com.adfreepdf.reader.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.adfreepdf.reader.R
import com.adfreepdf.reader.data.PdfFile
import com.adfreepdf.reader.data.PdfRepository
import com.adfreepdf.reader.data.Prefs
import com.adfreepdf.reader.databinding.ActivityHomeBinding
import com.adfreepdf.reader.ui.tools.ToolsActivity
import com.adfreepdf.reader.ui.viewer.ViewerActivity
import com.adfreepdf.reader.util.FileUtils
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityHomeBinding
    private lateinit var prefs: Prefs
    private var all: List<PdfFile> = emptyList()
    private var tab = 0
    private var query = ""

    private val adapter = PdfListAdapter(
        onClick = { openViewer(it, 0) },
        onMore = { file, view -> showFileMenu(file, view) }
    )

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = FileUtils.queryName(this, uri)
            openViewer(PdfFile(uri.toString(), name, FileUtils.querySize(this, uri), 0L), 0)
        }
    }

    private val askPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { load() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        b.tabs.addTab(b.tabs.newTab().setText(R.string.tab_files))
        b.tabs.addTab(b.tabs.newTab().setText(R.string.tab_recent))
        b.tabs.addTab(b.tabs.newTab().setText(R.string.tab_bookmarks))
        b.tabs.addTab(b.tabs.newTab().setText(R.string.tab_tools))
        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) {
                if (t.position == 3) {
                    startActivity(Intent(this@HomeActivity, ToolsActivity::class.java))
                    b.tabs.getTabAt(tab)?.select()
                    return
                }
                tab = t.position
                refresh()
            }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })

        b.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, bb: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, bb: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                query = s?.toString().orEmpty(); refresh()
            }
        })

        b.fab.setOnClickListener {
            pickPdf.launch(arrayOf("application/pdf"))
        }

        b.toolbar.inflateMenu(R.menu.home)
        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sort -> { showSortDialog(); true }
                else -> false
            }
        }

        ensurePermissions()
    }

    override fun onResume() { super.onResume(); if (::b.isInitialized) refresh() }

    private fun ensurePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val needed = perms.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) askPermissions.launch(needed.toTypedArray()) else load()
    }

    private fun load() {
        lifecycleScope.launch {
            all = withContext(Dispatchers.IO) { PdfRepository.scan(this@HomeActivity) }
            refresh()
        }
    }

    private fun refresh() {
        val src = when (tab) {
            1 -> prefs.recents()
            2 -> {
                val keys = prefs.bookmarks().mapNotNull { it.substringBeforeLast('#') }.distinct()
                all.filter { it.uri in keys }
            }
            else -> all
        }
        val filtered = if (query.isBlank()) src else src.filter { it.name.contains(query, true) }
        adapter.submitList(filtered)
        b.empty.isVisible = filtered.isEmpty()
        b.list.isVisible = filtered.isNotEmpty()
        b.emptyTitle.text = when (tab) {
            1 -> "No recent files"
            2 -> "No bookmarks yet"
            else -> getString(R.string.no_files)
        }
    }

    private fun openViewer(file: PdfFile, page: Int) {
        prefs.addRecent(file)
        startActivity(
            Intent(this, ViewerActivity::class.java)
                .setData(Uri.parse(file.uri))
                .putExtra(ViewerActivity.EXTRA_NAME, file.name)
                .putExtra(ViewerActivity.EXTRA_PAGE, page)
        )
    }

    private fun showSearchDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.search_hint); setText(query)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.search_hint)
            .setView(input)
            .setPositiveButton(R.string.done) { _, _ -> query = input.text.toString(); refresh() }
            .setNegativeButton(R.string.cancel) { _, _ -> query = ""; refresh() }
            .show()
    }

    private fun showSortDialog() {
        val options = arrayOf(getString(R.string.sort_name), getString(R.string.sort_date), getString(R.string.sort_size))
        AlertDialog.Builder(this)
            .setTitle(R.string.sort)
            .setSingleChoiceItems(options, prefs.sortMode) { d, which ->
                prefs.sortMode = which
                all = when (which) {
                    Prefs.SORT_DATE -> all.sortedByDescending { it.lastModified }
                    Prefs.SORT_SIZE -> all.sortedByDescending { it.size }
                    else -> all.sortedBy { it.name.lowercase() }
                }
                refresh(); d.dismiss()
            }.show()
    }

    private fun showFileMenu(file: PdfFile, anchor: android.view.View) {
        val items = arrayOf(getString(R.string.share), getString(R.string.rename), getString(R.string.delete))
        androidx.appcompat.widget.PopupMenu(this, anchor).apply {
            items.forEachIndexed { i, s -> menu.add(0, i, i, s) }
            setOnMenuItemClickListener { m ->
                when (m.itemId) {
                    0 -> share(file)
                    1 -> rename(file)
                    else -> confirmDelete(file)
                }
                true
            }
            show()
        }
    }

    private fun share(file: PdfFile) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(file.uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, getString(R.string.share)))
    }

    private fun rename(file: PdfFile) {
        val input = android.widget.EditText(this).apply { setText(file.name) }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename).setView(input)
            .setPositiveButton(R.string.done) { _, _ ->
                android.widget.Toast.makeText(this, "Rename not supported for shared storage", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun confirmDelete(file: PdfFile) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(file.name)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching { contentResolver.delete(Uri.parse(file.uri), null, null) }.isSuccess
                    }
                    android.widget.Toast.makeText(
                        this@HomeActivity,
                        if (ok) R.string.success else R.string.failed,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    load()
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }
}
