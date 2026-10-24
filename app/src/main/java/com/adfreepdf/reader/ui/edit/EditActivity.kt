package com.adfreepdf.reader.ui.edit

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.adfreepdf.reader.R
import com.adfreepdf.reader.databinding.ActivityEditBinding
import com.adfreepdf.reader.databinding.DialogSignatureBinding
import com.adfreepdf.reader.pdf.Anno
import com.adfreepdf.reader.pdf.AnnoType
import com.adfreepdf.reader.pdf.PdfTools
import com.adfreepdf.reader.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class EditActivity : AppCompatActivity() {

    private lateinit var b: ActivityEditBinding
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var srcFile: File? = null
    private var name = "document.pdf"
    private var pageCount = 0
    private var page = 0
    private var pageWpt = 0
    private var pageHpt = 0
    private var bmpW = 1
    private var bmpH = 1

    private val pageItems = HashMap<Int, MutableList<DrawingView.Item>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditBinding.inflate(layoutInflater)
        setContentView(b.root)

        val uri: Uri = intent.data ?: run { finish(); return }
        name = intent.getStringExtra(EXTRA_NAME) ?: FileUtils.queryName(this, uri)
        page = intent.getIntExtra(EXTRA_PAGE, 0)

        b.toolbar.setNavigationOnClickListener { finish() }

        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    srcFile = FileUtils.toCacheFile(this@EditActivity, uri, name)
                    val d = contentResolver.openFileDescriptor(uri, "r")!!
                    pfd = d
                    renderer = PdfRenderer(d)
                    pageCount = renderer!!.pageCount
                }.isSuccess
            }
            if (!ok) { Toast.makeText(this@EditActivity, R.string.failed, Toast.LENGTH_SHORT).show(); finish(); return@launch }
            wireTools()
            loadPage(page)
        }
    }

    private fun wireTools() {
        b.toolHighlight.setOnClickListener { setMode(DrawingView.Mode.HIGHLIGHT) }
        b.toolPen.setOnClickListener { setMode(DrawingView.Mode.PEN) }
        b.toolText.setOnClickListener { setMode(DrawingView.Mode.TEXT) }
        b.toolSignature.setOnClickListener { setMode(DrawingView.Mode.SIGNATURE) }
        b.toolEraser.setOnClickListener { setMode(DrawingView.Mode.ERASER) }
        b.toolUndo.setOnClickListener { b.drawView.undo() }
        b.toolClear.setOnClickListener { b.drawView.clearAll() }
        b.btnPrev.setOnClickListener { switchPage(page - 1) }
        b.btnNext.setOnClickListener { switchPage(page + 1) }
        b.btnSave.setOnClickListener { save() }

        b.drawView.onTextRequest = { at -> askText(at) }
        b.drawView.onSignatureRequest = { at -> askSignature(at) }
        setMode(DrawingView.Mode.PEN)
    }

    private fun setMode(m: DrawingView.Mode) {
        b.drawView.mode = m
        b.toolHighlight.isChecked = m == DrawingView.Mode.HIGHLIGHT
        b.toolPen.isChecked = m == DrawingView.Mode.PEN
        b.toolText.isChecked = m == DrawingView.Mode.TEXT
        b.toolSignature.isChecked = m == DrawingView.Mode.SIGNATURE
        b.toolEraser.isChecked = m == DrawingView.Mode.ERASER
    }

    private fun switchPage(target: Int) {
        if (target < 0 || target >= pageCount) return
        pageItems[page] = b.drawView.items().toMutableList()
        b.drawView.detach()
        page = target
        loadPage(page)
    }

    private fun loadPage(index: Int) {
        b.progress.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                runCatching {
                    val r = renderer!!
                    synchronized(r) {
                        r.openPage(index).use { p ->
                            pageWpt = p.width; pageHpt = p.height
                            val w = 1240
                            val h = (w * p.height.toFloat() / p.width.toFloat()).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }.getOrNull()
            }
            if (data == null) { b.progress.visibility = android.view.View.GONE; return@launch }
            bmpW = data.width; bmpH = data.height
            b.pageImage.setImageBitmap(data)
            b.drawView.restore(pageItems[index] ?: mutableListOf())
            b.progress.visibility = android.view.View.GONE
        }
    }

    // ---------- coordinate mapping ----------
    private fun fit(): FloatArray {
        val vw = b.drawView.width.toFloat().takeIf { it > 0 } ?: bmpW.toFloat()
        val vh = b.drawView.height.toFloat().takeIf { it > 0 } ?: bmpH.toFloat()
        val scale = minOf(vw / bmpW, vh / bmpH)
        val left = (vw - bmpW * scale) / 2f
        val top = (vh - bmpH * scale) / 2f
        return floatArrayOf(scale, left, top)
    }

    private fun viewToPdf(x: Float, y: Float): PointF {
        val (scale, left, top) = fit()
        val bx = (x - left) / scale
        val by = (y - top) / scale
        return PointF(bx * pageWpt / bmpW, by * pageHpt / bmpH)
    }

    // ---------- signature / text ----------
    private fun askText(at: PointF) {
        val input = android.widget.EditText(this).apply { hint = getString(R.string.text) }
        AlertDialog.Builder(this)
            .setTitle(R.string.text)
            .setView(input)
            .setPositiveButton(R.string.done) { _, _ ->
                if (input.text.isNotBlank()) b.drawView.addText(at, input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun askSignature(at: PointF) {
        val sb = DialogSignatureBinding.inflate(layoutInflater)
        sb.sigPad.mode = DrawingView.Mode.PEN
        sb.sigPad.color = Color.BLACK
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.draw_signature)
            .setView(sb.root)
            .setPositiveButton(R.string.done, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val bmp = capturePad(sb)
                if (bmp != null) {
                    val w = 160f; val h = 160f * bmp.height / bmp.width
                    b.drawView.addSignature(RectF(at.x, at.y, at.x + w, at.y + h), bmp)
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun capturePad(sb: DialogSignatureBinding): Bitmap? {
        val pad = sb.sigPad
        if (pad.width <= 0 || pad.height <= 0) return null
        val bmp = Bitmap.createBitmap(pad.width, pad.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val oldBg = pad.background
        pad.setBackgroundColor(Color.TRANSPARENT)
        pad.draw(canvas)
        pad.background = oldBg
        return bmp
    }

    // ---------- save ----------
    private fun save() {
        val src = srcFile ?: return
        pageItems[page] = b.drawView.items().toMutableList()

        lifecycleScope.launch {
            Toast.makeText(this@EditActivity, R.string.saving, Toast.LENGTH_SHORT).show()
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    val annos = ArrayList<Anno>()
                    pageItems.forEach { (pg, items) ->
                        items.forEach { item ->
                            when (item) {
                                is DrawingView.Stroke -> {
                                    val flat = ArrayList<Float>()
                                    item.pts.forEach { p ->
                                        val pdf = viewToPdf(p.x, p.y)
                                        flat.add(pdf.x); flat.add(pdf.y)
                                    }
                                    val (fs, _, _) = fit()
                                    val strokePts = (item.width / fs) * (pageWpt.toFloat() / bmpW)
                                    annos.add(
                                        Anno(
                                            page = pg,
                                            type = if (item.highlight) AnnoType.HIGHLIGHT else AnnoType.PEN,
                                            strokes = listOf(flat.toFloatArray()),
                                            color = item.color,
                                            stroke = strokePts
                                        )
                                    )
                                }
                                is DrawingView.TextItem -> {
                                    val pdf = viewToPdf(item.at.x, item.at.y)
                                    annos.add(
                                        Anno(
                                            page = pg, type = AnnoType.TEXT,
                                            rect = floatArrayOf(pdf.x, pdf.y, 0f, 0f),
                                            text = item.text, color = item.color
                                        )
                                    )
                                }
                                is DrawingView.SigItem -> {
                                    val tl = viewToPdf(item.rect.left, item.rect.top)
                                    val br = viewToPdf(item.rect.right, item.rect.bottom)
                                    val bos = ByteArrayOutputStream()
                                    item.bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                                    annos.add(
                                        Anno(
                                            page = pg, type = AnnoType.SIGNATURE,
                                            rect = floatArrayOf(
                                                tl.x, tl.y,
                                                (br.x - tl.x).coerceAtLeast(1f),
                                                (br.y - tl.y).coerceAtLeast(1f)
                                            ),
                                            image = bos.toByteArray(), color = item.color
                                        )
                                    )
                                }
                            }
                        }
                    }
                    val outName = "annotated_${System.currentTimeMillis()}.pdf"
                    val outFile = FileUtils.saveToDownloads(this@EditActivity, outName) { os ->
                        PdfTools.annotate(src, os, annos, 1f)
                    }
                    outFile
                }.getOrNull()
            }
            if (out == null) {
                Toast.makeText(this@EditActivity, R.string.failed, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@EditActivity, "Saved: ${out.name}", Toast.LENGTH_LONG).show()
                shareFile(out)
            }
        }
    }

    private fun shareFile(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, getString(R.string.share)))
        }
    }

    override fun onDestroy() {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_PAGE = "page"
    }
}
