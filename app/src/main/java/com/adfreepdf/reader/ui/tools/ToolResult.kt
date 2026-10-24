package com.adfreepdf.reader.ui.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.adfreepdf.reader.R
import java.io.File

object ToolResult {
    fun share(context: Context, file: File) {
        runCatching {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(i, context.getString(R.string.share)))
        }
    }
}
