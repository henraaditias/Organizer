package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.FolderPickerTarget
import com.example.ui.HistoryFilter
import com.example.ui.MainViewModel
import com.example.ui.components.bentoDotGrid
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DryRunPreviewDialog
import com.example.ui.screens.FolderPickerDialog
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LiveTransferProgressDialog
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SubfolderExclusionsDialog
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CanvasBg
import com.example.ui.theme.LabelMonoCapsStyle
import com.example.ui.theme.OrganizerTheme
import com.example.ui.theme.StatusError
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceRaised
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data object DashboardRoute

@Serializable
data object HistoryRoute

@Serializable
data object ScheduleRoute

private data class NavDestinationItem(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            OrganizerTheme {
                val app = applicationContext as OrganizerApplication
                val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(app))
                OrganizerAppShell(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun OrganizerAppShell(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val dashboardState by viewModel.dashboardUiState.collectAsStateWithLifecycle()
    val dryRunState by viewModel.dryRunState.collectAsStateWithLifecycle()
    val exclusionsState by viewModel.exclusionsState.collectAsStateWithLifecycle()
    val watchedRun by viewModel.watchedRun.collectAsStateWithLifecycle()
    val liveRunOperations by viewModel.liveRunOperations.collectAsStateWithLifecycle()
    val showLiveProgressDialog by viewModel.showLiveProgressDialog.collectAsStateWithLifecycle()
    val allOperations by viewModel.allOperations.collectAsStateWithLifecycle()
    val failedOperations by viewModel.failedOperations.collectAsStateWithLifecycle()
    val historyFilter by viewModel.historyFilter.collectAsStateWithLifecycle()
    val scheduleConfig by viewModel.scheduleConfig.collectAsStateWithLifecycle()
    val folderPickerState by viewModel.folderPickerState.collectAsStateWithLifecycle()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: android.content.Context, input: Uri?): Intent {
                return super.createIntent(context, input).apply {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    )
                }
            }
        }
    ) { uri ->
        if (uri != null) {
            viewModel.onFolderSelected(uri)
        }
    }

    val destinationPickerLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: android.content.Context, input: Uri?): Intent {
                return super.createIntent(context, input).apply {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    )
                }
            }
        }
    ) { uri ->
        if (uri != null) {
            viewModel.onDestinationFolderSelected(uri)
        }
    }

    val exportReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.exportFailedReportToUri(uri)
        }
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }

    fun requestOptionalNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            hasNotificationPermission = granted
            if (!granted) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissionsAndCheckRecovery()
    }

    val legacyStoragePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissionsAndCheckRecovery()
    }

    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            runCatching {
                allFilesSettingsLauncher.launch(appIntent)
            }.onFailure {
                runCatching {
                    allFilesSettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            legacyStoragePermLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionsAndCheckRecovery()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.uiMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navItems = remember {
        listOf(
            NavDestinationItem(
                route = DashboardRoute,
                labelRes = R.string.nav_dashboard,
                selectedIcon = Icons.Filled.Dashboard,
                unselectedIcon = Icons.Outlined.Dashboard,
                testTag = "nav_dashboard"
            ),
            NavDestinationItem(
                route = HistoryRoute,
                labelRes = R.string.nav_history,
                selectedIcon = Icons.Filled.History,
                unselectedIcon = Icons.Outlined.History,
                testTag = "nav_history"
            ),
            NavDestinationItem(
                route = ScheduleRoute,
                labelRes = R.string.nav_schedule,
                selectedIcon = Icons.Filled.Schedule,
                unselectedIcon = Icons.Outlined.Schedule,
                testTag = "nav_schedule"
            )
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val failedCount = failedOperations.size

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg)
            .bentoDotGrid()
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    Box {
                        HorizontalDivider(color = SurfaceBorder)
                        NavigationBar(
                            containerColor = SurfaceCard,
                            contentColor = TextPrimary
                        ) {
                            navItems.forEach { item ->
                                val selected = currentDestination?.hasRoute(item.route::class) == true
                                val label = stringResource(item.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(item.route) },
                                    icon = {
                                        if (item.route == HistoryRoute && failedCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(containerColor = StatusError) {
                                                        Text(
                                                            text = failedCount.toString(),
                                                            color = CanvasBg
                                                        )
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = label
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = label
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = label.uppercase(),
                                            style = LabelMonoCapsStyle
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentCyan,
                                        selectedTextColor = AccentCyan,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = SurfaceRaised
                                    ),
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = SurfaceCard,
                        contentColor = TextPrimary,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            val label = stringResource(item.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    if (item.route == HistoryRoute && failedCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = StatusError) {
                                                    Text(failedCount.toString(), color = CanvasBg)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = label
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = label
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = label.uppercase(),
                                        style = LabelMonoCapsStyle
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = AccentCyan,
                                    selectedTextColor = AccentCyan,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = SurfaceRaised
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                    VerticalDivider(color = SurfaceBorder)
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = DashboardRoute,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1040.dp)
                    ) {
                        composable<DashboardRoute> {
                            DashboardScreen(
                                uiState = dashboardState,
                                onAddFolderClick = { viewModel.openFolderPicker(FolderPickerTarget.SOURCE) },
                                onChooseDestinationClick = { viewModel.openFolderPicker(FolderPickerTarget.DESTINATION) },
                                onResetDestinationClick = { viewModel.resetDestinationToDefault() },
                                onOrganizeAllClick = { viewModel.startDryRunPreview(null) },
                                onOrganizeFolderClick = { folder -> viewModel.startDryRunPreview(folder) },
                                onManageExclusionsClick = { folder -> viewModel.openExclusionsSheet(folder) },
                                onRemoveFolderClick = { folder -> viewModel.removeFolder(folder.treeUri) },
                                onRegrantFolderClick = { viewModel.openFolderPicker(FolderPickerTarget.SOURCE) },
                                onResumeInterruptedClick = {
                                    viewModel.resumeInterruptedRun()
                                },
                                onCancelActiveWorkClick = { viewModel.cancelActiveWork() },
                                onOpenLiveProgressClick = { viewModel.openLiveProgressModal() },
                                onOpenFailedReportClick = {
                                    viewModel.setHistoryFilter(HistoryFilter.FAILED)
                                    navigateToTab(HistoryRoute)
                                }
                            )
                        }

                        composable<HistoryRoute> {
                            val filtered = remember(allOperations, historyFilter) {
                                viewModel.filterOperations(allOperations, historyFilter)
                            }
                            HistoryScreen(
                                recentRuns = dashboardState.recentRuns,
                                filteredOperations = filtered,
                                failedOperationsCount = failedCount,
                                activeFilter = historyFilter,
                                isWorkerRunning = dashboardState.isWorkerRunning,
                                onFilterChange = { viewModel.setHistoryFilter(it) },
                                onRetryAllFailed = {
                                    viewModel.retryAllFailed()
                                },
                                onRetrySingle = { opId ->
                                    viewModel.retrySingleOperation(opId)
                                },
                                onExportFailedReport = {
                                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                    exportReportLauncher.launch("organizer_failed_report_$stamp.txt")
                                }
                            )
                        }

                        composable<ScheduleRoute> {
                            ScheduleScreen(
                                config = scheduleConfig,
                                isWorkerRunning = dashboardState.isWorkerRunning,
                                hasFolders = dashboardState.folders.isNotEmpty(),
                                onUpdateConfig = { cfg ->
                                    if (cfg.enabled) requestOptionalNotificationPermission()
                                    viewModel.updateScheduleConfig(cfg)
                                },
                                onRunAutoOrganizeNow = {
                                    viewModel.runAutoOrganizeNow()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    DryRunPreviewDialog(
        state = dryRunState,
        onDismiss = { viewModel.dismissDryRunPreview() },
        onConfirm = {
            viewModel.confirmAndRunFromDryRun()
        },
        onChangeDestination = { viewModel.openFolderPicker(FolderPickerTarget.DESTINATION) }
    )

    FolderPickerDialog(
        state = folderPickerState,
        onRequestAllFilesAccess = { requestAllFilesAccess() },
        onSelectFolderUri = { uriStr -> viewModel.selectLocalFolderUri(uriStr) },
        onNavigateToPath = { path -> viewModel.navigateFolderPickerTo(path) },
        onNavigateUp = { viewModel.navigateFolderPickerUp() },
        onOpenSystemSafPicker = {
            val target = folderPickerState.target
            viewModel.closeFolderPicker()
            if (target == FolderPickerTarget.DESTINATION) {
                destinationPickerLauncher.launch(null)
            } else {
                folderPickerLauncher.launch(null)
            }
        },
        onDismiss = { viewModel.closeFolderPicker() }
    )

    LiveTransferProgressDialog(
        visible = showLiveProgressDialog,
        run = watchedRun,
        operations = liveRunOperations,
        isWorkerRunning = dashboardState.isWorkerRunning,
        hasNotificationPermission = hasNotificationPermission,
        onRequestNotifications = { requestOptionalNotificationPermission() },
        onCancelRun = { viewModel.cancelActiveWork() },
        onDismiss = { viewModel.dismissLiveProgressModal() }
    )

    SubfolderExclusionsDialog(
        state = exclusionsState,
        isBusy = dashboardState.isWorkerRunning,
        onToggleNode = { node -> viewModel.toggleSubfolderExclusion(node) },
        onRefresh = { viewModel.refreshExclusionsSheet() },
        onOrganizeFolder = { viewModel.organizeFromExclusions() },
        onDismiss = { viewModel.closeExclusionsSheet() }
    )
}
