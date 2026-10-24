package com.adfreepdf.reader.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Finds PDF files on the device using MediaStore, with a filesystem fallback. */
object PdfRepository {

    fun scan(context: Context): List<PdfFile> {
        val out = LinkedHashMap<String, PdfFile>()
        scanMediaStore(context, out)
        if (out.isEmpty()) scanFileSystem(out)
        return out.values.sortedBy { it.name.lowercase() }
    }

    private fun scanMediaStore(context: Context, out: MutableMap<String, PdfFile>) {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("application/pdf", "%.pdf")
        runCatching {
            context.contentResolver.query(collection, projection, selection, args, null)
                ?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    while (c.moveToNext()) {
                        val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                        val name = c.getString(nameCol) ?: continue
                        val size = c.getLong(sizeCol)
                        val date = c.getLong(dateCol) * 1000L
                        out[uri.toString()] = PdfFile(uri.toString(), name, size, date)
                    }
                }
        }
    }

    private fun scanFileSystem(out: MutableMap<String, PdfFile>) {
        val roots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStorageDirectory()
        )
        roots.filter { it != null && it.exists() }.forEach { root ->
            runCatching {
                root.walkTopDown().maxDepth(6)
                    .filter { it.isFile && it.name.lowercase().endsWith(".pdf") }
                    .take(2000)
                    .forEach { f: File ->
                        val uri = Uri.fromFile(f).toString()
                        out[uri] = PdfFile(uri, f.name, f.length(), f.lastModified(), f.absolutePath)
                    }
            }
        }
    }
}
