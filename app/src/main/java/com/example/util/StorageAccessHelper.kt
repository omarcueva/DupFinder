package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.example.model.StorageVolumeInfo
import java.io.File
import java.util.Locale

object StorageAccessHelper {

  fun hasAllFilesAccess(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Environment.isExternalStorageManager()
    } else {
      val readGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE,
      ) == PackageManager.PERMISSION_GRANTED
      val writeGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
      ) == PackageManager.PERMISSION_GRANTED
      readGranted && writeGranted
    }
  }

  /**
   * Discovers all mounted storage volumes including Internal Shared Storage and any
   * removable external SD cards or USB OTG drives.
   */
  fun getMountedStorageVolumes(context: Context): List<StorageVolumeInfo> {
    val discovered = linkedMapOf<String, StorageVolumeInfo>()
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

    if (storageManager != null) {
      val volumes = storageManager.storageVolumes
      for ((index, vol) in volumes.withIndex()) {
        val dir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          vol.directory
        } else {
          // Reflection-free fallback below via getExternalFilesDirs
          if (vol.isPrimary) Environment.getExternalStorageDirectory() else null
        }
        if (dir != null && dir.exists()) {
          val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
          val label = vol.getDescription(context).takeIf { !it.isNullOrBlank() }
            ?: if (vol.isRemovable) "SD Card" else "Internal Storage"
          val stats = readVolumeStats(dir)
          discovered[canonical] = StorageVolumeInfo(
            id = vol.uuid ?: "vol_$index",
            label = if (vol.isRemovable && !label.contains("SD", ignoreCase = true) && !label.contains("USB", ignoreCase = true)) {
              "$label (External)"
            } else {
              label
            },
            rootFile = dir,
            isRemovable = vol.isRemovable,
            totalBytes = stats.first,
            freeBytes = stats.second,
          )
        }
      }
    }

    // Fallback / supplement for API 24-29 or removable SD cards via getExternalFilesDirs
    val appDirs = context.getExternalFilesDirs(null)
    appDirs.filterNotNull().forEachIndexed { idx, appDir ->
      val path = appDir.absolutePath
      val androidIdx = path.indexOf("/Android/data/")
      if (androidIdx > 0) {
        val rootPath = path.substring(0, androidIdx)
        val rootDir = File(rootPath)
        if (rootDir.exists()) {
          val canonical = runCatching { rootDir.canonicalPath }.getOrDefault(rootDir.absolutePath)
          if (!discovered.containsKey(canonical)) {
            val isRemovable = idx > 0
            val stats = readVolumeStats(rootDir)
            discovered[canonical] = StorageVolumeInfo(
              id = "ext_$idx",
              label = if (isRemovable) "External SD Card ($idx)" else "Internal Storage",
              rootFile = rootDir,
              isRemovable = isRemovable,
              totalBytes = stats.first,
              freeBytes = stats.second,
            )
          }
        }
      }
    }

    // Final safety fallback to primary external storage
    if (discovered.isEmpty()) {
      @Suppress("DEPRECATION")
      val primary = Environment.getExternalStorageDirectory()
      if (primary != null && primary.exists()) {
        val canonical = runCatching { primary.canonicalPath }.getOrDefault(primary.absolutePath)
        val stats = readVolumeStats(primary)
        discovered[canonical] = StorageVolumeInfo(
          id = "primary",
          label = "Internal Storage",
          rootFile = primary,
          isRemovable = false,
          totalBytes = stats.first,
          freeBytes = stats.second,
        )
      }
    }

    return discovered.values.toList()
  }

  private fun readVolumeStats(dir: File): Pair<Long, Long> {
    return try {
      val stat = StatFs(dir.absolutePath)
      stat.totalBytes to stat.availableBytes
    } catch (_: Exception) {
      0L to 0L
    }
  }

  fun inferMimeType(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (ext.isBlank()) return "application/octet-stream"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
  }

  fun formatDisplayPath(context: Context, folder: File): String {
    val volumes = getMountedStorageVolumes(context)
    val abs = runCatching { folder.canonicalPath }.getOrDefault(folder.absolutePath)
    for (vol in volumes) {
      val volPath = runCatching { vol.rootFile.canonicalPath }.getOrDefault(vol.rootFile.absolutePath)
      if (abs == volPath) return vol.label
      if (abs.startsWith("$volPath/")) {
        val rel = abs.removePrefix("$volPath/").replace("/", " › ")
        return "${vol.label} › $rel"
      }
    }
    return abs
  }
}
