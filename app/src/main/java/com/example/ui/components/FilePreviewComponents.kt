package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.DuplicateGroup
import com.example.model.ScannedFile
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDupSweepColors
import com.example.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FilePreviewThumbnail(
  file: ScannedFile,
  modifier: Modifier = Modifier,
  size: Dp = 68.dp,
  showPlayBadgeForVideo: Boolean = true,
) {
  val context = LocalContext.current
  val shape = RoundedCornerShape(12.dp)

  Box(
    modifier = modifier
      .size(size)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    contentAlignment = Alignment.Center,
  ) {
    when {
      file.isImage || file.isVideo -> {
        AsyncImage(
          model = ImageRequest.Builder(context)
            .data(file.uri)
            .crossfade(true)
            .build(),
          contentDescription = "Preview of ${file.displayName}",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        if (file.isVideo && showPlayBadgeForVideo) {
          Box(
            modifier = Modifier
              .size(26.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = "Video file",
              tint = Color.White,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      file.isPdf -> {
        val pdfBitmap by produceState<Bitmap?>(initialValue = null, key1 = file.uri) {
          value = renderPdfFirstPage(context, file.uri, maxWidthPx = 280)
        }
        val bmp = pdfBitmap
        if (bmp != null) {
          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "PDF preview of ${file.displayName}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(
            imageVector = Icons.Filled.PictureAsPdf,
            contentDescription = "PDF document",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(30.dp),
          )
        }
      }

      file.isAudio -> {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier.padding(4.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.AudioFile,
            contentDescription = "Audio file",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
          )
          if (file.extension.isNotBlank()) {
            Text(
              text = file.extension.take(4),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      file.isTextPreviewable -> {
        val snippet by produceState<String?>(initialValue = null, key1 = file.uri) {
          value = readTextSnippet(context, file.uri, maxChars = 140)
        }
        val text = snippet
        if (!text.isNullOrBlank()) {
          Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
              fontFamily = JetBrainsMonoFamily,
              fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.78f,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
              .fillMaxSize()
              .padding(6.dp),
          )
        } else {
          Icon(
            imageVector = Icons.Filled.Description,
            contentDescription = "Text document",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
          )
        }
      }

      else -> {
        val icon = when {
          file.mimeType.contains("zip") || file.extension in setOf("ZIP", "RAR", "7Z", "TAR", "GZ", "APK") ->
            Icons.Filled.FolderZip
          file.isVideo -> Icons.Filled.VideoFile
          else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier.padding(4.dp),
        ) {
          Icon(
            imageVector = icon,
            contentDescription = "File icon",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(26.dp),
          )
          if (file.extension.isNotBlank()) {
            Text(
              text = file.extension.take(5),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

@Composable
fun LargePreviewPane(
  file: ScannedFile,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(16f / 10f)
      .clip(RoundedCornerShape(14.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    contentAlignment = Alignment.Center,
  ) {
    when {
      file.isImage || file.isVideo -> {
        AsyncImage(
          model = ImageRequest.Builder(context)
            .data(file.uri)
            .crossfade(true)
            .build(),
          contentDescription = "Large preview of ${file.displayName}",
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
        )
      }

      file.isPdf -> {
        val pdfBitmap by produceState<Bitmap?>(initialValue = null, key1 = file.uri) {
          value = renderPdfFirstPage(context, file.uri, maxWidthPx = 720)
        }
        val bmp = pdfBitmap
        if (bmp != null) {
          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "PDF page preview of ${file.displayName}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(
            imageVector = Icons.Filled.PictureAsPdf,
            contentDescription = "PDF document",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(48.dp),
          )
        }
      }

      file.isAudio -> {
        AudioPreviewPlayer(uri = file.uri, displayName = file.displayName)
      }

      file.isTextPreviewable -> {
        val snippet by produceState<String?>(initialValue = null, key1 = file.uri) {
          value = readTextSnippet(context, file.uri, maxChars = 600)
        }
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        ) {
          Text(
            text = snippet ?: "Unable to preview text content.",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      else -> {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = "File preview",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
          )
          Text(
            text = file.mimeType,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun AudioPreviewPlayer(uri: Uri, displayName: String) {
  val context = LocalContext.current
  var isPlaying by remember(uri) { mutableStateOf(false) }
  val player = remember(uri) {
    try {
      MediaPlayer().apply {
        setDataSource(context, uri)
        prepare()
        setOnCompletionListener { isPlaying = false }
      }
    } catch (_: Exception) {
      null
    }
  }

  DisposableEffect(player) {
    onDispose {
      try {
        if (player?.isPlaying == true) player.stop()
        player?.release()
      } catch (_: Exception) {
      }
    }
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.padding(16.dp),
  ) {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier
        .size(56.dp)
        .clickable(enabled = player != null) {
          if (player == null) return@clickable
          if (isPlaying) {
            player.pause()
            isPlaying = false
          } else {
            player.start()
            isPlaying = true
          }
        },
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (isPlaying) "Pause $displayName" else "Play $displayName",
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(30.dp),
        )
      }
    }
    Text(
      text = if (player == null) "Audio preview unavailable" else if (isPlaying) "Playing preview…" else "Tap to play audio preview",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun ComparisonPreviewDialog(
  group: DuplicateGroup,
  selectedUris: Set<Uri>,
  onToggleSelection: (Uri) -> Unit,
  onPromoteToOriginal: (Uri) -> Unit,
  onDismiss: () -> Unit,
) {
  val semantic = LocalDupSweepColors.current
  var comparedIndex by remember(group.id, group.originalFile.uri) { mutableStateOf(0) }
  val duplicates = group.duplicateFiles
  val comparedDuplicate = duplicates.getOrNull(comparedIndex.coerceIn(0, (duplicates.size - 1).coerceAtLeast(0)))

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .padding(vertical = 20.dp),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
    ) {
      Column(
        modifier = Modifier
          .verticalScroll(rememberScrollState())
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Original vs. Duplicate Preview",
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = "${Formatters.formatBytes(group.sizeBytes)} each • SHA-256 ${group.sha256.take(12)}…",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("close_comparison_dialog"),
          ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close comparison")
          }
        }

        // Original Panel
        ComparisonFileCard(
          title = "ORIGINAL • KEPT",
          accentColor = semantic.originalAccent,
          containerColor = semantic.originalContainer,
          file = group.originalFile,
          isOriginal = true,
          isSelectedForTrash = group.originalFile.uri in selectedUris,
          onToggleTrash = { onToggleSelection(group.originalFile.uri) },
          onPromoteToOriginal = null,
        )

        if (duplicates.size > 1) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            duplicates.forEachIndexed { idx, _ ->
              val isActive = idx == comparedIndex
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isActive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = if (isActive) BorderStroke(1.dp, semantic.duplicateAccent) else null,
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .clickable { comparedIndex = idx },
              ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                  Text(
                    text = "Copy #${idx + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        if (comparedDuplicate != null) {
          ComparisonFileCard(
            title = "DUPLICATE #${comparedIndex + 1}",
            accentColor = semantic.duplicateAccent,
            containerColor = semantic.duplicateContainer,
            file = comparedDuplicate,
            isOriginal = false,
            isSelectedForTrash = comparedDuplicate.uri in selectedUris,
            onToggleTrash = { onToggleSelection(comparedDuplicate.uri) },
            onPromoteToOriginal = { onPromoteToOriginal(comparedDuplicate.uri) },
          )
        }

        Button(
          onClick = onDismiss,
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        ) {
          Text("Done Comparing")
        }
      }
    }
  }
}

@Composable
private fun ComparisonFileCard(
  title: String,
  accentColor: Color,
  containerColor: Color,
  file: ScannedFile,
  isOriginal: Boolean,
  isSelectedForTrash: Boolean,
  onToggleTrash: () -> Unit,
  onPromoteToOriginal: (() -> Unit)?,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.7f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = containerColor,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = if (isOriginal) Icons.Filled.Shield else Icons.Filled.DeleteOutline,
              contentDescription = null,
              tint = accentColor,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = title,
              style = MaterialTheme.typography.labelMedium,
              color = accentColor,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = if (isSelectedForTrash) "Trash" else "Keep",
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelectedForTrash) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Checkbox(
            checked = isSelectedForTrash,
            onCheckedChange = { onToggleTrash() },
            colors = CheckboxDefaults.colors(checkedColor = accentColor),
          )
        }
      }

      LargePreviewPane(file = file)

      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = file.displayName,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "Folder: ${file.relativePath}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "Modified: ${Formatters.formatDate(file.lastModifiedMillis)} • ${Formatters.formatBytes(file.sizeBytes)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (onPromoteToOriginal != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        OutlinedButton(
          onClick = onPromoteToOriginal,
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
          Icon(
            imageVector = Icons.Filled.SwapHoriz,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text("Keep This File as Original Instead")
        }
      }
    }
  }
}

private suspend fun renderPdfFirstPage(
  context: android.content.Context,
  uri: Uri,
  maxWidthPx: Int,
): Bitmap? = withContext(Dispatchers.IO) {
  try {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
      PdfRenderer(pfd).use { renderer ->
        if (renderer.pageCount <= 0) return@withContext null
        renderer.openPage(0).use { page ->
          val scale = maxWidthPx.toFloat() / page.width.coerceAtLeast(1)
          val w = maxWidthPx
          val h = (page.height * scale).toInt().coerceAtLeast(1)
          val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
          bmp.eraseColor(android.graphics.Color.WHITE)
          page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
          bmp
        }
      }
    }
  } catch (_: Exception) {
    null
  }
}

private suspend fun readTextSnippet(
  context: android.content.Context,
  uri: Uri,
  maxChars: Int,
): String? = withContext(Dispatchers.IO) {
  try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
      val bytes = ByteArray(maxChars * 2)
      val read = stream.read(bytes)
      if (read <= 0) return@withContext null
      String(bytes, 0, read, Charsets.UTF_8).take(maxChars)
    }
  } catch (_: Exception) {
    null
  }
}
