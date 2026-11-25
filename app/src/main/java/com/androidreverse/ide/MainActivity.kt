package com.androidreverse.ide

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.androidreverse.ide.core.ai.configureAppCheck
import com.androidreverse.ide.core.binary.InspectedContent
import com.androidreverse.ide.ui.MainViewModel
import com.androidreverse.ide.ui.components.AppIcons
import com.androidreverse.ide.ui.screens.CallGraphScreen
import com.androidreverse.ide.ui.screens.CfgScreen
import com.androidreverse.ide.ui.screens.GeminiAskScreen
import com.androidreverse.ide.ui.screens.InspectorScreen
import com.androidreverse.ide.ui.screens.InstalledAppsScreen
import com.androidreverse.ide.ui.screens.NotesScreen
import com.androidreverse.ide.ui.screens.SettingsScreen
import com.androidreverse.ide.ui.screens.ToolsScreen
import com.androidreverse.ide.ui.screens.WorkspaceScreen
import com.androidreverse.ide.ui.theme.AndroidReverseTheme
import kotlinx.serialization.Serializable

@Serializable data object RouteWorkspace
@Serializable data object RouteTools
@Serializable data object RouteNotes
@Serializable data object RouteSettings
@Serializable data object RouteInstalledApps
@Serializable data object RouteAskGemini
@Serializable data object RouteInspector
@Serializable data object RouteCfg
@Serializable data object RouteCallGraph

private data class TopNavItem(
    val route: Any,
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String
)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isDark = uiState.settings.isDarkTheme
            val windowSizeClass = calculateWindowSizeClass(this)

            LaunchedEffect(isDark) {
                val style = if (isDark) {
                    SystemBarStyle.dark(Color.Transparent.toArgb())
                } else {
                    SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            AndroidReverseTheme(darkTheme = isDark) {
                ReverseIdeApp(
                    viewModel = viewModel,
                    useRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
                )
            }
        }
    }
}

@Composable
private fun ReverseIdeApp(
    viewModel: MainViewModel,
    useRail: Boolean
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val context = LocalContext.current
    val openApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openFromUri(uri)
        }
    }

    val exportReportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.exportAiReportToUri(uri)
        }
    }

    val exportApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        if (uri != null) {
            viewModel.exportWorkspace(uri)
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val topDestinations = remember {
        listOf(
            TopNavItem(RouteWorkspace, R.string.nav_workspace, AppIcons.Workspace, "nav_workspace"),
            TopNavItem(RouteTools, R.string.nav_tools, AppIcons.Tools, "nav_tools"),
            TopNavItem(RouteNotes, R.string.nav_notes, AppIcons.Notes, "nav_notes"),
            TopNavItem(RouteSettings, R.string.nav_settings, AppIcons.Settings, "nav_settings")
        )
    }

    val showChrome = destination == null ||
        destination.hasRoute<RouteWorkspace>() ||
        destination.hasRoute<RouteTools>() ||
        destination.hasRoute<RouteNotes>() ||
        destination.hasRoute<RouteSettings>()

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isCurrentBookmarked = remember(uiState.inspectedContent, uiState.bookmarks) {
        val id = when (val c = uiState.inspectedContent) {
            is InspectedContent.DexClass -> "dex:${c.detail.summary.dexName}:${c.detail.summary.descriptor}"
            is InspectedContent.XmlFile -> "file:${c.path}"
            is InspectedContent.TextFile -> "file:${c.path}"
            is InspectedContent.ElfBinary -> "file:${c.path}"
            is InspectedContent.RawBinary -> "file:${c.path}"
            null -> null
        }
        id != null && uiState.bookmarks.any { it.id == id }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showChrome && !useRail) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    topDestinations.forEach { item ->
                        val selected = when (item.route) {
                            RouteWorkspace -> destination?.hasRoute<RouteWorkspace>() == true
                            RouteTools -> destination?.hasRoute<RouteTools>() == true
                            RouteNotes -> destination?.hasRoute<RouteNotes>() == true
                            RouteSettings -> destination?.hasRoute<RouteSettings>() == true
                            else -> false
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = stringResource(item.labelRes)
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) },
                            modifier = Modifier.testTag(item.tag)
                        )
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
            if (showChrome && useRail) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    topDestinations.forEach { item ->
                        val selected = when (item.route) {
                            RouteWorkspace -> destination?.hasRoute<RouteWorkspace>() == true
                            RouteTools -> destination?.hasRoute<RouteTools>() == true
                            RouteNotes -> destination?.hasRoute<RouteNotes>() == true
                            RouteSettings -> destination?.hasRoute<RouteSettings>() == true
                            else -> false
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = stringResource(item.labelRes)
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) },
                            modifier = Modifier.testTag(item.tag)
                        )
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = RouteWorkspace,
                modifier = Modifier.weight(1f)
            ) {
                composable<RouteWorkspace> {
                    WorkspaceScreen(
                        uiState = uiState,
                        onOpenInstalledApps = { navController.navigate(RouteInstalledApps) },
                        onPickApk = {
                            openApkLauncher.launch(
                                arrayOf(
                                    "application/vnd.android.package-archive",
                                    "application/zip",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        onAskGemini = { navController.navigate(RouteAskGemini) },
                        onSaveReportTxt = {
                            val safeBase = (uiState.workspace?.displayName ?: "app")
                                .lowercase()
                                .replace(Regex("[^a-z0-9]+"), "_")
                                .trim('_')
                                .ifEmpty { "android_app" }
                            exportReportTxtLauncher.launch("${safeBase}_blueprint.txt")
                        },
                        onShareReportTxt = {
                            viewModel.prepareShareableReport { file ->
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, file.name)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, file.name))
                            }
                        },
                        onExportApk = {
                            val name = uiState.workspace?.displayName ?: "rebuilt.apk"
                            exportApkLauncher.launch("rebuilt_$name")
                        },
                        onCloseWorkspace = { viewModel.closeWorkspace() },
                        onFilterChange = { viewModel.updateTreeFilter(it) },
                        onToggleFolder = { viewModel.toggleFolder(it) },
                        onOpenFile = { path ->
                            viewModel.inspectFile(path) {
                                navController.navigate(RouteInspector)
                            }
                        },
                        onOpenDexClass = { summary ->
                            viewModel.inspectDexClass(summary) {
                                navController.navigate(RouteInspector)
                            }
                        },
                        onSearch = { viewModel.runSearch(it) },
                        onSearchHitClick = { hit ->
                            if (hit.isDexClass) {
                                viewModel.inspectDexByDescriptor(hit.dexName, hit.classDescriptor) {
                                    navController.navigate(RouteInspector)
                                }
                            } else {
                                viewModel.inspectFile(hit.filePath) {
                                    navController.navigate(RouteInspector)
                                }
                            }
                        }
                    )
                }

                composable<RouteInstalledApps> {
                    InstalledAppsScreen(
                        apps = uiState.installedApps,
                        isLoading = uiState.isLoadingInstalledApps,
                        onLoadApps = { force -> viewModel.loadInstalledApps(force) },
                        onSelectApp = { app ->
                            navController.popBackStack()
                            viewModel.openInstalledApp(app)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<RouteAskGemini> {
                    GeminiAskScreen(
                        workspace = uiState.workspace,
                        inspectedContent = uiState.inspectedContent,
                        messages = uiState.geminiMessages,
                        isStreaming = uiState.isGeminiStreaming,
                        errorMessage = uiState.geminiError,
                        onAsk = { viewModel.askGemini(it) },
                        onClearChat = { viewModel.clearGeminiChat() },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<RouteTools> {
                    ToolsScreen(
                        certificates = uiState.workspace?.certificates.orEmpty()
                    )
                }

                composable<RouteNotes> {
                    NotesScreen(
                        notes = uiState.notes,
                        bookmarks = uiState.bookmarks,
                        hasWorkspace = uiState.workspace != null,
                        onSaveNote = { id, title, body -> viewModel.addOrUpdateNote(id, title, body) },
                        onDeleteNote = { viewModel.deleteNote(it) },
                        onOpenBookmark = { bm ->
                            if (bm.isDexClass) {
                                viewModel.inspectDexByDescriptor(bm.dexName, bm.classDescriptor) {
                                    navController.navigate(RouteInspector)
                                }
                            } else {
                                viewModel.inspectFile(bm.targetPath) {
                                    navController.navigate(RouteInspector)
                                }
                            }
                        },
                        onRemoveBookmark = { viewModel.removeBookmark(it) }
                    )
                }

                composable<RouteSettings> {
                    SettingsScreen(
                        settings = uiState.settings,
                        onUpdateSettings = { viewModel.updateSettings(it) }
                    )
                }

                composable<RouteInspector> {
                    InspectorScreen(
                        content = uiState.inspectedContent,
                        isLoading = uiState.isInspecting,
                        isBookmarked = isCurrentBookmarked,
                        settings = uiState.settings,
                        onBack = { navController.popBackStack() },
                        onToggleBookmark = { viewModel.toggleBookmarkForCurrent() },
                        onSaveEdit = { path, text -> viewModel.savePendingEdit(path, text) },
                        onOpenMethodCfg = { detail, method ->
                            viewModel.openMethodCfg(detail, method) {
                                navController.navigate(RouteCfg)
                            }
                        },
                        onOpenCallGraph = { detail ->
                            viewModel.openClassCallGraph(detail) {
                                navController.navigate(RouteCallGraph)
                            }
                        },
                        onAskGemini = { navController.navigate(RouteAskGemini) }
                    )
                }

                composable<RouteCfg> {
                    CfgScreen(
                        graph = uiState.activeCfg,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<RouteCallGraph> {
                    CallGraphScreen(
                        graph = uiState.activeCallGraph,
                        depth = uiState.callGraphDepth,
                        onDepthDelta = { viewModel.updateCallGraphDepth(it) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
