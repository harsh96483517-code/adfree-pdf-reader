package com.adfreepdf.reader.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun queryName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "document.pdf"
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) {
                    c.getString(idx)?.let { return it }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    }

    fun querySize(context: Context, uri: Uri): Long {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst()) return c.getLong(idx)
            }
        }
        return 0L
    }

    /** Copies a content Uri into the app cache so PDFBox can read it as a File. */
    fun toCacheFile(context: Context, uri: Uri, name: String? = null): File {
        val safe = (name ?: queryName(context, uri)).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(context.cacheDir, "${System.currentTimeMillis()}_$safe")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return file
    }

    fun humanSize(bytes: Long): String {
        if (bytes <= 0) return "\u2014"
        val units = arrayOf("B", "KB", "MB", "GB")
        var v = bytes.toDouble(); var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun date(millis: Long): String {
        if (millis <= 0) return ""
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
    }

    /** Writes bytes into the public Downloads folder and returns a shareable Uri. */
    fun saveToDownloads(context: Context, name: String, write: (java.io.OutputStream) -> Unit): File {
        val dir = android.os.Environment
            .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val outDir = File(dir, "AdFreePdfReader").apply { mkdirs() }
        val outFile = File(outDir, name)
        FileOutputStream(outFile).use { write(it) }
        return outFile
    }
}
