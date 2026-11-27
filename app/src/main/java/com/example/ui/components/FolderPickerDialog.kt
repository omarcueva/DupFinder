package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ScanTarget
import com.example.model.StorageVolumeInfo
import com.example.util.Formatters
import com.example.util.StorageAccessHelper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderPickerDialog(
  mountedVolumes: List<StorageVolumeInfo>,
  initialTarget: ScanTarget?,
  onSelectWholeDevice: () -> Unit,
  onSelectFolder: (File, String) -> Unit,
  onUseSystemSafPicker: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val defaultVolume = mountedVolumes.firstOrNull()
  var currentDir by remember(mountedVolumes, initialTarget) {
    val start = when (initialTarget) {
      is ScanTarget.FileSystemFolder -> File(initialTarget.path).takeIf { it.exists() && it.isDirectory }
      else -> null
    }
    mutableStateOf(start)
  }

  val subdirectories by produceState<List<File>>(initialValue = emptyList(), key1 = currentDir) {
    val dir = currentDir
    value = if (dir == null) {
      emptyList()
    } else {
      withContext(Dispatchers.IO) {
        dir.listFiles()
          ?.filter { it.isDirectory && it.canRead() && !it.name.startsWith(".dupsweep_trash") }
          ?.sortedBy { it.name.lowercase() }
          ?: emptyList()
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.85f),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
          ) {
            if (currentDir != null) {
              IconButton(
                onClick = {
                  val dir = currentDir!!
                  val isVolumeRoot = mountedVolumes.any {
                    it.rootFile.absolutePath == dir.absolutePath
                  }
                  currentDir = if (isVolumeRoot) null else dir.parentFile
                },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Up one folder",
                )
              }
            }
            Column {
              Text(
                text = if (currentDir == null) "Choose Storage or Folder" else currentDir!!.name.ifBlank { "Storage Root" },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (currentDir != null) {
                Text(
                  text = StorageAccessHelper.formatDisplayPath(context, currentDir!!),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close folder browser")
          }
        }

        if (currentDir == null) {
          // Root View: Whole Device + Mounted Volumes + Quick Folder Shortcuts
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            item(key = "whole_device") {
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onSelectWholeDevice() }
                  .testTag("pick_whole_device_card"),
              ) {
                Row(
                  modifier = Modifier.padding(16.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp),
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Whole Device (Internal + SD Cards)",
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                      text = "Scan all ${mountedVolumes.size.coerceAtLeast(1)} mounted storage volume(s) together",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                  }
                  Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                }
              }
            }

            item(key = "volumes_header") {
              Text(
                text = "Storage Volumes (Tap to browse folders)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
              )
            }

            items(items = mountedVolumes, key = { it.id }) { vol ->
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { currentDir = vol.rootFile }
                  .testTag("pick_volume_${vol.id}"),
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Icon(
                    imageVector = if (vol.isRemovable) Icons.Filled.SdStorage else Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = vol.label,
                      style = MaterialTheme.typography.titleSmall,
                    )
                    val spaceText = if (vol.totalBytes > 0L) {
                      "${Formatters.formatBytes(vol.freeBytes)} free of ${Formatters.formatBytes(vol.totalBytes)}"
                    } else {
                      vol.rootFile.absolutePath
                    }
                    Text(
                      text = spaceText,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open ${vol.label}",
                  )
                }
              }
            }

            if (defaultVolume != null) {
              val commonNames = listOf("Download", "Pictures", "DCIM", "Documents", "Music", "Movies")
              val existingShortcuts = commonNames
                .map { File(defaultVolume.rootFile, it) }
                .filter { it.exists() && it.isDirectory && it.canRead() }

              if (existingShortcuts.isNotEmpty()) {
                item(key = "shortcuts") {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                      text = "Quick Folder Shortcuts",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(top = 6.dp),
                    )
                    FlowRow(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      existingShortcuts.forEach { folder ->
                        AssistChip(
                          onClick = {
                            onSelectFolder(
                              folder,
                              StorageAccessHelper.formatDisplayPath(context, folder),
                            )
                          },
                          label = { Text(folder.name) },
                          leadingIcon = {
                            Icon(
                              imageVector = Icons.Filled.Folder,
                              contentDescription = null,
                              modifier = Modifier.size(18.dp),
                            )
                          },
                          modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        } else {
          // Directory Drill-down View
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (subdirectories.isEmpty()) {
              item(key = "empty_subdirs") {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = "No subfolders inside this directory.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            } else {
              items(items = subdirectories, key = { it.absolutePath }) { subDir ->
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { currentDir = subDir },
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Folder,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(22.dp),
                    )
                    Text(
                      text = subDir.name,
                      style = MaterialTheme.typography.bodyLarge,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                    Icon(
                      imageVector = Icons.Filled.ChevronRight,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }

          Button(
            onClick = {
              val dir = currentDir!!
              onSelectFolder(dir, StorageAccessHelper.formatDisplayPath(context, dir))
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("select_current_folder_button"),
          ) {
            Text("Select This Folder")
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onUseSystemSafPicker) {
            Text("Use System Picker (SAF)")
          }
          TextButton(onClick = onDismiss) {
            Text("Cancel")
          }
        }
      }
    }
  }
}
