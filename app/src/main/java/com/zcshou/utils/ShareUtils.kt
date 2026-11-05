package com.zcshou.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * File sharing and text sharing utilities for LibreGo.
 */
object ShareUtils {

    @JvmStatic
    fun getUriFromFile(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileProvider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    @JvmStatic
    fun shareFile(context: Context, file: File, title: String) {
        val share = Intent(Intent.ACTION_SEND).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_STREAM, getUriFromFile(context, file))
            type = "application/octet-stream"
        }
        context.startActivity(Intent.createChooser(share, title))
    }

    @JvmStatic
    fun shareText(context: Context, title: String, text: String) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        context.startActivity(Intent.createChooser(share, title))
    }
}
