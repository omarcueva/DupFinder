package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DuplicateGroup
import com.example.model.ResultSortOrder
import com.example.model.ScannedFile
import com.example.ui.components.ComparisonPreviewDialog
import com.example.ui.components.FilePreviewThumbnail
import com.example.ui.theme.LocalDupSweepColors
import com.example.util.Formatters
import com.example.viewmodel.DupSweepUiState

@Composable
fun ResultsScreen(
  uiState: DupSweepUiState,
  onToggleFileSelection: (Uri) -> Unit,
  onSelectAllDuplicates: () -> Unit,
  onClearSelection: () -> Unit,
  onPromoteToOriginal: (String, Uri) -> Unit,
  onSortOrderChanged: (ResultSortOrder) -> Unit,
  onOpenComparison: (DuplicateGroup) -> Unit,
  onCloseComparison: () -> Unit,
  onRequestMoveToTrash: () -> Unit,
  onDismissMoveToTrashConfirm: () -> Unit,
  onConfirmMoveToTrash: () -> Unit,
  onNavigateToScan: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val groups = uiState.sortedGroups

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    if (!uiState.hasCompletedScan) {
      EmptyResultsPlaceholder(
        title = "No Scan Run Yet",
        subtitle = "Choose a folder, minimum file size, and file type on the Scan tab to detect byte-identical duplicates.",
        buttonText = "Go to Scan Setup",
        onAction = onNavigateToScan,
      )
    } else if (groups.isEmpty()) {
      val scannedCount = uiState.scanSummary?.totalFilesScanned ?: 0
      EmptyResultsPlaceholder(
        title = "No Duplicates Found",
        subtitle = "Checked $scannedCount file(s) by SHA-256 content hash. Every file in this folder is unique!",
        buttonText = "Scan Another Folder",
        isSuccess = true,
        onAction = onNavigateToScan,
      )
    } else {
      Column(
        modifier = Modifier
          .widthIn(max = 720.dp)
          .fillMaxSize(),
      ) {
        // Scrollable duplicate groups list
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          item(key = "summary_header") {
            ResultsSummaryAndToolbar(
              uiState = uiState,
              onSelectAllDuplicates = onSelectAllDuplicates,
              onClearSelection = onClearSelection,
              onSortOrderChanged = onSortOrderChanged,
            )
          }

          items(
            items = groups,
            key = { it.id },
          ) { group ->
            DuplicateGroupCard(
              group = group,
              selectedUris = uiState.selectedUris,
              onToggleFileSelection = onToggleFileSelection,
              onPromoteToOriginal = { newOrigUri ->
                onPromoteToOriginal(group.id, newOrigUri)
              },
              onCompareClick = { onOpenComparison(group) },
            )
          }
        }

        // Sticky Bottom Action Bar with "Move to Trash"
        Surface(
          tonalElevation = 8.dp,
          shadowElevation = 8.dp,
          color = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${uiState.selectedCount} file(s) selected",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = "${Formatters.formatBytes(uiState.selectedBytes)} reclaimable",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Button(
              onClick = onRequestMoveToTrash,
              enabled = uiState.selectedCount > 0 && !uiState.isMovingToTrash,
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
              ),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("move_to_trash_button"),
            ) {
              if (uiState.isMovingToTrash) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onTertiary,
                )
              } else {
                Icon(
                  imageVector = Icons.Filled.DeleteOutline,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                )
              }
              Spacer(Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.move_to_trash),
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
      }
    }
  }

  // Side-by-Side Comparison Modal
  val inspecting = uiState.inspectingGroup
  if (inspecting != null) {
    ComparisonPreviewDialog(
      group = inspecting,
      selectedUris = uiState.selectedUris,
      onToggleSelection = onToggleFileSelection,
      onPromoteToOriginal = { newUri -> onPromoteToOriginal(inspecting.id, newUri) },
      onDismiss = onCloseComparison,
    )
  }

  // Move to Trash Confirmation Dialog
  if (uiState.showMoveToTrashConfirm) {
    val selectedGroupsAllSelectedCount = uiState.duplicateGroups.count { group ->
      group.files.all { it.uri in uiState.selectedUris }
    }
    AlertDialog(
      onDismissRequest = onDismissMoveToTrashConfirm,
      icon = {
        Icon(
          imageVector = Icons.Filled.DeleteOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary,
        )
      },
      title = { Text(stringResource(R.string.confirm_trash_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Move ${uiState.selectedCount} selected file(s) (${Formatters.formatBytes(uiState.selectedBytes)}) to Trash?",
            style = MaterialTheme.typography.bodyMedium,
          )
          if (selectedGroupsAllSelectedCount > 0) {
            Text(
              text = "Warning: In $selectedGroupsAllSelectedCount group(s), you selected all copies including the Original.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          } else {
            Text(
              text = "Original files remain safely untouched.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = onConfirmMoveToTrash,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
          ),
          modifier = Modifier.testTag("confirm_move_to_trash_dialog_button"),
        ) {
          Text(stringResource(R.string.confirm_action_trash))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissMoveToTrashConfirm) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultsSummaryAndToolbar(
  uiState: DupSweepUiState,
  onSelectAllDuplicates: () -> Unit,
  onClearSelection: () -> Unit,
  onSortOrderChanged: (ResultSortOrder) -> Unit,
) {
  var sortMenuExpanded by remember { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "${uiState.duplicateGroups.size} Duplicate Group(s)",
            style = MaterialTheme.typography.titleLarge,
          )
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
          ) {
            Text(
              text = "Save up to ${Formatters.formatBytes(uiState.totalReclaimableBytes)}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
        val summary = uiState.scanSummary
        Text(
          text = if (summary != null) {
            "${uiState.totalDuplicateFilesCount} duplicates across ${summary.totalFilesScanned} scanned files (${Formatters.formatDuration(summary.scanDurationMillis)})"
          } else {
            "${uiState.totalDuplicateFilesCount} duplicate files detected"
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      FilledTonalButton(
        onClick = onSelectAllDuplicates,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("select_all_duplicates_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.DoneAll,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.select_all_duplicates))
      }

      OutlinedButton(
        onClick = onClearSelection,
        enabled = uiState.selectedCount > 0,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("clear_selection_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.ClearAll,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.clear_selection))
      }

      Box {
        OutlinedButton(
          onClick = { sortMenuExpanded = true },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("sort_results_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Sort,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(uiState.sortOrder.label)
        }
        DropdownMenu(
          expanded = sortMenuExpanded,
          onDismissRequest = { sortMenuExpanded = false },
        ) {
          ResultSortOrder.entries.forEach { order ->
            DropdownMenuItem(
              text = { Text(order.label) },
              onClick = {
                onSortOrderChanged(order)
                sortMenuExpanded = false
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DuplicateGroupCard(
  group: DuplicateGroup,
  selectedUris: Set<Uri>,
  onToggleFileSelection: (Uri) -> Unit,
  onPromoteToOriginal: (Uri) -> Unit,
  onCompareClick: () -> Unit,
) {
  val semantic = LocalDupSweepColors.current
  val original = group.originalFile
  val duplicates = group.duplicateFiles
  val allCopiesSelected = group.files.all { it.uri in selectedUris }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Group Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${Formatters.formatBytes(group.sizeBytes)} • ${group.files.size} identical copies",
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = "SHA-256 ${group.sha256.take(10)}… • Free ${Formatters.formatBytes(group.reclaimableBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        FilledTonalButton(
          onClick = onCompareClick,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("compare_group_button_${group.id}"),
        ) {
          Icon(
            imageVector = Icons.Filled.Compare,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(stringResource(R.string.compare_preview), style = MaterialTheme.typography.labelMedium)
        }
      }

      if (allCopiesSelected) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = semantic.warningContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.WarningAmber,
              contentDescription = null,
              tint = semantic.warningAccent,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "All copies in this group are selected for Trash.",
              style = MaterialTheme.typography.bodySmall,
              color = semantic.warningAccent,
            )
          }
        }
      }

      // Original Preview Row
      FileCopyPreviewRow(
        file = original,
        originalFile = original,
        isOriginal = true,
        isSelected = original.uri in selectedUris,
        onToggleSelection = { onToggleFileSelection(original.uri) },
        onRowClick = onCompareClick,
        onMakeOriginal = null,
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

      // Duplicate Preview Rows
      duplicates.forEach { dup ->
        FileCopyPreviewRow(
          file = dup,
          originalFile = original,
          isOriginal = false,
          isSelected = dup.uri in selectedUris,
          onToggleSelection = { onToggleFileSelection(dup.uri) },
          onRowClick = onCompareClick,
          onMakeOriginal = { onPromoteToOriginal(dup.uri) },
        )
      }
    }
  }
}

@Composable
private fun FileCopyPreviewRow(
  file: ScannedFile,
  originalFile: ScannedFile,
  isOriginal: Boolean,
  isSelected: Boolean,
  onToggleSelection: () -> Unit,
  onRowClick: () -> Unit,
  onMakeOriginal: (() -> Unit)?,
) {
  val semantic = LocalDupSweepColors.current
  val accent = if (isOriginal) semantic.originalAccent else semantic.duplicateAccent
  val containerBg = when {
    isOriginal && !isSelected -> semantic.originalContainer.copy(alpha = 0.55f)
    isSelected -> semantic.duplicateContainer.copy(alpha = 0.7f)
    else -> MaterialTheme.colorScheme.surfaceContainer
  }
  val borderColor = when {
    isOriginal && !isSelected -> semantic.originalAccent.copy(alpha = 0.45f)
    isSelected -> semantic.duplicateAccent.copy(alpha = 0.6f)
    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
  }

  val differentName = !isOriginal && file.displayName != originalFile.displayName
  val differentDate = !isOriginal &&
    file.lastModifiedMillis > 0L &&
    originalFile.lastModifiedMillis > 0L &&
    file.lastModifiedMillis != originalFile.lastModifiedMillis

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = containerBg,
    border = BorderStroke(1.dp, borderColor),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onRowClick),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Checkbox(
        checked = isSelected,
        onCheckedChange = { onToggleSelection() },
        colors = CheckboxDefaults.colors(checkedColor = accent),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag(
            if (isOriginal) "original_checkbox_${file.uri.hashCode()}"
            else "duplicate_checkbox_${file.uri.hashCode()}"
          ),
      )

      FilePreviewThumbnail(file = file, size = 62.dp)

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isOriginal) semantic.originalContainer else semantic.duplicateContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector = if (isOriginal) Icons.Filled.Shield else Icons.Filled.DeleteOutline,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = if (isOriginal) {
                  stringResource(R.string.original_badge)
                } else {
                  stringResource(R.string.duplicate_badge)
                },
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
              )
            }
          }

          if (differentName || differentDate) {
            val tagText = when {
              differentName && differentDate -> "Diff name & date"
              differentName -> "Diff name"
              else -> "Diff date"
            }
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
              Text(
                text = tagText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }

        Text(
          text = file.displayName,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Text(
          text = "${file.relativePath} • ${Formatters.formatShortDate(file.lastModifiedMillis)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (onMakeOriginal != null) {
        IconButton(
          onClick = onMakeOriginal,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("make_original_button_${file.uri.hashCode()}"),
        ) {
          Icon(
            imageVector = Icons.Filled.SwapHoriz,
            contentDescription = "Keep ${file.displayName} as Original instead",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyResultsPlaceholder(
  title: String,
  subtitle: String,
  buttonText: String,
  isSuccess: Boolean = false,
  onAction: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = if (isSuccess) {
        LocalDupSweepColors.current.originalContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
      },
      modifier = Modifier.size(84.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = if (isSuccess) Icons.Filled.CheckCircleOutline else Icons.Filled.Search,
          contentDescription = null,
          tint = if (isSuccess) {
            LocalDupSweepColors.current.originalAccent
          } else {
            MaterialTheme.colorScheme.primary
          },
          modifier = Modifier.size(42.dp),
        )
      }
    }
    Spacer(Modifier.height(18.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    FilledTonalButton(
      onClick = onAction,
      modifier = Modifier.minimumInteractiveComponentSize(),
    ) {
      Text(buttonText)
    }
  }
}
