package com.example.data

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.model.ScannedFile
import com.example.model.TrashedItem
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface MoveToTrashPlan {
  data class Ready(
    val systemMediaUris: List<Uri>,
    val systemBackedFiles: List<ScannedFile>,
    val appTrashFiles: List<ScannedFile>,
  ) : MoveToTrashPlan
}

data class TrashOperationResult(
  val trashedUris: Set<Uri>,
  val movedToAppTrashCount: Int,
  val movedToSystemTrashCount: Int,
  val failedCount: Int,
)

class TrashRepository(private val context: Context) {

  private val trashDir: File by lazy {
    File(context.filesDir, "dupsweep_trash").apply { if (!exists()) mkdirs() }
  }

  private val indexFile: File by lazy {
    File(context.filesDir, "trash_index.json")
  }

  suspend fun loadTrashedItems(): List<TrashedItem> = withContext(Dispatchers.IO) {
    readIndex().sortedByDescending { it.trashedAtMillis }
  }

  suspend fun prepareMoveToTrash(files: List<ScannedFile>): MoveToTrashPlan.Ready =
    withContext(Dispatchers.IO) {
      val mediaUris = mutableListOf<Uri>()
      val mediaFiles = mutableListOf<ScannedFile>()
      val appTrashFiles = mutableListOf<ScannedFile>()

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        for (file in files) {
          val isMedia = file.isImage || file.isVideo || file.isAudio
          val mediaUri = if (isMedia) {
            resolveMediaStoreUri(file)
          } else {
            null
          }
          if (mediaUri != null) {
            mediaUris.add(mediaUri)
            mediaFiles.add(file)
          } else {
            appTrashFiles.add(file)
          }
        }
      } else {
        appTrashFiles.addAll(files)
      }

      MoveToTrashPlan.Ready(
        systemMediaUris = mediaUris,
        systemBackedFiles = mediaFiles,
        appTrashFiles = appTrashFiles,
      )
    }

  private fun resolveMediaStoreUri(file: ScannedFile): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    return try {
      if (file.filePath != null) {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
          MediaStore.Files.FileColumns._ID,
          MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        context.contentResolver.query(
          collection,
          projection,
          "${MediaStore.MediaColumns.DATA} = ?",
          arrayOf(file.filePath),
          null,
        )?.use { cursor ->
          if (cursor.moveToFirst()) {
            val id = cursor.getLong(0)
            val mediaType = cursor.getInt(1)
            val baseUri = when (mediaType) {
              MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
              MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
              MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
              else -> null
            }
            baseUri?.let { ContentUris.withAppendedId(it, id) }
          } else {
            null
          }
        }
      } else {
        MediaStore.getMediaUri(context, file.uri)
      }
    } catch (_: Exception) {
      null
    }
  }

  fun buildSystemTrashIntentSender(mediaUris: List<Uri>): IntentSender? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || mediaUris.isEmpty()) return null
    return MediaStore.createTrashRequest(context.contentResolver, mediaUris, true).intentSender
  }

  suspend fun moveToAppTrash(files: List<ScannedFile>): TrashOperationResult =
    withContext(Dispatchers.IO) {
      val existing = readIndex().toMutableList()
      val trashedUris = mutableSetOf<Uri>()
      var movedCount = 0
      var failedCount = 0

      for (file in files) {
        val id = UUID.randomUUID().toString()
        val storedFile = File(trashDir, id)
        val copiedOk = try {
          var bytesCopied = 0L
          if (file.filePath != null) {
            File(file.filePath).inputStream().use { input ->
              storedFile.outputStream().use { output ->
                bytesCopied = input.copyTo(output, bufferSize = 64 * 1024)
              }
            }
          } else {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
              storedFile.outputStream().use { output ->
                bytesCopied = input.copyTo(output, bufferSize = 64 * 1024)
              }
            }
          }
          bytesCopied == file.sizeBytes || (file.sizeBytes > 0L && storedFile.length() == file.sizeBytes)
        } catch (_: Exception) {
          false
        }

        if (!copiedOk) {
          storedFile.delete()
          failedCount++
          continue
        }

        val deletedSource = try {
          if (file.filePath != null) {
            val f = File(file.filePath)
            f.delete() || !f.exists()
          } else {
            DocumentsContract.deleteDocument(context.contentResolver, file.uri)
          }
        } catch (_: Exception) {
          false
        }

        if (deletedSource) {
          existing.add(
            TrashedItem(
              id = id,
              displayName = file.displayName,
              mimeType = file.mimeType,
              sizeBytes = file.sizeBytes,
              relativePath = file.relativePath,
              originalFilePath = file.filePath.orEmpty(),
              originalTreeUri = file.treeUri?.toString().orEmpty(),
              originalParentDocId = file.parentDocumentId,
              trashFileName = id,
              trashedAtMillis = System.currentTimeMillis(),
            )
          )
          trashedUris.add(file.uri)
          movedCount++
        } else {
          storedFile.delete()
          failedCount++
        }
      }

      writeIndex(existing)
      TrashOperationResult(
        trashedUris = trashedUris,
        movedToAppTrashCount = movedCount,
        movedToSystemTrashCount = 0,
        failedCount = failedCount,
      )
    }

  suspend fun restoreItem(item: TrashedItem): Boolean = withContext(Dispatchers.IO) {
    val storedFile = File(trashDir, item.trashFileName)
    if (!storedFile.exists()) {
      removeFromIndex(item.id)
      return@withContext false
    }

    return@withContext try {
      if (item.originalFilePath.isNotBlank()) {
        val target = File(item.originalFilePath)
        target.parentFile?.mkdirs()
        val destFile = if (target.exists()) {
          val base = target.nameWithoutExtension
          val ext = target.extension
          val suffix = if (ext.isNotBlank()) ".$ext" else ""
          File(target.parentFile, "${base}_restored_${System.currentTimeMillis()}$suffix")
        } else {
          target
        }
        storedFile.inputStream().use { input ->
          destFile.outputStream().use { output ->
            input.copyTo(output, bufferSize = 64 * 1024)
          }
        }
        storedFile.delete()
        removeFromIndex(item.id)
        true
      } else {
        val treeUri = Uri.parse(item.originalTreeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, item.originalParentDocId)
        val createdUri = DocumentsContract.createDocument(
          context.contentResolver,
          parentUri,
          item.mimeType.ifBlank { "application/octet-stream" },
          item.displayName,
        ) ?: return@withContext false

        context.contentResolver.openOutputStream(createdUri)?.use { out ->
          storedFile.inputStream().use { input ->
            input.copyTo(out, bufferSize = 64 * 1024)
          }
        } ?: return@withContext false

        storedFile.delete()
        removeFromIndex(item.id)
        true
      }
    } catch (_: Exception) {
      false
    }
  }

  suspend fun deletePermanently(item: TrashedItem): Unit = withContext(Dispatchers.IO) {
    File(trashDir, item.trashFileName).delete()
    removeFromIndex(item.id)
  }

  suspend fun emptyTrash(): Unit = withContext(Dispatchers.IO) {
    trashDir.listFiles()?.forEach { it.delete() }
    writeIndex(emptyList())
  }

  private fun removeFromIndex(id: String) {
    val updated = readIndex().filterNot { it.id == id }
    writeIndex(updated)
  }

  private fun readIndex(): List<TrashedItem> {
    if (!indexFile.exists()) return emptyList()
    return try {
      val raw = indexFile.readText()
      if (raw.isBlank()) return emptyList()
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          add(
            TrashedItem(
              id = obj.getString("id"),
              displayName = obj.getString("displayName"),
              mimeType = obj.getString("mimeType"),
              sizeBytes = obj.getLong("sizeBytes"),
              relativePath = obj.getString("relativePath"),
              originalFilePath = obj.optString("originalFilePath", ""),
              originalTreeUri = obj.optString("originalTreeUri", ""),
              originalParentDocId = obj.optString("originalParentDocId", ""),
              trashFileName = obj.getString("trashFileName"),
              trashedAtMillis = obj.getLong("trashedAtMillis"),
            )
          )
        }
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun writeIndex(items: List<TrashedItem>) {
    val arr = JSONArray()
    for (item in items) {
      arr.put(
        JSONObject().apply {
          put("id", item.id)
          put("displayName", item.displayName)
          put("mimeType", item.mimeType)
          put("sizeBytes", item.sizeBytes)
          put("relativePath", item.relativePath)
          put("originalFilePath", item.originalFilePath)
          put("originalTreeUri", item.originalTreeUri)
          put("originalParentDocId", item.originalParentDocId)
          put("trashFileName", item.trashFileName)
          put("trashedAtMillis", item.trashedAtMillis)
        }
      )
    }
    indexFile.writeText(arr.toString())
  }
}
