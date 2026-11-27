package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.AppDestination
import com.example.model.ScanTarget
import com.example.ui.components.FolderPickerDialog
import com.example.ui.screens.ResultsScreen
import com.example.ui.screens.ScannerSetupScreen
import com.example.ui.screens.TrashScreen
import com.example.ui.theme.DupSweepTheme
import com.example.viewmodel.DupSweepViewModel
import com.example.viewmodel.UiEvent

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      DupSweepTheme {
        DupSweepApp()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DupSweepApp(viewModel: DupSweepViewModel = viewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStorageState()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
    onResult = {
      viewModel.refreshStorageState()
    },
  )

  val allFilesSettingsLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
    onResult = {
      viewModel.refreshStorageState()
    },
  )

  val requestAllFilesAccess: () -> Unit = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val packageUri = Uri.parse("package:${context.packageName}")
      val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
      try {
        allFilesSettingsLauncher.launch(appIntent)
      } catch (_: Exception) {
        try {
          allFilesSettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        } catch (_: Exception) {
          allFilesSettingsLauncher.launch(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
          )
        }
      }
    } else {
      legacyStoragePermissionLauncher.launch(
        arrayOf(
          Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
      )
    }
  }

  val safPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
    onResult = { uri ->
      if (uri != null) {
        viewModel.onSafFolderSelected(uri)
      }
    },
  )

  val systemTrashLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartIntentSenderForResult(),
    onResult = { result ->
      viewModel.onSystemTrashResult(result.resultCode == Activity.RESULT_OK)
    },
  )

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: AppDestination.SCAN.route

  val navigateToTab: (AppDestination) -> Unit = { dest ->
    viewModel.onDestinationSelected(dest)
    navController.navigate(dest.route) {
      popUpTo(AppDestination.SCAN.route) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        is UiEvent.ShowMessage -> {
          snackbarHostState.showSnackbar(event.message)
        }
        is UiEvent.LaunchSystemTrash -> {
          val req = IntentSenderRequest.Builder(event.intentSender).build()
          systemTrashLauncher.launch(req)
        }
        is UiEvent.NavigateTo -> {
          navigateToTab(event.destination)
        }
      }
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    val destinations = listOf(
      AppDestination.SCAN,
      AppDestination.RESULTS,
      AppDestination.TRASH,
    )

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        CenterAlignedTopAppBar(
          title = {
            val titleText = when (currentRoute) {
              AppDestination.RESULTS.route -> "Duplicate Matches"
              AppDestination.TRASH.route -> stringResource(R.string.trash_title)
              else -> stringResource(R.string.app_name)
            }
            Text(
              text = titleText,
              style = MaterialTheme.typography.titleLarge,
            )
          },
          colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
          ),
        )
      },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar {
            destinations.forEach { dest ->
              val selected = currentRoute == dest.route
              val badgeCount = when (dest) {
                AppDestination.RESULTS -> uiState.duplicateGroups.size
                AppDestination.TRASH -> uiState.trashedItems.size
                AppDestination.SCAN -> 0
              }
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(dest) },
                icon = {
                  NavIconWithBadge(
                    destination = dest,
                    selected = selected,
                    badgeCount = badgeCount,
                  )
                },
                label = { Text(destinationLabel(dest)) },
                modifier = Modifier.testTag("nav_tab_${dest.route}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useNavRail) {
          NavigationRail {
            destinations.forEach { dest ->
              val selected = currentRoute == dest.route
              val badgeCount = when (dest) {
                AppDestination.RESULTS -> uiState.duplicateGroups.size
                AppDestination.TRASH -> uiState.trashedItems.size
                AppDestination.SCAN -> 0
              }
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(dest) },
                icon = {
                  NavIconWithBadge(
                    destination = dest,
                    selected = selected,
                    badgeCount = badgeCount,
                  )
                },
                label = { Text(destinationLabel(dest)) },
                modifier = Modifier.testTag("nav_rail_${dest.route}"),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          NavHost(
            navController = navController,
            startDestination = AppDestination.SCAN.route,
          ) {
            composable(AppDestination.SCAN.route) {
              ScannerSetupScreen(
                uiState = uiState,
                onRequestAllFilesAccess = requestAllFilesAccess,
                onOpenFolderBrowser = viewModel::openFolderBrowser,
                onSelectWholeDevice = viewModel::selectWholeDevice,
                onIncludeSubfoldersChanged = viewModel::onIncludeSubfoldersChanged,
                onMinSizeChanged = viewModel::onMinSizeChanged,
                onFileTypeChanged = viewModel::onFileTypeChanged,
                onStartScan = viewModel::startScan,
                onCancelScan = viewModel::cancelScan,
              )
            }

            composable(AppDestination.RESULTS.route) {
              BackHandler {
                navigateToTab(AppDestination.SCAN)
              }
              ResultsScreen(
                uiState = uiState,
                onToggleFileSelection = viewModel::toggleFileSelection,
                onSelectAllDuplicates = viewModel::selectAllDuplicates,
                onClearSelection = viewModel::clearSelection,
                onPromoteToOriginal = viewModel::promoteToOriginal,
                onSortOrderChanged = viewModel::onSortOrderChanged,
                onOpenComparison = viewModel::openComparisonPreview,
                onCloseComparison = viewModel::closeComparisonPreview,
                onRequestMoveToTrash = viewModel::requestMoveToTrash,
                onDismissMoveToTrashConfirm = viewModel::dismissMoveToTrashConfirm,
                onConfirmMoveToTrash = viewModel::confirmMoveToTrash,
                onNavigateToScan = { navigateToTab(AppDestination.SCAN) },
              )
            }

            composable(AppDestination.TRASH.route) {
              BackHandler {
                navigateToTab(AppDestination.SCAN)
              }
              TrashScreen(
                uiState = uiState,
                onRestoreItem = viewModel::restoreTrashItem,
                onDeletePermanently = viewModel::deleteTrashItemPermanently,
                onRequestEmptyTrash = viewModel::requestEmptyTrash,
                onDismissEmptyTrash = viewModel::dismissEmptyTrashConfirm,
                onConfirmEmptyTrash = viewModel::confirmEmptyTrash,
              )
            }
          }
        }
      }
    }
  }

  if (uiState.showFolderBrowserDialog) {
    FolderPickerDialog(
      mountedVolumes = uiState.mountedVolumes,
      initialTarget = uiState.scanTarget,
      onSelectWholeDevice = viewModel::selectWholeDevice,
      onSelectFolder = viewModel::selectFileSystemFolder,
      onUseSystemSafPicker = {
        viewModel.closeFolderBrowser()
        val initialUri = (uiState.scanTarget as? ScanTarget.SafTree)?.treeUri
        safPickerLauncher.launch(initialUri)
      },
      onDismiss = viewModel::closeFolderBrowser,
    )
  }
}

@Composable
private fun destinationLabel(destination: AppDestination): String = when (destination) {
  AppDestination.SCAN -> stringResource(R.string.nav_scan)
  AppDestination.RESULTS -> stringResource(R.string.nav_duplicates)
  AppDestination.TRASH -> stringResource(R.string.nav_trash)
}

@Composable
private fun NavIconWithBadge(
  destination: AppDestination,
  selected: Boolean,
  badgeCount: Int,
) {
  val icon = when (destination) {
    AppDestination.SCAN -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
    AppDestination.RESULTS -> if (selected) Icons.Filled.ContentCopy else Icons.Outlined.ContentCopy
    AppDestination.TRASH -> if (selected) Icons.Filled.DeleteOutline else Icons.Outlined.DeleteOutline
  }

  if (badgeCount > 0) {
    BadgedBox(
      badge = {
        Badge {
          Text(if (badgeCount > 99) "99+" else badgeCount.toString())
        }
      },
    ) {
      Icon(imageVector = icon, contentDescription = destinationLabel(destination))
    }
  } else {
    Icon(imageVector = icon, contentDescription = destinationLabel(destination))
  }
}
