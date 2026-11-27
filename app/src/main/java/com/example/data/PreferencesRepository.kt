package com.example.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.FileTypeFilter
import com.example.model.MinSizePreset
import com.example.model.ScanTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dupsweep_prefs")

data class UserScanPreferences(
  val scanTarget: ScanTarget? = null,
  val minSizeBytes: Long = MinSizePreset.KB_10.bytes,
  val fileTypeFilter: FileTypeFilter = FileTypeFilter.ALL,
  val includeSubfolders: Boolean = true,
)

class PreferencesRepository(private val context: Context) {

  private object Keys {
    val TARGET_TYPE = stringPreferencesKey("target_type")
    val TARGET_VALUE = stringPreferencesKey("target_value")
    val TARGET_LABEL = stringPreferencesKey("target_label")
    val MIN_SIZE_BYTES = longPreferencesKey("min_size_bytes")
    val FILE_TYPE = stringPreferencesKey("file_type")
    val INCLUDE_SUBFOLDERS = booleanPreferencesKey("include_subfolders")
  }

  val preferencesFlow: Flow<UserScanPreferences> = context.dataStore.data.map { prefs ->
    val targetType = prefs[Keys.TARGET_TYPE].orEmpty()
    val targetValue = prefs[Keys.TARGET_VALUE].orEmpty()
    val targetLabel = prefs[Keys.TARGET_LABEL].orEmpty()
    val target: ScanTarget? = when (targetType) {
      "WHOLE_DEVICE" -> ScanTarget.WholeDevice()
      "FILESYSTEM" -> targetValue.takeIf { it.isNotBlank() }?.let {
        ScanTarget.FileSystemFolder(path = it, label = targetLabel.ifBlank { it })
      }
      "SAF" -> targetValue.takeIf { it.isNotBlank() }?.let {
        ScanTarget.SafTree(treeUri = Uri.parse(it), label = targetLabel)
      }
      else -> null
    }

    val typeName = prefs[Keys.FILE_TYPE]
    val filter = FileTypeFilter.entries.firstOrNull { it.name == typeName } ?: FileTypeFilter.ALL
    UserScanPreferences(
      scanTarget = target,
      minSizeBytes = prefs[Keys.MIN_SIZE_BYTES] ?: MinSizePreset.KB_10.bytes,
      fileTypeFilter = filter,
      includeSubfolders = prefs[Keys.INCLUDE_SUBFOLDERS] ?: true,
    )
  }

  suspend fun saveScanTarget(target: ScanTarget) {
    context.dataStore.edit { prefs ->
      when (target) {
        is ScanTarget.WholeDevice -> {
          prefs[Keys.TARGET_TYPE] = "WHOLE_DEVICE"
          prefs[Keys.TARGET_VALUE] = ""
          prefs[Keys.TARGET_LABEL] = target.label
        }
        is ScanTarget.FileSystemFolder -> {
          prefs[Keys.TARGET_TYPE] = "FILESYSTEM"
          prefs[Keys.TARGET_VALUE] = target.path
          prefs[Keys.TARGET_LABEL] = target.label
        }
        is ScanTarget.SafTree -> {
          prefs[Keys.TARGET_TYPE] = "SAF"
          prefs[Keys.TARGET_VALUE] = target.treeUri.toString()
          prefs[Keys.TARGET_LABEL] = target.label
        }
      }
    }
  }

  suspend fun saveMinSizeBytes(bytes: Long) {
    context.dataStore.edit { prefs ->
      prefs[Keys.MIN_SIZE_BYTES] = bytes
    }
  }

  suspend fun saveFileType(filter: FileTypeFilter) {
    context.dataStore.edit { prefs ->
      prefs[Keys.FILE_TYPE] = filter.name
    }
  }

  suspend fun saveIncludeSubfolders(include: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[Keys.INCLUDE_SUBFOLDERS] = include
    }
  }
}
