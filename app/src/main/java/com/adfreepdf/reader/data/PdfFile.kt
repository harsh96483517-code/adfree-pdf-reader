package com.adfreepdf.reader.data

import android.net.Uri

/** A PDF discovered on the device. */
data class PdfFile(
    val uri: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val path: String? = null
) {
    val uriObj: Uri get() = Uri.parse(uri)
    /** Stable key used for bookmarks / recents. */
    val key: String get() = uri
}
