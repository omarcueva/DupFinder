package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Formatters {
  fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(units.lastIndex)
    val value = bytes / 1024.0.pow(exp.toDouble())
    return if (value >= 100.0) {
      String.format(Locale.US, "%.0f %s", value, units[exp])
    } else if (value >= 10.0) {
      String.format(Locale.US, "%.1f %s", value, units[exp])
    } else {
      String.format(Locale.US, "%.2f %s", value, units[exp])
    }
  }

  fun formatDate(millis: Long): String {
    if (millis <= 0L) return "Unknown date"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
  }

  fun formatShortDate(millis: Long): String {
    if (millis <= 0L) return "Unknown"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
  }

  fun formatDuration(millis: Long): String {
    if (millis < 1000L) return "${millis}ms"
    val sec = millis / 1000.0
    return String.format(Locale.US, "%.1fs", sec)
  }

  fun resolveFolderDisplayName(context: Context, treeUri: Uri): String {
    try {
      val docId = DocumentsContract.getTreeDocumentId(treeUri)
      val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
      context.contentResolver.query(
        docUri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          val name = cursor.getString(0)
          if (!name.isNullOrBlank()) {
            val decodedPath = decodeTreeDocPath(docId)
            return if (decodedPath.isNotBlank()) decodedPath else name
          }
        }
      }
      val fallback = decodeTreeDocPath(docId)
      if (fallback.isNotBlank()) return fallback
    } catch (_: Exception) {
      // Fallback to URI last segment below
    }
    return treeUri.lastPathSegment?.substringAfter(':') ?: "Selected Folder"
  }

  private fun decodeTreeDocPath(docId: String): String {
    val parts = docId.split(':', limit = 2)
    return if (parts.size == 2) {
      val root = when {
        parts[0].equals("primary", ignoreCase = true) -> "Internal Storage"
        parts[0].equals("home", ignoreCase = true) -> "Documents"
        else -> parts[0]
      }
      val sub = parts[1].trim('/').replace("/", " › ")
      if (sub.isBlank()) root else "$root › $sub"
    } else {
      docId
    }
  }
}
