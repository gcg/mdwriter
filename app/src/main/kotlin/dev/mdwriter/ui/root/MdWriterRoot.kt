package dev.mdwriter.ui.root

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import dev.mdwriter.AppContainer
import dev.mdwriter.R
import dev.mdwriter.data.settings.Settings
import dev.mdwriter.editor.EditorController
import dev.mdwriter.editor.spans.EditorStyle
import dev.mdwriter.ui.editor.EditorScreen
import dev.mdwriter.ui.editor.EditorViewModel
import dev.mdwriter.ui.gesture.SwipeDir
import dev.mdwriter.ui.gesture.SwipeDir.TowardEnd
import dev.mdwriter.ui.gesture.SwipeDir.TowardStart
import dev.mdwriter.ui.library.DeleteUndoSnackbarHost
import dev.mdwriter.ui.library.LibraryDrawer
import dev.mdwriter.ui.library.LibraryEvent
import dev.mdwriter.ui.library.LibraryPane
import dev.mdwriter.ui.library.LibraryUiState
import dev.mdwriter.ui.library.LibraryViewModel
import dev.mdwriter.ui.theme.MdWriterTheme
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterTheme
import dev.mdwriter.ui.theme.hairline
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Root composable (T11 + T12 + T13, 01 §6.4): the app theme driven live from
 * [dev.mdwriter.data.settings.SettingsRepository], one [EditorViewModel] session for its whole lifetime, the
 * library drawer/pane, swipe navigation, the fading chrome, and back-priority ordering. T16/T17/T19 add the
 * remaining overlays (preview, find, settings); this task only leaves their extension points (see
 * `plans/tasks/T13-swipe-and-chrome.md` Scope/Out).
 *
 * On windows narrower than 840 dp the library is a `ModalNavigationDrawer`; at 840 dp and above it is a permanent
 * [LibraryPane] toggled by the same glyph/swipe. The editor subtree is wrapped in `movableContentOf` (§G) so
 * switching between the two layouts never re-attaches [EditorController.scrollView] to a second parent (it has
 * exactly one — `IllegalStateException` otherwise).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MdWriterRoot(
    container: AppContainer,
    commands: SharedFlow<AppCommand>,
    modifier: Modifier = Modifier,
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    MdWriterTheme(themeMode = settings.themeMode, pureBlack = settings.pureBlack, font = settings.typeface) {
        val editorVm: EditorViewModel = viewModel(factory = EditorViewModel.Factory)
        val libraryVm: LibraryViewModel =
            viewModel {
                LibraryViewModel(
                    library = container.library,
                    settings = container.settings,
                    positions = container.positions,
                    session = editorVm,
                    appScope = container.applicationScope,
                    io = container.dispatchers.io,
                )
            }

        val context = LocalContext.current
        val colors = WriterTheme.colors
        val controller = remember { EditorController(context, EditorStyle.create(context, colors)) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val libraryHostState = remember { SnackbarHostState() }
        val imeVisible = WindowInsets.isImeVisible
        var restoreIme by remember { mutableStateOf(false) }
        val haptics = LocalHapticFeedback.current

        // ---- adaptive layout (§G) --------------------------------------------------------------------------------
        val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
        val expanded = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
        var paneVisible by rememberSaveable { mutableStateOf(true) }

        // ---- swipe navigation + preview stub (§G, out of scope: T16 replaces both) -------------------------------
        val onOpenPreview: () -> Unit = {}
        val previewAvailable = false

        fun openLibrary() {
            controller.collapseSelection()
            // T16/T17 close the preview/find here first, if they are open; both are always closed in T13.
            if (expanded) paneVisible = true else scope.launch { drawerState.open() }
        }

        fun toggleLibrary() {
            if (expanded) paneVisible = !paneVisible else openLibrary()
        }

        fun swipeAccepts(dir: SwipeDir): Boolean =
            when (dir) {
                TowardEnd -> !(expanded && paneVisible)
                TowardStart -> (expanded && paneVisible) || previewAvailable
            }

        fun routeSwipe(dir: SwipeDir) {
            when (dir) {
                TowardEnd -> openLibrary()
                TowardStart -> if (expanded && paneVisible) paneVisible = false else onOpenPreview()
            }
        }

        fun swipeEnabled(): Boolean {
            val sel = controller.selection.value
            val ui = editorVm.uiState.value
            return settings.swipeNavigation && sel.start == sel.end &&
                !ui.drawerOpen && !ui.previewOpen && !ui.findOpen && !ui.settingsOpen
        }

        val armedSelection = remember { IntArray(2) }

        LaunchedEffect(editorVm) { editorVm.start() }

        LaunchedEffect(commands) {
            commands.collect { cmd ->
                when (cmd) {
                    AppCommand.NewNote -> libraryVm.newNote()
                    AppCommand.ToggleLibrary -> toggleLibrary()
                }
            }
        }

        LaunchedEffect(expanded) { if (expanded) drawerState.snapTo(DrawerValue.Closed) }

        LaunchedEffect(drawerState) {
            snapshotFlow { drawerState.targetValue }
                .drop(1)
                .distinctUntilChanged()
                .collect { target ->
                    val open = target == DrawerValue.Open
                    editorVm.setDrawerOpen(open)
                    if (open) {
                        restoreIme = imeVisible && controller.hasFocus()
                        controller.collapseSelection()
                        controller.hideIme()
                        editorVm.onDrawerOpened()
                        libraryVm.onDrawerOpened()
                    } else {
                        libraryVm.onDrawerClosed()
                        if (restoreIme) {
                            controller.requestFocus()
                            controller.showIme()
                        }
                        restoreIme = false
                    }
                }
        }
        // The ONLY collector of libraryVm.events (a Channel-backed flow: only one effective subscriber — a second
        // independent collector, e.g. inside DeleteUndoSnackbarHost, would race this one and silently drop events;
        // found via on-device testing, see DeleteUndoSnackbarHost's own doc comment).
        LaunchedEffect(libraryVm) {
            libraryVm.events.collect { event ->
                when (event) {
                    LibraryEvent.CloseDrawer -> {
                        restoreIme = false
                        if (!expanded) drawerState.close()
                    }

                    is LibraryEvent.Message -> {
                        scope.launch { libraryHostState.showSnackbar(event.text) }
                    }
                }
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { libraryVm.onStop() }
        // T14: revalidate every linked folder's grant + reachability on every ON_START (a lost grant shows the
        // location as Disconnected — never auto-unlinked, platform §2.13), then force a re-list.
        LifecycleEventEffect(Lifecycle.Event.ON_START) {
            scope.launch {
                container.library.revalidate()
                container.library.invalidate()
            }
        }

        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val drawerWidth = minOf(WriterDimens.drawerMaxWidth.value, screenWidthDp - WriterDimens.drawerEdgeGap.value).dp
        val libraryIcon = if (expanded && paneVisible) R.drawable.ic_left_panel_close else R.drawable.ic_left_panel_open

        val editor =
            remember {
                movableContentOf {
                    EditorScreen(
                        vm = editorVm,
                        controller = controller,
                        onMessage = { text -> scope.launch { snackbarHostState.showSnackbar(text) } },
                        onOpenLibrary = ::toggleLibrary,
                        libraryIcon = libraryIcon,
                        onNewNote = libraryVm::newNote,
                        swipeEnabled = ::swipeEnabled,
                        swipeAccepts = ::swipeAccepts,
                        onSwipeArmedDown = {
                            armedSelection[0] = controller.editText.selectionStart
                            armedSelection[1] = controller.editText.selectionEnd
                        },
                        onSwipe = { dir ->
                            controller.editText.setSelection(armedSelection[0], armedSelection[1])
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            routeSwipe(dir)
                        },
                    )
                }
            }

        Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (expanded) {
                    Row(Modifier.fillMaxSize()) {
                        if (paneVisible) {
                            LibraryPane(libraryVm, Modifier.width(WriterDimens.permanentPaneWidth).fillMaxHeight())
                            VerticalDivider(thickness = hairline(), color = colors.divider)
                        }
                        Box(Modifier.weight(1f)) { editor() }
                    }
                } else {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open,
                        scrimColor = colors.scrim,
                        drawerContent = {
                            ModalDrawerSheet(
                                drawerState = drawerState,
                                modifier = Modifier.width(drawerWidth),
                                drawerShape = RectangleShape,
                                drawerContainerColor = colors.surface,
                                drawerContentColor = colors.text,
                                drawerTonalElevation = 0.dp,
                            ) {
                                LibraryDrawer(libraryVm)
                                // Search-clear / folder-up back handlers (01 §6.4 / §H) — composed AFTER
                                // LibraryDrawer's own children (i.e. after ModalDrawerSheet's own predictive-back
                                // handler), enabled only while the modal drawer is open (never in pane mode).
                                val lib by libraryVm.uiState.collectAsStateWithLifecycle()
                                val libContent = lib as? LibraryUiState.Content
                                BackHandler(
                                    enabled = drawerState.isOpen && libContent?.atRoot == false,
                                ) { libraryVm.navigateUp() }
                                BackHandler(
                                    enabled = drawerState.isOpen && libContent?.searching == true,
                                ) { libraryVm.clearSearch() }
                            }
                        },
                    ) {
                        editor()
                    }
                }
                DeleteUndoSnackbarHost(
                    libraryVm,
                    libraryHostState,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
                )
            }
        }

        // Back priority (highest first): IME (system) > selection > find > drawer (search clear > folder up >
        // close) > preview (T16) > system back-to-home. activity-compose 1.13: LAST COMPOSED WINS, so compose the
        // lowest first, always composed, gated only by `enabled` (an enabled root handler disables the
        // back-to-home animation). Drawer, preview and find are mutually exclusive (openLibrary/openPreview close
        // the others), and opening the drawer or preview collapses the selection, so only selection+find can be
        // enabled together -> selection last.
        val ui by editorVm.uiState.collectAsStateWithLifecycle()
        val selection by controller.selection.collectAsStateWithLifecycle()
        BackHandler(enabled = ui.findOpen) { editorVm.closeFind() }
        BackHandler(enabled = selection.start != selection.end) { controller.collapseSelection() }
    }
}
