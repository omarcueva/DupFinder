package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DuplicateScanner
import com.example.data.PreferencesRepository
import com.example.data.TrashRepository
import com.example.model.AppDestination
import com.example.model.DuplicateGroup
import com.example.model.FileTypeFilter
import com.example.model.MinSizePreset
import com.example.model.ResultSortOrder
import com.example.model.ScanProgress
import com.example.model.ScanSummary
import com.example.model.ScanTarget
import com.example.model.ScannedFile
import com.example.model.StorageVolumeInfo
import com.example.model.TrashedItem
import com.example.util.Formatters
import com.example.util.StorageAccessHelper
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DupSweepUiState(
  val currentDestination: AppDestination = AppDestination.SCAN,
  val hasAllFilesAccess: Boolean = false,
  val mountedVolumes: List<StorageVolumeInfo> = emptyList(),
  val scanTarget: ScanTarget? = null,
  val showFolderBrowserDialog: Boolean = false,
  val minSizeBytes: Long = MinSizePreset.KB_10.bytes,
  val fileTypeFilter: FileTypeFilter = FileTypeFilter.ALL,
  val includeSubfolders: Boolean = true,

  val isScanning: Boolean = false,
  val scanProgress: ScanProgress = ScanProgress(),
  val hasCompletedScan: Boolean = false,
  val scanSummary: ScanSummary? = null,

  val duplicateGroups: List<DuplicateGroup> = emptyList(),
  val selectedUris: Set<Uri> = emptySet(),
  val sortOrder: ResultSortOrder = ResultSortOrder.LARGEST_RECLAIMABLE,
  val inspectingGroup: DuplicateGroup? = null,

  val showMoveToTrashConfirm: Boolean = false,
  val isMovingToTrash: Boolean = false,
  val trashedItems: List<TrashedItem> = emptyList(),
  val showEmptyTrashConfirm: Boolean = false,
) {
  val sortedGroups: List<DuplicateGroup>
    get() = when (sortOrder) {
      ResultSortOrder.LARGEST_RECLAIMABLE -> duplicateGroups.sortedByDescending { it.reclaimableBytes }
      ResultSortOrder.MOST_DUPLICATES -> duplicateGroups.sortedByDescending { it.files.size }
      ResultSortOrder.FILE_SIZE -> duplicateGroups.sortedByDescending { it.sizeBytes }
      ResultSortOrder.NAME -> duplicateGroups.sortedBy { it.originalFile.displayName.lowercase() }
    }

  val totalDuplicateFilesCount: Int
    get() = duplicateGroups.sumOf { it.duplicateFiles.size }

  val totalReclaimableBytes: Long
    get() = duplicateGroups.sumOf { it.reclaimableBytes }

  val selectedFiles: List<ScannedFile>
    get() = duplicateGroups.flatMap { it.files }.filter { it.uri in selectedUris }

  val selectedCount: Int
    get() = selectedFiles.size

  val selectedBytes: Long
    get() = selectedFiles.sumOf { it.sizeBytes }

  val totalTrashBytes: Long
    get() = trashedItems.sumOf { it.sizeBytes }
}

sealed interface UiEvent {
  data class ShowMessage(val message: String) : UiEvent
  data class LaunchSystemTrash(val intentSender: IntentSender) : UiEvent
  data class NavigateTo(val destination: AppDestination) : UiEvent
}

class DupSweepViewModel(application: Application) : AndroidViewModel(application) {

  private val scanner = DuplicateScanner(application)
  private val trashRepository = TrashRepository(application)
  private val prefsRepository = PreferencesRepository(application)

  private val _uiState = MutableStateFlow(DupSweepUiState())
  val uiState: StateFlow<DupSweepUiState> = _uiState.asStateFlow()

  private val _events = MutableSharedFlow<UiEvent>()
  val events: SharedFlow<UiEvent> = _events.asSharedFlow()

  private var activeScanJob: Job? = null
  private var pendingSystemTrashFiles: List<ScannedFile> = emptyList()
  private var pendingAppTrashFiles: List<ScannedFile> = emptyList()

  init {
    viewModelScope.launch {
      val context = getApplication<Application>()
      val hasAccess = StorageAccessHelper.hasAllFilesAccess(context)
      val volumes = StorageAccessHelper.getMountedStorageVolumes(context)
      val prefs = prefsRepository.preferencesFlow.first()
      val restoredTarget = when (val t = prefs.scanTarget) {
        is ScanTarget.SafTree -> if (hasPersistedAccess(t.treeUri)) t else null
        is ScanTarget.FileSystemFolder -> if (hasAccess && File(t.path).exists()) t else null
        is ScanTarget.WholeDevice -> if (hasAccess) t else null
        null -> if (hasAccess) ScanTarget.WholeDevice() else null
      }
      val trash = trashRepository.loadTrashedItems()
      _uiState.update {
        it.copy(
          hasAllFilesAccess = hasAccess,
          mountedVolumes = volumes,
          scanTarget = restoredTarget,
          minSizeBytes = prefs.minSizeBytes,
          fileTypeFilter = prefs.fileTypeFilter,
          includeSubfolders = prefs.includeSubfolders,
          trashedItems = trash,
        )
      }
    }
  }

  fun refreshStorageState() {
    val context = getApplication<Application>()
    val hasAccess = StorageAccessHelper.hasAllFilesAccess(context)
    val volumes = StorageAccessHelper.getMountedStorageVolumes(context)
    _uiState.update { state ->
      val updatedTarget = if (state.scanTarget == null && hasAccess) {
        ScanTarget.WholeDevice()
      } else {
        state.scanTarget
      }
      state.copy(
        hasAllFilesAccess = hasAccess,
        mountedVolumes = volumes,
        scanTarget = updatedTarget,
      )
    }
  }

  private fun hasPersistedAccess(uri: Uri): Boolean {
    val perms = getApplication<Application>().contentResolver.persistedUriPermissions
    return perms.any { it.uri == uri && it.isReadPermission }
  }

  fun onDestinationSelected(destination: AppDestination) {
    _uiState.update { it.copy(currentDestination = destination) }
  }

  fun openFolderBrowser() {
    refreshStorageState()
    _uiState.update { it.copy(showFolderBrowserDialog = true) }
  }

  fun closeFolderBrowser() {
    _uiState.update { it.copy(showFolderBrowserDialog = false) }
  }

  fun selectWholeDevice() {
    val target = ScanTarget.WholeDevice()
    _uiState.update {
      it.copy(
        scanTarget = target,
        showFolderBrowserDialog = false,
      )
    }
    viewModelScope.launch {
      prefsRepository.saveScanTarget(target)
    }
  }

  fun selectFileSystemFolder(folder: File, label: String) {
    val target = ScanTarget.FileSystemFolder(path = folder.absolutePath, label = label)
    _uiState.update {
      it.copy(
        scanTarget = target,
        showFolderBrowserDialog = false,
      )
    }
    viewModelScope.launch {
      prefsRepository.saveScanTarget(target)
    }
  }

  fun onSafFolderSelected(treeUri: Uri) {
    val context = getApplication<Application>()
    try {
      val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      context.contentResolver.takePersistableUriPermission(treeUri, flags)
    } catch (_: SecurityException) {
      try {
        context.contentResolver.takePersistableUriPermission(
          treeUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      } catch (_: Exception) {
      }
    }
    val label = Formatters.resolveFolderDisplayName(context, treeUri)
    val target = ScanTarget.SafTree(treeUri = treeUri, label = label)
    _uiState.update {
      it.copy(
        scanTarget = target,
        showFolderBrowserDialog = false,
      )
    }
    viewModelScope.launch {
      prefsRepository.saveScanTarget(target)
    }
  }

  fun onMinSizeChanged(bytes: Long) {
    val safeBytes = bytes.coerceAtLeast(1L)
    _uiState.update { it.copy(minSizeBytes = safeBytes) }
    viewModelScope.launch {
      prefsRepository.saveMinSizeBytes(safeBytes)
    }
  }

  fun onFileTypeChanged(filter: FileTypeFilter) {
    _uiState.update { it.copy(fileTypeFilter = filter) }
    viewModelScope.launch {
      prefsRepository.saveFileType(filter)
    }
  }

  fun onIncludeSubfoldersChanged(include: Boolean) {
    _uiState.update { it.copy(includeSubfolders = include) }
    viewModelScope.launch {
      prefsRepository.saveIncludeSubfolders(include)
    }
  }

  fun startScan() {
    val state = _uiState.value
    val target = state.scanTarget
    if (target == null) {
      viewModelScope.launch {
        _events.emit(UiEvent.ShowMessage("Please choose a folder or Whole Device before scanning."))
      }
      return
    }

    if ((target is ScanTarget.WholeDevice || target is ScanTarget.FileSystemFolder) &&
      !StorageAccessHelper.hasAllFilesAccess(getApplication())
    ) {
      viewModelScope.launch {
        _events.emit(UiEvent.ShowMessage("Please grant All Files Access to scan device folders and SD cards."))
      }
      return
    }

    activeScanJob?.cancel()
    activeScanJob = viewModelScope.launch {
      _uiState.update {
        it.copy(
          isScanning = true,
          scanProgress = ScanProgress(),
        )
      }
      try {
        val result = scanner.scanTarget(
          target = target,
          minSizeBytes = state.minSizeBytes,
          fileTypeFilter = state.fileTypeFilter,
          includeSubfolders = state.includeSubfolders,
        ) { progress ->
          _uiState.update { it.copy(scanProgress = progress) }
        }

        // Select ALL duplicates by default, never the Original!
        val defaultSelected = result.groups
          .flatMap { it.duplicateFiles }
          .map { it.uri }
          .toSet()

        val totalDuplicates = result.groups.sumOf { it.duplicateFiles.size }
        val reclaimable = result.groups.sumOf { it.reclaimableBytes }

        val summary = ScanSummary(
          folderLabel = target.displayLabel,
          totalFilesScanned = result.totalFilesScanned,
          groupsCount = result.groups.size,
          totalDuplicatesCount = totalDuplicates,
          totalReclaimableBytes = reclaimable,
          scanDurationMillis = result.durationMillis,
        )

        _uiState.update {
          it.copy(
            isScanning = false,
            hasCompletedScan = true,
            duplicateGroups = result.groups,
            selectedUris = defaultSelected,
            scanSummary = summary,
            currentDestination = AppDestination.RESULTS,
          )
        }
        _events.emit(UiEvent.NavigateTo(AppDestination.RESULTS))
      } catch (_: CancellationException) {
        _uiState.update { it.copy(isScanning = false) }
        _events.emit(UiEvent.ShowMessage("Scan cancelled."))
      } catch (_: SecurityException) {
        _uiState.update { it.copy(isScanning = false) }
        _events.emit(UiEvent.ShowMessage("Storage permission missing or expired. Please grant access and retry."))
      } catch (e: Exception) {
        _uiState.update { it.copy(isScanning = false) }
        _events.emit(UiEvent.ShowMessage("Scan failed: ${e.localizedMessage ?: "Unknown error"}"))
      }
    }
  }

  fun cancelScan() {
    activeScanJob?.cancel()
    _uiState.update { it.copy(isScanning = false) }
  }

  fun toggleFileSelection(uri: Uri) {
    _uiState.update { state ->
      val updated = if (uri in state.selectedUris) {
        state.selectedUris - uri
      } else {
        state.selectedUris + uri
      }
      state.copy(selectedUris = updated)
    }
  }

  fun promoteToOriginal(groupId: String, newOriginalUri: Uri) {
    _uiState.update { state ->
      var previousOriginalUri: Uri? = null
      val updatedGroups = state.duplicateGroups.map { group ->
        if (group.id == groupId && group.originalFile.uri != newOriginalUri) {
          previousOriginalUri = group.originalFile.uri
          group.copy(originalUri = newOriginalUri)
        } else {
          group
        }
      }
      val updatedSelection = state.selectedUris.toMutableSet().apply {
        remove(newOriginalUri)
        previousOriginalUri?.let { add(it) }
      }
      val updatedInspecting = state.inspectingGroup?.let { inspecting ->
        updatedGroups.firstOrNull { it.id == inspecting.id }
      }
      state.copy(
        duplicateGroups = updatedGroups,
        selectedUris = updatedSelection,
        inspectingGroup = updatedInspecting,
      )
    }
  }

  fun selectAllDuplicates() {
    _uiState.update { state ->
      val allDuplicateUris = state.duplicateGroups
        .flatMap { it.duplicateFiles }
        .map { it.uri }
        .toSet()
      state.copy(selectedUris = allDuplicateUris)
    }
  }

  fun clearSelection() {
    _uiState.update { it.copy(selectedUris = emptySet()) }
  }

  fun onSortOrderChanged(order: ResultSortOrder) {
    _uiState.update { it.copy(sortOrder = order) }
  }

  fun openComparisonPreview(group: DuplicateGroup) {
    _uiState.update { it.copy(inspectingGroup = group) }
  }

  fun closeComparisonPreview() {
    _uiState.update { it.copy(inspectingGroup = null) }
  }

  fun requestMoveToTrash() {
    if (_uiState.value.selectedCount == 0) return
    _uiState.update { it.copy(showMoveToTrashConfirm = true) }
  }

  fun dismissMoveToTrashConfirm() {
    _uiState.update { it.copy(showMoveToTrashConfirm = false) }
  }

  fun confirmMoveToTrash() {
    val filesToTrash = _uiState.value.selectedFiles
    if (filesToTrash.isEmpty()) {
      _uiState.update { it.copy(showMoveToTrashConfirm = false) }
      return
    }

    _uiState.update { it.copy(showMoveToTrashConfirm = false, isMovingToTrash = true) }

    viewModelScope.launch {
      val plan = trashRepository.prepareMoveToTrash(filesToTrash)
      val sender = trashRepository.buildSystemTrashIntentSender(plan.systemMediaUris)
      if (sender != null && plan.systemBackedFiles.isNotEmpty()) {
        pendingSystemTrashFiles = plan.systemBackedFiles
        pendingAppTrashFiles = plan.appTrashFiles
        _events.emit(UiEvent.LaunchSystemTrash(sender))
      } else {
        executeAppTrashMove(filesToTrash, systemTrashedCount = 0, systemTrashedBytes = 0L)
      }
    }
  }

  fun onSystemTrashResult(approved: Boolean) {
    val sysFiles = pendingSystemTrashFiles
    val appFiles = pendingAppTrashFiles
    pendingSystemTrashFiles = emptyList()
    pendingAppTrashFiles = emptyList()

    viewModelScope.launch {
      val sysUris = if (approved) sysFiles.map { it.uri }.toSet() else emptySet()
      val sysBytes = if (approved) sysFiles.sumOf { it.sizeBytes } else 0L
      val sysCount = if (approved) sysFiles.size else 0

      if (appFiles.isNotEmpty() && (approved || sysFiles.isEmpty())) {
        val appResult = trashRepository.moveToAppTrash(appFiles)
        val combinedRemoved = sysUris + appResult.trashedUris
        applyRemovedFiles(combinedRemoved)
        refreshTrashList()
        val totalMoved = sysCount + appResult.movedToAppTrashCount
        val freed = sysBytes + appFiles.filter { it.uri in appResult.trashedUris }.sumOf { it.sizeBytes }
        _uiState.update { it.copy(isMovingToTrash = false) }
        _events.emit(
          UiEvent.ShowMessage("Moved $totalMoved file(s) to Trash (${Formatters.formatBytes(freed)} freed).")
        )
      } else if (approved) {
        applyRemovedFiles(sysUris)
        _uiState.update { it.copy(isMovingToTrash = false) }
        _events.emit(
          UiEvent.ShowMessage("Moved $sysCount media file(s) to System Trash (${Formatters.formatBytes(sysBytes)} freed).")
        )
      } else {
        _uiState.update { it.copy(isMovingToTrash = false) }
        _events.emit(UiEvent.ShowMessage("Move to Trash cancelled."))
      }
    }
  }

  private suspend fun executeAppTrashMove(
    files: List<ScannedFile>,
    systemTrashedCount: Int,
    systemTrashedBytes: Long,
  ) {
    val result = trashRepository.moveToAppTrash(files)
    applyRemovedFiles(result.trashedUris)
    refreshTrashList()
    _uiState.update { it.copy(isMovingToTrash = false) }

    val totalMoved = systemTrashedCount + result.movedToAppTrashCount
    val freed = systemTrashedBytes + files.filter { it.uri in result.trashedUris }.sumOf { it.sizeBytes }
    val msg = buildString {
      if (totalMoved > 0) {
        append("Moved $totalMoved file(s) to Trash (${Formatters.formatBytes(freed)} freed).")
      }
      if (result.failedCount > 0) {
        if (isNotEmpty()) append(" ")
        append("${result.failedCount} file(s) could not be moved.")
      }
    }
    if (msg.isNotBlank()) {
      _events.emit(UiEvent.ShowMessage(msg))
    }
  }

  private fun applyRemovedFiles(removedUris: Set<Uri>) {
    if (removedUris.isEmpty()) return
    _uiState.update { state ->
      val updatedGroups = state.duplicateGroups.mapNotNull { group ->
        val remaining = group.files.filterNot { it.uri in removedUris }
        if (remaining.size < 2) {
          null
        } else {
          val newOriginal = remaining.firstOrNull { it.uri == group.originalUri } ?: remaining.first()
          group.copy(files = remaining, originalUri = newOriginal.uri)
        }
      }
      val validUris = updatedGroups.flatMap { it.files }.map { it.uri }.toSet()
      val updatedSelected = (state.selectedUris - removedUris).intersect(validUris)
      val updatedInspecting = state.inspectingGroup?.let { inspecting ->
        updatedGroups.firstOrNull { it.id == inspecting.id }
      }
      state.copy(
        duplicateGroups = updatedGroups,
        selectedUris = updatedSelected,
        inspectingGroup = updatedInspecting,
      )
    }
  }

  fun restoreTrashItem(item: TrashedItem) {
    viewModelScope.launch {
      val restored = trashRepository.restoreItem(item)
      refreshTrashList()
      val msg = if (restored) {
        "Restored ${item.displayName} to ${item.relativePath}"
      } else {
        "Could not restore ${item.displayName}. Check folder write permission."
      }
      _events.emit(UiEvent.ShowMessage(msg))
    }
  }

  fun deleteTrashItemPermanently(item: TrashedItem) {
    viewModelScope.launch {
      trashRepository.deletePermanently(item)
      refreshTrashList()
      _events.emit(UiEvent.ShowMessage("Permanently deleted ${item.displayName}."))
    }
  }

  fun requestEmptyTrash() {
    if (_uiState.value.trashedItems.isEmpty()) return
    _uiState.update { it.copy(showEmptyTrashConfirm = true) }
  }

  fun dismissEmptyTrashConfirm() {
    _uiState.update { it.copy(showEmptyTrashConfirm = false) }
  }

  fun confirmEmptyTrash() {
    _uiState.update { it.copy(showEmptyTrashConfirm = false) }
    viewModelScope.launch {
      trashRepository.emptyTrash()
      refreshTrashList()
      _events.emit(UiEvent.ShowMessage("Trash emptied permanently."))
    }
  }

  private suspend fun refreshTrashList() {
    val items = trashRepository.loadTrashedItems()
    _uiState.update { it.copy(trashedItems = items) }
  }
}
