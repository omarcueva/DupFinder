package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.FileTypeFilter
import com.example.model.MinSizePreset
import com.example.model.ScanPhase
import com.example.model.ScanTarget
import com.example.model.SizeUnit
import com.example.ui.theme.LocalDupSweepColors
import com.example.util.Formatters
import com.example.viewmodel.DupSweepUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerSetupScreen(
  uiState: DupSweepUiState,
  onRequestAllFilesAccess: () -> Unit,
  onOpenFolderBrowser: () -> Unit,
  onSelectWholeDevice: () -> Unit,
  onIncludeSubfoldersChanged: (Boolean) -> Unit,
  onMinSizeChanged: (Long) -> Unit,
  onFileTypeChanged: (FileTypeFilter) -> Unit,
  onStartScan: () -> Unit,
  onCancelScan: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val semantic = LocalDupSweepColors.current
  val isPreset = remember(uiState.minSizeBytes) {
    MinSizePreset.entries.any { it.bytes == uiState.minSizeBytes }
  }
  var showCustomSizeEditor by remember(isPreset) { mutableStateOf(!isPreset) }
  var customSizeInput by remember { mutableStateOf("500") }
  var customSizeUnit by remember { mutableStateOf(SizeUnit.KB) }
  var showAllFilesRationale by remember { mutableStateOf(false) }

  if (showAllFilesRationale) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { showAllFilesRationale = false },
      icon = {
        Icon(
          imageVector = Icons.Filled.LockOpen,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      },
      title = { Text("All Files Access Required") },
      text = {
        Text(
          "To scan the entire device—including Downloads, root folders, and external SD cards—DupSweep needs the All Files Access permission. All scanning runs 100% locally on your device.",
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showAllFilesRationale = false
            onRequestAllFilesAccess()
          },
          modifier = Modifier.testTag("confirm_grant_all_files_button"),
        ) {
          Text("Open Settings")
        }
      },
      dismissButton = {
        androidx.compose.material3.TextButton(
          onClick = { showAllFilesRationale = false },
        ) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 680.dp)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Hero Banner Card
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(142.dp),
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_scan_hero_1795750276047),
            contentDescription = "Illustration of duplicate file scanning prism",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(
                Brush.verticalGradient(
                  colors = listOf(
                    Color.Black.copy(alpha = 0.15f),
                    Color.Black.copy(alpha = 0.8f),
                  )
                )
              )
              .padding(18.dp),
            contentAlignment = Alignment.BottomStart,
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = stringResource(R.string.hero_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
              )
              Text(
                text = stringResource(R.string.hero_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
              )
            }
          }
        }
      }

      // 1. Scan Scope & Folder Card
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = stringResource(R.string.section_folder),
              style = MaterialTheme.typography.titleMedium,
            )
            if (uiState.hasAllFilesAccess) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = semantic.originalContainer,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = semantic.originalAccent,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = "All Folders Unlocked",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.originalAccent,
                  )
                }
              }
            }
          }

          if (!uiState.hasAllFilesAccess) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = semantic.warningContainer,
              border = BorderStroke(1.dp, semantic.warningAccent.copy(alpha = 0.45f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = stringResource(R.string.all_files_needed_note),
                  style = MaterialTheme.typography.bodySmall,
                  color = semantic.warningAccent,
                )
                Button(
                  onClick = onRequestAllFilesAccess,
                  colors = ButtonDefaults.buttonColors(
                    containerColor = semantic.warningAccent,
                    contentColor = Color.White,
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("grant_all_files_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(Modifier.width(8.dp))
                  Text(stringResource(R.string.grant_all_files_access))
                }
              }
            }
          }

          Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (uiState.scanTarget != null) {
              MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
              MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = if (uiState.scanTarget is ScanTarget.WholeDevice) {
                  Icons.Filled.Devices
                } else {
                  Icons.Filled.FolderOpen
                },
                contentDescription = "Selected scan target",
                tint = if (uiState.scanTarget != null) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(28.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = uiState.scanTarget?.displayLabel
                    ?: stringResource(R.string.no_folder_selected),
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
                val vols = uiState.mountedVolumes.joinToString(", ") { it.label }
                val subtext = if (uiState.scanTarget is ScanTarget.WholeDevice) {
                  if (vols.isNotBlank()) "Volumes: $vols" else "Internal storage & mounted SD cards"
                } else if (uiState.hasAllFilesAccess) {
                  stringResource(R.string.all_files_granted_note)
                } else if (vols.isNotBlank()) {
                  "Detected: $vols — grant All Files Access to scan"
                } else {
                  "Tap Grant All Files Access above or Browse Folders"
                }
                Text(
                  text = subtext,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            FilledTonalButton(
              onClick = {
                if (uiState.hasAllFilesAccess) {
                  onSelectWholeDevice()
                } else {
                  showAllFilesRationale = true
                }
              },
              enabled = !uiState.isScanning,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("whole_device_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Devices,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text("Whole Device + SD Cards")
            }

            OutlinedButton(
              onClick = {
                if (uiState.hasAllFilesAccess) {
                  onOpenFolderBrowser()
                } else {
                  showAllFilesRationale = true
                }
              },
              enabled = !uiState.isScanning,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("choose_folder_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.choose_folder))
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Switch(
              checked = uiState.includeSubfolders,
              onCheckedChange = onIncludeSubfoldersChanged,
              enabled = !uiState.isScanning,
              modifier = Modifier.testTag("include_subfolders_switch"),
            )
            Text(
              text = stringResource(R.string.include_subfolders),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }

      // 2. Minimum File Size Card
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.section_min_size),
              style = MaterialTheme.typography.titleMedium,
            )
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
              Text(
                text = "≥ ${Formatters.formatBytes(uiState.minSizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            MinSizePreset.entries.forEach { preset ->
              val selected = !showCustomSizeEditor && uiState.minSizeBytes == preset.bytes
              FilterChip(
                selected = selected,
                onClick = {
                  showCustomSizeEditor = false
                  onMinSizeChanged(preset.bytes)
                },
                enabled = !uiState.isScanning,
                label = { Text(preset.label) },
                leadingIcon = if (selected) {
                  {
                    Icon(
                      imageVector = Icons.Filled.Check,
                      contentDescription = null,
                      modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                  }
                } else null,
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("min_size_chip_${preset.name.lowercase()}"),
              )
            }

            FilterChip(
              selected = showCustomSizeEditor,
              onClick = { showCustomSizeEditor = !showCustomSizeEditor },
              enabled = !uiState.isScanning,
              label = { Text(stringResource(R.string.custom_size)) },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.Tune,
                  contentDescription = null,
                  modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
              },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("min_size_chip_custom"),
            )
          }

          AnimatedVisibility(visible = showCustomSizeEditor) {
            Column(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(top = 4.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedTextField(
                  value = customSizeInput,
                  onValueChange = { raw ->
                    customSizeInput = raw.filter { it.isDigit() }.take(7)
                  },
                  label = { Text(stringResource(R.string.custom_size_hint)) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("custom_size_input"),
                )
                SizeUnit.entries.forEach { unit ->
                  FilterChip(
                    selected = customSizeUnit == unit,
                    onClick = { customSizeUnit = unit },
                    label = { Text(unit.label) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  )
                }
              }
              OutlinedButton(
                onClick = {
                  val num = customSizeInput.toLongOrNull() ?: 1L
                  val bytes = (num * customSizeUnit.multiplier).coerceAtLeast(1L)
                  onMinSizeChanged(bytes)
                },
                modifier = Modifier
                  .align(Alignment.End)
                  .minimumInteractiveComponentSize()
                  .testTag("apply_custom_size_button"),
              ) {
                Text("Apply Custom Size")
              }
            }
          }
        }
      }

      // 3. File Type Card
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = stringResource(R.string.section_file_type),
            style = MaterialTheme.typography.titleMedium,
          )

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FileTypeFilter.entries.forEach { filter ->
              val selected = uiState.fileTypeFilter == filter
              val icon = when (filter) {
                FileTypeFilter.ALL -> Icons.AutoMirrored.Filled.InsertDriveFile
                FileTypeFilter.IMAGES -> Icons.Filled.Image
                FileTypeFilter.VIDEOS -> Icons.Filled.VideoFile
                FileTypeFilter.AUDIO -> Icons.Filled.AudioFile
                FileTypeFilter.DOCUMENTS -> Icons.Filled.Description
                FileTypeFilter.ARCHIVES -> Icons.Filled.FolderZip
              }
              FilterChip(
                selected = selected,
                onClick = { onFileTypeChanged(filter) },
                enabled = !uiState.isScanning,
                label = { Text(filter.label) },
                leadingIcon = {
                  Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                  )
                },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("file_type_chip_${filter.name.lowercase()}"),
              )
            }
          }
        }
      }

      // Active Scan Progress Card
      AnimatedVisibility(visible = uiState.isScanning) {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = stringResource(R.string.scanning_title),
                style = MaterialTheme.typography.titleMedium,
              )
              OutlinedButton(
                onClick = onCancelScan,
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("cancel_scan_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.cancel_scan))
              }
            }

            val phaseText = when (uiState.scanProgress.phase) {
              ScanPhase.INDEXING -> stringResource(R.string.phase_indexing)
              ScanPhase.BUCKETING -> stringResource(R.string.phase_bucketing)
              ScanPhase.HASHING -> stringResource(R.string.phase_hashing)
            }
            Text(
              text = phaseText,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            if (uiState.scanProgress.phase == ScanPhase.HASHING && uiState.scanProgress.sizeCandidatesCount > 0) {
              val progress = (uiState.scanProgress.hashedFilesCount.toFloat() /
                uiState.scanProgress.sizeCandidatesCount.toFloat()).coerceIn(0f, 1f)
              LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(8.dp)
                  .clip(RoundedCornerShape(4.dp)),
              )
            } else {
              LinearProgressIndicator(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(8.dp)
                  .clip(RoundedCornerShape(4.dp)),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Files checked: ${uiState.scanProgress.indexedFilesCount}",
                style = MaterialTheme.typography.labelSmall,
              )
              if (uiState.scanProgress.sizeCandidatesCount > 0) {
                Text(
                  text = "Hashed: ${uiState.scanProgress.hashedFilesCount}/${uiState.scanProgress.sizeCandidatesCount}",
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }

            if (uiState.scanProgress.currentFileName.isNotBlank()) {
              Text(
                text = uiState.scanProgress.currentFileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      // Start Scan CTA
      Button(
        onClick = {
          if (!uiState.hasAllFilesAccess && uiState.scanTarget == null) {
            onRequestAllFilesAccess()
          } else if (uiState.scanTarget == null) {
            onOpenFolderBrowser()
          } else {
            onStartScan()
          }
        },
        enabled = !uiState.isScanning,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
          .testTag("start_scan_button"),
      ) {
        Icon(
          imageVector = if (!uiState.hasAllFilesAccess && uiState.scanTarget == null) {
            Icons.Filled.LockOpen
          } else if (uiState.scanTarget == null) {
            Icons.Filled.FolderOpen
          } else {
            Icons.Filled.Search
          },
          contentDescription = null,
          modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
          text = if (!uiState.hasAllFilesAccess && uiState.scanTarget == null) {
            stringResource(R.string.grant_all_files_access)
          } else if (uiState.scanTarget == null) {
            "Choose Folder or Whole Device"
          } else {
            stringResource(R.string.start_scan)
          },
          style = MaterialTheme.typography.titleMedium,
        )
      }

      Spacer(Modifier.height(12.dp))
    }
  }
}
