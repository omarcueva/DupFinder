package com.example.model

import android.net.Uri
import java.io.File
import java.util.Locale

enum class FileTypeFilter(val label: String) {
  ALL("All Files"),
  IMAGES("Images"),
  VIDEOS("Videos"),
  AUDIO("Audio"),
  DOCUMENTS("Documents"),
  ARCHIVES("Archives");

  fun matches(mimeType: String, fileName: String): Boolean {
    if (this == ALL) return true
    val mime = mimeType.lowercase(Locale.ROOT)
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (this) {
      ALL -> true
      IMAGES -> mime.startsWith("image/") || ext in IMAGE_EXTS
      VIDEOS -> mime.startsWith("video/") || ext in VIDEO_EXTS
      AUDIO -> mime.startsWith("audio/") || ext in AUDIO_EXTS
      DOCUMENTS -> mime.startsWith("text/") ||
        mime == "application/pdf" ||
        mime.contains("document") ||
        mime.contains("spreadsheet") ||
        mime.contains("presentation") ||
        ext in DOC_EXTS
      ARCHIVES -> mime.contains("zip") ||
        mime.contains("rar") ||
        mime.contains("7z") ||
        mime.contains("tar") ||
        mime.contains("gzip") ||
        ext in ARCHIVE_EXTS
    }
  }

  companion object {
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "dng", "raw")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v")
    private val AUDIO_EXTS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma")
    private val DOC_EXTS = setOf("pdf", "txt", "md", "csv", "json", "xml", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "epub")
    private val ARCHIVE_EXTS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk")
  }
}

enum class MinSizePreset(val label: String, val bytes: Long) {
  ANY("Any (>0 B)", 1L),
  KB_10("10 KB", 10L * 1024L),
  KB_100("100 KB", 100L * 1024L),
  MB_1("1 MB", 1024L * 1024L),
  MB_10("10 MB", 10L * 1024L * 1024L),
}

enum class SizeUnit(val label: String, val multiplier: Long) {
  BYTES("B", 1L),
  KB("KB", 1024L),
  MB("MB", 1024L * 1024L),
}

enum class ResultSortOrder(val label: String) {
  LARGEST_RECLAIMABLE("Largest Savings"),
  MOST_DUPLICATES("Most Copies"),
  FILE_SIZE("File Size"),
  NAME("Name"),
}

sealed interface ScanTarget {
  data class WholeDevice(val label: String = "Whole Device (Internal + SD Cards)") : ScanTarget
  data class FileSystemFolder(val path: String, val label: String) : ScanTarget
  data class SafTree(val treeUri: Uri, val label: String) : ScanTarget

  val displayLabel: String
    get() = when (this) {
      is WholeDevice -> label
      is FileSystemFolder -> label
      is SafTree -> label
    }
}

data class StorageVolumeInfo(
  val id: String,
  val label: String,
  val rootFile: File,
  val isRemovable: Boolean,
  val totalBytes: Long,
  val freeBytes: Long,
)

data class ScannedFile(
  val uri: Uri,
  val filePath: String? = null,
  val documentId: String = "",
  val parentDocumentId: String = "",
  val treeUri: Uri? = null,
  val displayName: String,
  val relativePath: String,
  val mimeType: String,
  val sizeBytes: Long,
  val lastModifiedMillis: Long,
  val sha256: String,
) {
  val isFileSystemBacked: Boolean get() = !filePath.isNullOrBlank()
  val isImage: Boolean get() = FileTypeFilter.IMAGES.matches(mimeType, displayName)
  val isVideo: Boolean get() = FileTypeFilter.VIDEOS.matches(mimeType, displayName)
  val isAudio: Boolean get() = FileTypeFilter.AUDIO.matches(mimeType, displayName)
  val isPdf: Boolean get() = mimeType.equals("application/pdf", ignoreCase = true) ||
    displayName.endsWith(".pdf", ignoreCase = true)
  val isTextPreviewable: Boolean get() {
    val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return mimeType.startsWith("text/", ignoreCase = true) ||
      ext in setOf("txt", "md", "csv", "json", "xml", "log", "yaml", "yml")
  }
  val extension: String get() = displayName.substringAfterLast('.', "").uppercase(Locale.ROOT)
}

data class DuplicateGroup(
  val id: String,
  val sha256: String,
  val sizeBytes: Long,
  val files: List<ScannedFile>,
  val originalUri: Uri,
) {
  val originalFile: ScannedFile
    get() = files.firstOrNull { it.uri == originalUri } ?: files.first()

  val duplicateFiles: List<ScannedFile>
    get() = files.filter { it.uri != originalFile.uri }

  val reclaimableBytes: Long
    get() = sizeBytes * (files.size - 1).coerceAtLeast(0)
}

enum class ScanPhase {
  INDEXING,
  BUCKETING,
  HASHING,
}

data class ScanProgress(
  val phase: ScanPhase = ScanPhase.INDEXING,
  val indexedFilesCount: Int = 0,
  val sizeCandidatesCount: Int = 0,
  val hashedFilesCount: Int = 0,
  val currentFileName: String = "",
)

data class ScanSummary(
  val folderLabel: String,
  val totalFilesScanned: Int,
  val groupsCount: Int,
  val totalDuplicatesCount: Int,
  val totalReclaimableBytes: Long,
  val scanDurationMillis: Long,
)

data class TrashedItem(
  val id: String,
  val displayName: String,
  val mimeType: String,
  val sizeBytes: Long,
  val relativePath: String,
  val originalFilePath: String = "",
  val originalTreeUri: String = "",
  val originalParentDocId: String = "",
  val trashFileName: String,
  val trashedAtMillis: Long,
)

enum class AppDestination(val route: String) {
  SCAN("scan"),
  RESULTS("results"),
  TRASH("trash"),
}
