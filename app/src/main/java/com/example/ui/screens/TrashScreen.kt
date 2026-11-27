package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.TrashedItem
import com.example.util.Formatters
import com.example.viewmodel.DupSweepUiState

@Composable
fun TrashScreen(
  uiState: DupSweepUiState,
  onRestoreItem: (TrashedItem) -> Unit,
  onDeletePermanently: (TrashedItem) -> Unit,
  onRequestEmptyTrash: () -> Unit,
  onDismissEmptyTrash: () -> Unit,
  onConfirmEmptyTrash: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 720.dp)
        .fillMaxSize(),
    ) {
      if (uiState.trashedItems.isEmpty()) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(84.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
              )
            }
          }
          Spacer(Modifier.height(18.dp))
          Text(
            text = stringResource(R.string.trash_empty_title),
            style = MaterialTheme.typography.headlineSmall,
          )
          Spacer(Modifier.height(8.dp))
          Text(
            text = stringResource(R.string.trash_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item(key = "trash_header") {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${uiState.trashedItems.size} item(s) in Trash",
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text = "${Formatters.formatBytes(uiState.totalTrashBytes)} held in safe trash",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                OutlinedButton(
                  onClick = onRequestEmptyTrash,
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("empty_trash_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(stringResource(R.string.empty_trash))
                }
              }
            }
          }

          item(key = "trash_info") {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Filled.Info,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = "Media files trashed via Android's system prompt are stored in your device Gallery/Files Trash for 30 days. Other files appear here.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
          }

          items(
            items = uiState.trashedItems,
            key = { it.id },
          ) { item ->
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = "${item.relativePath} • ${Formatters.formatBytes(item.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = "Trashed ${Formatters.formatDate(item.trashedAtMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                FilledTonalButton(
                  onClick = { onRestoreItem(item) },
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("restore_trash_item_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.RestoreFromTrash,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                  Text(stringResource(R.string.restore), style = MaterialTheme.typography.labelMedium)
                }

                IconButton(
                  onClick = { onDeletePermanently(item) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = "Delete ${item.displayName} permanently",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (uiState.showEmptyTrashConfirm) {
    AlertDialog(
      onDismissRequest = onDismissEmptyTrash,
      title = { Text("Empty Trash Permanently?") },
      text = {
        Text(
          "Permanently delete all ${uiState.trashedItems.size} file(s) (${Formatters.formatBytes(uiState.totalTrashBytes)})? This cannot be undone."
        )
      },
      confirmButton = {
        Button(
          onClick = onConfirmEmptyTrash,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        ) {
          Text(stringResource(R.string.delete_permanently))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissEmptyTrash) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
}
