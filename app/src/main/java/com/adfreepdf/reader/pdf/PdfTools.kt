package com.adfreepdf.reader.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

enum class AnnoType { HIGHLIGHT, PEN, TEXT, SIGNATURE }

/**
 * One annotation in PDF-point coordinates (origin top-left), ready to be
 * flattened onto the page.
 */
data class Anno(
    val page: Int,
    val type: AnnoType,
    val rect: FloatArray? = null,          // [x, y, w, h]
    val strokes: List<FloatArray> = emptyList(), // freehand points [x, y, ...]
    val text: String? = null,
    val image: ByteArray? = null,
    val color: Int = Color.YELLOW,
    val stroke: Float = 2f
)

/**
 * All PDF processing lives here. Pure Java/PDFBox - no ads, no network,
 * no third-party tracking. Everything runs on-device.
 */
object PdfTools {

    // ---------------- Merge ----------------

    fun merge(inputs: List<File>, out: File) {
        val merger = PDFMergerUtility()
        inputs.forEach { merger.addSource(it) }
        FileOutputStream(out).use { fos ->
            merger.destinationStream = fos
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        }
    }

    // ---------------- Split ----------------

    /** [pages] are 1-based page numbers to extract into a new PDF. */
    fun split(src: File, out: File, pages: List<Int>) {
        PDDocument.load(src).use { doc ->
            PDDocument().use { result ->
                pages.forEach { p ->
                    val idx = p - 1
                    if (idx in 0 until doc.numberOfPages) {
                        result.importPage(doc.getPage(idx))
                    }
                }
                result.save(out)
            }
        }
    }

    fun pageCount(src: File): Int = PDDocument.load(src).use { it.numberOfPages }

    // ---------------- Compress (rasterise + re-encode) ----------------

    fun compress(src: File, out: File, quality: Int) {
        // quality 10..100 -> render DPI 60..150, JPEG quality 30..90
        val q = quality.coerceIn(10, 100)
        val dpi = 60f + (q - 10) * (150f - 60f) / 90f
        val jpegQuality = 30 + (q - 10) * (90 - 30) / 90

        PDDocument.load(src).use { doc ->
            val renderer = PDFRenderer(doc)
            PDDocument().use { result ->
                for (i in 0 until doc.numberOfPages) {
                    val bmp = renderer.renderImageWithDPI(i, dpi)
                    val bytes = bitmapToJpeg(bmp, jpegQuality)
                    bmp.recycle()
                    val img = PDImageXObject.createFromByteArray(result, bytes, "p$i")
                    val page = PDPage(PDRectangle(img.width.toFloat(), img.height.toFloat()))
                    result.addPage(page)
                    PDPageContentStream(result, page).use { cs ->
                        cs.drawImage(img, 0f, 0f, img.width.toFloat(), img.height.toFloat())
                    }
                }
                result.save(out)
            }
        }
    }

    // ---------------- Images -> PDF ----------------

    fun imagesToPdf(images: List<File>, out: File) {
        PDDocument().use { doc ->
            images.forEachIndexed { i, file ->
                val bmp = decodeScaled(file, 1600) ?: return@forEachIndexed
                val bytes = bitmapToJpeg(bmp, 85)
                bmp.recycle()
                val img = PDImageXObject.createFromByteArray(doc, bytes, "img$i")
                val pageW = 595f
                val pageH = pageW * img.height.toFloat() / img.width.toFloat()
                val page = PDPage(PDRectangle(pageW, pageH))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(img, 0f, 0f, pageW, pageH)
                }
            }
            doc.save(out)
        }
    }

    // ---------------- Text extraction / search ----------------

    fun extractText(src: File): List<String> {
        PDDocument.load(src).use { doc ->
            val out = ArrayList<String>(doc.numberOfPages)
            for (i in 1..doc.numberOfPages) {
                val stripper = PDFTextStripper()
                stripper.startPage = i
                stripper.endPage = i
                out.add(stripper.getText(doc))
            }
            return out
        }
    }

    // ---------------- Annotate / sign (flatten onto the PDF) ----------------

    /**
     * Flattens [annos] onto a copy of [src]. [renderScale] is the pixels-per-point
     * the page was displayed at, used to convert screen coordinates back to PDF points.
     */
    fun annotate(src: File, out: File, annos: List<Anno>, renderScale: Float) {
        PDDocument.load(src).use { doc ->
            val scale = if (renderScale <= 0f) 1f else renderScale
            annos.groupBy { it.page }.forEach { (pageIdx, list) ->
                if (pageIdx !in 0 until doc.numberOfPages) return@forEach
                val page = doc.getPage(pageIdx)
                val pageH = page.mediaBox.height
                PDPageContentStream(
                    doc, page,
                    PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    list.forEach { a ->
                        when (a.type) {
                            AnnoType.HIGHLIGHT -> drawHighlight(cs, a, pageH, scale)
                            AnnoType.PEN -> drawStrokes(cs, a, pageH, scale)
                            AnnoType.TEXT -> drawText(cs, a, pageH, scale)
                            AnnoType.SIGNATURE -> drawSignature(doc, cs, a, pageH, scale)
                        }
                    }
                }
            }
            doc.save(out)
        }
    }

    private fun drawHighlight(cs: PDPageContentStream, a: Anno, pageH: Float, scale: Float) {
        val gs = PDExtendedGraphicsState().apply {
            nonStrokingAlphaConstant = 0.40f
            strokingAlphaConstant = 0.40f
            alphaSourceFlag = true
        }
        cs.setGraphicsStateParameters(gs)
        setFill(cs, a.color)
        cs.setStrokingColor(red(a.color), green(a.color), blue(a.color))
        cs.setLineWidth((a.stroke / scale).coerceAtLeast(6f))
        cs.setLineCapStyle(1)
        cs.setLineJoinStyle(1)
        if (a.strokes.isNotEmpty()) {
            a.strokes.forEach { s -> drawPolyline(cs, s, pageH, scale) }
        } else {
            val r = a.rect ?: return
            val x = r[0] / scale
            val w = r[2] / scale
            val h = r[3] / scale
            val y = pageH - (r[1] + r[3]) / scale
            cs.addRect(x, y, w, h)
            cs.fill()
        }
        val reset = PDExtendedGraphicsState().apply {
            nonStrokingAlphaConstant = 1f
            strokingAlphaConstant = 1f
            alphaSourceFlag = true
        }
        cs.setGraphicsStateParameters(reset)
    }

    private fun drawStrokes(cs: PDPageContentStream, a: Anno, pageH: Float, scale: Float) {
        cs.setStrokingColor(red(a.color), green(a.color), blue(a.color))
        cs.setLineWidth((a.stroke / scale).coerceAtLeast(0.5f))
        cs.setLineCapStyle(1)
        cs.setLineJoinStyle(1)
        a.strokes.forEach { s -> drawPolyline(cs, s, pageH, scale) }
    }

    /** Draws one freehand stroke; [s] is a flat [x0,y0,x1,y1,...] array in PDF points. */
    private fun drawPolyline(cs: PDPageContentStream, s: FloatArray, pageH: Float, scale: Float) {
        if (s.size < 2) return
        cs.moveTo(s[0] / scale, pageH - s[1] / scale)
        if (s.size == 2) {
            cs.lineTo(s[0] / scale + 0.1f, pageH - s[1] / scale)
        } else {
            var i = 2
            while (i + 1 < s.size) {
                cs.lineTo(s[i] / scale, pageH - s[i + 1] / scale)
                i += 2
            }
        }
        cs.stroke()
    }

    private fun drawText(cs: PDPageContentStream, a: Anno, pageH: Float, scale: Float) {
        val r = a.rect ?: return
        val txt = a.text ?: return
        val size = 14f / scale
        cs.beginText()
        cs.setNonStrokingColor(red(a.color), green(a.color), blue(a.color))
        cs.setFont(PDType1Font.HELVETICA, size)
        cs.newLineAtOffset(r[0] / scale, pageH - r[1] / scale - size)
        cs.showText(txt.replace("\n", " "))
        cs.endText()
    }

    private fun drawSignature(
        doc: PDDocument, cs: PDPageContentStream, a: Anno, pageH: Float, scale: Float
    ) {
        val r = a.rect ?: return
        val bytes = a.image ?: return
        val img = PDImageXObject.createFromByteArray(doc, bytes, "sig")
        val x = r[0] / scale
        val w = r[2] / scale
        val h = r[3] / scale
        val y = pageH - (r[1] + r[3]) / scale
        cs.drawImage(img, x, y, w, h)
    }

    // ---------------- helpers ----------------

    private fun setFill(cs: PDPageContentStream, color: Int) {
        cs.setNonStrokingColor(red(color), green(color), blue(color))
    }

    private fun red(c: Int) = ((c shr 16) and 0xFF) / 255f
    private fun green(c: Int) = ((c shr 8) and 0xFF) / 255f
    private fun blue(c: Int) = (c and 0xFF) / 255f

    private fun bitmapToJpeg(bmp: Bitmap, quality: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return bos.toByteArray()
    }

    private fun decodeScaled(file: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }
}
