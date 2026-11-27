package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.model.DuplicateGroup
import com.example.model.FileTypeFilter
import com.example.model.ScanPhase
import com.example.model.ScanProgress
import com.example.model.ScanTarget
import com.example.model.ScannedFile
import com.example.util.StorageAccessHelper
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class DuplicateScanResult(
  val totalFilesScanned: Int,
  val groups: List<DuplicateGroup>,
  val durationMillis: Long,
)

class DuplicateScanner(private val context: Context) {

  private data class IndexedEntry(
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
  )

  suspend fun scanTarget(
    target: ScanTarget,
    minSizeBytes: Long,
    fileTypeFilter: FileTypeFilter,
    includeSubfolders: Boolean,
    onProgress: (ScanProgress) -> Unit,
  ): DuplicateScanResult = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()
    val effectiveMinSize = minSizeBytes.coerceAtLeast(1L)

    val indexedFiles = when (target) {
      is ScanTarget.WholeDevice -> {
        val volumes = StorageAccessHelper.getMountedStorageVolumes(context)
        val roots = volumes.map { it.rootFile to it.label }
        indexFileSystemRoots(roots, effectiveMinSize, fileTypeFilter, includeSubfolders, onProgress)
      }
      is ScanTarget.FileSystemFolder -> {
        val rootFile = File(target.path)
        val rootLabel = StorageAccessHelper.formatDisplayPath(context, rootFile)
        indexFileSystemRoots(
          roots = listOf(rootFile to rootLabel),
          minSizeBytes = effectiveMinSize,
          fileTypeFilter = fileTypeFilter,
          includeSubfolders = includeSubfolders,
          onProgress = onProgress,
        )
      }
      is ScanTarget.SafTree -> {
        indexSafTree(
          treeUri = target.treeUri,
          minSizeBytes = effectiveMinSize,
          fileTypeFilter = fileTypeFilter,
          includeSubfolders = includeSubfolders,
          onProgress = onProgress,
        )
      }
    }

    // Phase 2: Size bucketing (files with unique sizes cannot be byte-identical duplicates)
    onProgress(
      ScanProgress(
        phase = ScanPhase.BUCKETING,
        indexedFilesCount = indexedFiles.size,
      )
    )
    val sizeCandidates = indexedFiles
      .groupBy { it.sizeBytes }
      .values
      .filter { it.size >= 2 }
      .flatten()

    if (sizeCandidates.isEmpty()) {
      return@withContext DuplicateScanResult(
        totalFilesScanned = indexedFiles.size,
        groups = emptyList(),
        durationMillis = System.currentTimeMillis() - startTime,
      )
    }

    // Phase 3a: Partial pre-hash (first 8KB + last 8KB) for files > 32KB
    val resolver = context.contentResolver
    val preHashThreshold = 32L * 1024L
    val partialBuckets = mutableMapOf<Pair<Long, String>, MutableList<IndexedEntry>>()

    for (entry in sizeCandidates) {
      coroutineContext.ensureActive()
      val key = if (entry.sizeBytes > preHashThreshold) {
        val partial = if (entry.filePath != null) {
          computeFilePartialHash(File(entry.filePath), entry.sizeBytes)
        } else {
          computeSafPartialHash(resolver, entry.uri, entry.sizeBytes)
        } ?: continue
        entry.sizeBytes to partial
      } else {
        entry.sizeBytes to "SMALL"
      }
      partialBuckets.getOrPut(key) { mutableListOf() }.add(entry)
    }

    val fullHashCandidates = partialBuckets.values.filter { it.size >= 2 }.flatten()

    // Phase 3b: Full SHA-256 content hash regardless of name or modification date
    val scannedFiles = mutableListOf<ScannedFile>()
    var processedCount = 0
    for (entry in fullHashCandidates) {
      coroutineContext.ensureActive()
      processedCount++
      onProgress(
        ScanProgress(
          phase = ScanPhase.HASHING,
          indexedFilesCount = indexedFiles.size,
          sizeCandidatesCount = fullHashCandidates.size,
          hashedFilesCount = processedCount,
          currentFileName = entry.displayName,
        )
      )
      val sha256 = if (entry.filePath != null) {
        computeFileFullSha256(File(entry.filePath))
      } else {
        computeSafFullSha256(resolver, entry.uri)
      } ?: continue

      scannedFiles.add(
        ScannedFile(
          uri = entry.uri,
          filePath = entry.filePath,
          documentId = entry.documentId,
          parentDocumentId = entry.parentDocumentId,
          treeUri = entry.treeUri,
          displayName = entry.displayName,
          relativePath = entry.relativePath,
          mimeType = entry.mimeType,
          sizeBytes = entry.sizeBytes,
          lastModifiedMillis = entry.lastModifiedMillis,
          sha256 = sha256,
        )
      )
    }

    val groups = buildDuplicateGroups(scannedFiles)
    DuplicateScanResult(
      totalFilesScanned = indexedFiles.size,
      groups = groups,
      durationMillis = System.currentTimeMillis() - startTime,
    )
  }

  private suspend fun indexFileSystemRoots(
    roots: List<Pair<File, String>>,
    minSizeBytes: Long,
    fileTypeFilter: FileTypeFilter,
    includeSubfolders: Boolean,
    onProgress: (ScanProgress) -> Unit,
  ): List<IndexedEntry> {
    val indexedFiles = mutableListOf<IndexedEntry>()
    val seenCanonicalPaths = HashSet<String>()

    for ((rootDir, rootLabel) in roots) {
      coroutineContext.ensureActive()
      if (!rootDir.exists() || !rootDir.isDirectory || !rootDir.canRead()) continue

      val rootCanonical = runCatching { rootDir.canonicalPath }.getOrDefault(rootDir.absolutePath)
      val queue = ArrayDeque<File>()
      queue.add(rootDir)

      while (queue.isNotEmpty()) {
        coroutineContext.ensureActive()
        val currentDir = queue.removeFirst()
        val children = currentDir.listFiles() ?: continue

        for (child in children) {
          coroutineContext.ensureActive()
          val name = child.name
          if (child.isDirectory) {
            if (includeSubfolders && !shouldSkipSystemDirectory(child, name)) {
              val canonical = runCatching { child.canonicalPath }.getOrDefault(child.absolutePath)
              // Avoid symlink loops across storage mounts
              if (seenCanonicalPaths.add(canonical)) {
                queue.add(child)
              }
            }
          } else if (child.isFile && child.canRead()) {
            val size = child.length()
            if (size >= minSizeBytes) {
              val mime = StorageAccessHelper.inferMimeType(name)
              if (fileTypeFilter.matches(mime, name)) {
                val canonical = runCatching { child.canonicalPath }.getOrDefault(child.absolutePath)
                if (!seenCanonicalPaths.add(canonical)) continue

                val parentPath = child.parentFile?.let { parent ->
                  val pAbs = runCatching { parent.canonicalPath }.getOrDefault(parent.absolutePath)
                  val rel = pAbs.removePrefix(rootCanonical).trim('/')
                  if (rel.isEmpty()) rootLabel else "$rootLabel/$rel"
                } ?: rootLabel

                indexedFiles.add(
                  IndexedEntry(
                    uri = Uri.fromFile(child),
                    filePath = child.absolutePath,
                    displayName = name,
                    relativePath = parentPath,
                    mimeType = mime,
                    sizeBytes = size,
                    lastModifiedMillis = child.lastModified(),
                  )
                )
                if (indexedFiles.size % 25 == 0 || indexedFiles.size <= 5) {
                  onProgress(
                    ScanProgress(
                      phase = ScanPhase.INDEXING,
                      indexedFilesCount = indexedFiles.size,
                      currentFileName = name,
                    )
                  )
                }
              }
            }
          }
        }
      }
    }

    return indexedFiles
  }

  private fun shouldSkipSystemDirectory(dir: File, name: String): Boolean {
    if (name.startsWith(".dupsweep_trash", ignoreCase = true)) return true
    // Android 11+ restricts access to other apps' Android/data and Android/obb even with All Files Access
    val path = dir.absolutePath
    if (path.endsWith("/Android/data", ignoreCase = true) || path.endsWith("/Android/obb", ignoreCase = true)) {
      return !dir.canRead() || (dir.listFiles()?.isEmpty() == true)
    }
    return false
  }

  private suspend fun indexSafTree(
    treeUri: Uri,
    minSizeBytes: Long,
    fileTypeFilter: FileTypeFilter,
    includeSubfolders: Boolean,
    onProgress: (ScanProgress) -> Unit,
  ): List<IndexedEntry> {
    val resolver = context.contentResolver
    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val indexedFiles = mutableListOf<IndexedEntry>()
    val queue = ArrayDeque<Pair<String, String>>()
    queue.add(rootDocId to "")

    val projection = arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_SIZE,
      DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    while (queue.isNotEmpty()) {
      coroutineContext.ensureActive()
      val (parentDocId, currentRelPath) = queue.removeFirst()
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

      resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

        while (cursor.moveToNext()) {
          coroutineContext.ensureActive()
          val docId = if (idCol >= 0) cursor.getString(idCol) ?: continue else continue
          val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Unknown" else "Unknown"
          val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "application/octet-stream" else "application/octet-stream"
          val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
          val modified = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) else 0L

          if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            if (includeSubfolders && !name.startsWith(".dupsweep_trash", ignoreCase = true)) {
              val nextRel = if (currentRelPath.isEmpty()) name else "$currentRelPath/$name"
              queue.add(docId to nextRel)
            }
          } else {
            if (size >= minSizeBytes && fileTypeFilter.matches(mime, name)) {
              val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
              indexedFiles.add(
                IndexedEntry(
                  uri = docUri,
                  documentId = docId,
                  parentDocumentId = parentDocId,
                  treeUri = treeUri,
                  displayName = name,
                  relativePath = if (currentRelPath.isEmpty()) "/" else "/$currentRelPath/",
                  mimeType = mime,
                  sizeBytes = size,
                  lastModifiedMillis = modified,
                )
              )
              if (indexedFiles.size % 15 == 0 || indexedFiles.size <= 5) {
                onProgress(
                  ScanProgress(
                    phase = ScanPhase.INDEXING,
                    indexedFilesCount = indexedFiles.size,
                    currentFileName = name,
                  )
                )
              }
            }
          }
        }
      }
    }

    return indexedFiles
  }

  private fun computeFilePartialHash(file: File, sizeBytes: Long): String? {
    return try {
      val digest = MessageDigest.getInstance("SHA-256")
      val chunkSize = 8192
      val buffer = ByteArray(chunkSize)
      RandomAccessFile(file, "r").use { raf ->
        val headRead = raf.read(buffer, 0, chunkSize)
        if (headRead > 0) digest.update(buffer, 0, headRead)

        val tailStart = (sizeBytes - chunkSize).coerceAtLeast(0L)
        raf.seek(tailStart)
        val tailRead = raf.read(buffer, 0, chunkSize)
        if (tailRead > 0) digest.update(buffer, 0, tailRead)
      }
      digest.digest().toHexString()
    } catch (_: Exception) {
      null
    }
  }

  private fun computeFileFullSha256(file: File): String? {
    return try {
      val digest = MessageDigest.getInstance("SHA-256")
      val buffer = ByteArray(64 * 1024)
      file.inputStream().use { stream ->
        while (true) {
          val bytesRead = stream.read(buffer)
          if (bytesRead <= 0) break
          digest.update(buffer, 0, bytesRead)
        }
      }
      digest.digest().toHexString()
    } catch (_: Exception) {
      null
    }
  }

  private fun computeSafPartialHash(
    resolver: ContentResolver,
    uri: Uri,
    sizeBytes: Long,
  ): String? {
    return try {
      val digest = MessageDigest.getInstance("SHA-256")
      val chunkSize = 8192
      val buffer = ByteArray(chunkSize)
      resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
        afd.createInputStream().use { input ->
          var remaining = chunkSize
          while (remaining > 0) {
            val read = input.read(buffer, 0, remaining)
            if (read <= 0) break
            digest.update(buffer, 0, read)
            remaining -= read
          }
        }
      } ?: return null

      resolver.openFileDescriptor(uri, "r")?.use { pfd ->
        java.io.FileInputStream(pfd.fileDescriptor).use { fis ->
          val channel = fis.channel
          val tailStart = (sizeBytes - chunkSize).coerceAtLeast(0L)
          channel.position(tailStart)
          val tailBuf = ByteBuffer.allocate(chunkSize)
          while (tailBuf.hasRemaining()) {
            val read = channel.read(tailBuf)
            if (read <= 0) break
          }
          digest.update(tailBuf.array(), 0, tailBuf.position())
        }
      }

      digest.digest().toHexString()
    } catch (_: Exception) {
      "FALLBACK_FULL"
    }
  }

  private fun computeSafFullSha256(resolver: ContentResolver, uri: Uri): String? {
    return try {
      val digest = MessageDigest.getInstance("SHA-256")
      val buffer = ByteArray(64 * 1024)
      resolver.openInputStream(uri)?.use { stream ->
        while (true) {
          val bytesRead = stream.read(buffer)
          if (bytesRead <= 0) break
          digest.update(buffer, 0, bytesRead)
        }
      } ?: return null
      digest.digest().toHexString()
    } catch (_: Exception) {
      null
    }
  }

  companion object {
    private val COPY_SUFFIX_REGEX = Regex("(?i)(?:\\s*\\(\\d+\\)|\\s*-\\s*copy|\\s*_copy|\\s+copy)(?=\\.[^.]+$|$)")

    fun buildDuplicateGroups(files: List<ScannedFile>): List<DuplicateGroup> {
      return files
        .groupBy { it.sizeBytes to it.sha256 }
        .filter { (_, groupFiles) -> groupFiles.size >= 2 }
        .map { (key, groupFiles) ->
          val sorted = sortForOriginalFirst(groupFiles)
          val original = sorted.first()
          DuplicateGroup(
            id = "${key.first}_${key.second}",
            sha256 = key.second,
            sizeBytes = key.first,
            files = sorted,
            originalUri = original.uri,
          )
        }
        .sortedByDescending { it.reclaimableBytes }
    }

    fun sortForOriginalFirst(files: List<ScannedFile>): List<ScannedFile> {
      return files.sortedWith(
        compareBy<ScannedFile>(
          { if (it.lastModifiedMillis > 0L) it.lastModifiedMillis else Long.MAX_VALUE },
          { if (COPY_SUFFIX_REGEX.containsMatchIn(it.displayName)) 1 else 0 },
          { it.displayName.length },
          { it.displayName.lowercase(Locale.ROOT) },
          { it.relativePath.lowercase(Locale.ROOT) },
        )
      )
    }

    private fun ByteArray.toHexString(): String =
      joinToString(separator = "") { byte -> "%02x".format(byte) }
  }
}
