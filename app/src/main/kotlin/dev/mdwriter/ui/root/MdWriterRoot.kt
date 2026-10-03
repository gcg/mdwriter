package dev.mdwriter.ui.root

import android.util.TypedValue
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import dev.mdwriter.intents.RoutedIntent
import dev.mdwriter.ui.editor.EditorScreen
import dev.mdwriter.ui.editor.EditorViewModel
import dev.mdwriter.ui.editor.RootSheet
import dev.mdwriter.ui.gesture.SwipeDir
import dev.mdwriter.ui.gesture.SwipeDir.TowardEnd
import dev.mdwriter.ui.gesture.SwipeDir.TowardStart
import dev.mdwriter.ui.library.DeleteUndoSnackbarHost
import dev.mdwriter.ui.library.LibraryDrawer
import dev.mdwriter.ui.library.LibraryEvent
import dev.mdwriter.ui.library.LibraryPane
import dev.mdwriter.ui.library.LibraryUiState
import dev.mdwriter.ui.library.LibraryViewModel
import dev.mdwriter.ui.library.rememberExportAllNotes
import dev.mdwriter.ui.preview.PreviewGlyphRowHeight
import dev.mdwriter.ui.preview.PreviewOverlay
import dev.mdwriter.ui.preview.PreviewThemes
import dev.mdwriter.ui.preview.PreviewWebViewHolder
import dev.mdwriter.ui.settings.AboutSheet
import dev.mdwriter.ui.settings.SettingsSheet
import dev.mdwriter.ui.theme.EditorMetrics
import dev.mdwriter.ui.theme.MdWriterTheme
import dev.mdwriter.ui.theme.WidthClass
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterTheme
import dev.mdwriter.ui.theme.hairline
import dev.mdwriter.util.permitDiskIo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File

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
    launch: RoutedIntent,
    newIntents: Flow<RoutedIntent>,
    modifier: Modifier = Modifier,
) {
    val loadedState = container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val loaded = loadedState.value
    // Delegated State read: closures captured once (movableContentOf below, shortcut handlers) must always see the
    // CURRENT settings, never the ones of the composition that created them.
    val settings by remember { derivedStateOf { loadedState.value ?: Settings() } }
    MdWriterTheme(themeMode = settings.themeMode, pureBlack = settings.pureBlack, font = settings.typeface) {
        // Compose the editor only after the FIRST real settings emission: a default-then-real double emission
        // would reflow the document twice at startup (T19).
        if (loaded == null) {
            Box(Modifier.fillMaxSize().background(WriterTheme.colors.bg))
            return@MdWriterTheme
        }
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
                    shareOut = container.shareOut,
                    exporter = container.exporter,
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

        // ---- T16: the preview WebView (lazy, Activity context, never a ViewModel field — 01 §5) -----------------
        // filesDir resolution and the WebView provider's first init are framework-internal disk reads (T20).
        val libraryRoot = remember(context) { permitDiskIo { File(context.filesDir, "library") } }
        val previewHolder =
            remember(context, libraryRoot) { permitDiskIo { PreviewWebViewHolder(context, libraryRoot) } }
        DisposableEffect(Unit) { onDispose { previewHolder.destroy() } }

        // Same tokens the editor itself uses (WriterColors/WriterFont/EditorMetrics) — the preview matches it.
        // controller.style.widthClass is a plain var (not Compose state), same read pattern EditorScreen already
        // uses for EditorChrome; fontScale is the one dependency that needs an explicit key (Resources'
        // displayMetrics update in place on a density/font-scale config change, which Compose does not otherwise
        // observe — 01 §8 keeps this Activity alive across that change via configChanges).
        val fontScale = LocalConfiguration.current.fontScale
        val widthClass = controller.style.widthClass
        // The preview WebView draws edge to edge, but its close/share row sits below the status bar (56 dp tall,
        // PreviewOverlay): the page must start below both, or the title slides under the close glyph.
        val previewTopInsetDp =
            with(LocalDensity.current) {
                WindowInsets.safeDrawing
                    .getTop(this)
                    .toDp()
                    .value
            }
        val previewTheme =
            remember(
                colors,
                settings.typeface,
                settings.textSizeStep,
                settings.lineLength,
                widthClass,
                fontScale,
                previewTopInsetDp,
            ) {
                val dm = context.resources.displayMetrics
                val bodySizeSp = EditorMetrics.bodyTextSizeSp(settings.textSizeStep, widthClass)
                val bodyCssPx =
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, bodySizeSp.toFloat(), dm) / dm.density
                PreviewThemes.build(
                    colors = colors,
                    pureBlack = settings.pureBlack,
                    font = settings.typeface,
                    bodyCssPx = bodyCssPx,
                    measureChars = if (widthClass == WidthClass.Compact) null else settings.lineLength,
                    sideDp = EditorMetrics.sideMarginMin(widthClass).value,
                    topDp = previewTopInsetDp + PreviewGlyphRowHeight.value + EditorMetrics.topRoom(widthClass).value,
                    density = dm.density,
                )
            }
        // A theme change (settings, rotation, font scale) while the preview is open re-renders it in place; while
        // closed, the next openPreview() call already picks up the fresh previewTheme.
        LaunchedEffect(previewTheme) {
            if (editorVm.uiState.value.previewOpen) {
                editorVm.openPreview(
                    controller.snapshot(),
                    controller.caret(),
                    previewTheme,
                    editorVm.uiState.value.title,
                )
            }
        }

        // openPreview()/closePreview() are called through several layers that are only ever composed ONCE per
        // MdWriterRoot lifetime (the commands LaunchedEffect keyed on the stable `commands` flow; the
        // movableContentOf-wrapped `editor` below) — a plain `previewTheme` read inside their bodies would forever
        // see the FIRST composition's theme (e.g. never pick up a later dark-mode toggle). rememberUpdatedState
        // keeps the read live regardless of which composition's closure performs it (same reason EditorSurface's
        // pointerInput lambdas use it, T13).
        val currentPreviewTheme by rememberUpdatedState(previewTheme)

        val previewAvailable = true

        fun closePreview() {
            editorVm.closePreview()
        }

        // T17: the find bar's own `onClose` (its own × button, Esc, or the root BackHandler below) — never a
        // second, differently-shaped find handler (task step 6).
        fun closeFindBar() {
            controller.clearFind(selectFocused = true)
            controller.requestFocus()
            editorVm.closeFind()
        }

        fun openPreview() {
            controller.collapseSelection()
            controller.hideIme()
            if (!expanded) scope.launch { drawerState.close() }
            if (editorVm.uiState.value.findOpen) closeFindBar()
            editorVm.openPreview(
                controller.snapshot(),
                controller.caret(),
                currentPreviewTheme,
                editorVm.uiState.value.title,
            )
        }

        fun openLibrary() {
            controller.collapseSelection()
            if (editorVm.uiState.value.previewOpen) closePreview()
            if (editorVm.uiState.value.findOpen) closeFindBar()
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
                TowardStart -> if (expanded && paneVisible) paneVisible = false else openPreview()
            }
        }

        fun swipeEnabled(): Boolean {
            val sel = controller.selection.value
            val ui = editorVm.uiState.value
            return settings.swipeNavigation && sel.start == sel.end &&
                !ui.drawerOpen && !ui.previewOpen && !ui.findOpen && !ui.settingsOpen
        }

        val armedSelection = remember { IntArray(2) }

        LaunchedEffect(editorVm) { editorVm.startWith(launch) }
        // T18: new intents (SEND / VIEW / EDIT) while the activity is already running (singleTask + `MainActivity`'s
        // `addOnNewIntentListener`) — `handle` flushes the current document BEFORE resolving the new one (01 §6.3).
        LaunchedEffect(editorVm) { newIntents.collect(editorVm::handle) }

        LaunchedEffect(commands) {
            commands.collect { cmd ->
                when (cmd) {
                    AppCommand.NewNote -> {
                        libraryVm.newNote()
                    }

                    AppCommand.ToggleLibrary -> {
                        toggleLibrary()
                    }

                    AppCommand.Preview -> {
                        if (editorVm.uiState.value.previewOpen) closePreview() else openPreview()
                    }

                    AppCommand.Find -> {
                        if (editorVm.uiState.value.previewOpen) closePreview()
                        if (!expanded) scope.launch { drawerState.close() }
                        editorVm.openFind()
                    }
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

                    is LibraryEvent.ShareIntent -> {
                        context.startActivity(event.intent)
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
                        settings = settings,
                        onMessage = { text -> scope.launch { snackbarHostState.showSnackbar(text) } },
                        onOpenLibrary = ::toggleLibrary,
                        libraryIcon = libraryIcon,
                        onNewNote = libraryVm::newNote,
                        onSettings = { editorVm.showSheet(RootSheet.Settings) },
                        onPreview = ::openPreview,
                        onCloseFind = ::closeFindBar,
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
                            LibraryPane(
                                libraryVm,
                                container.exporter,
                                editorVm,
                                Modifier.width(WriterDimens.permanentPaneWidth).fillMaxHeight(),
                            )
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
                                LibraryDrawer(libraryVm, container.exporter, editorVm)
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

        // T16's PreviewSlot(): the preview overlay, composed here (after the drawer/pane inside Scaffold, before
        // the root's own find/selection BackHandlers below — see the priority note above).
        val previewPage by editorVm.preview.collectAsStateWithLifecycle()
        val currentDoc by editorVm.current.collectAsStateWithLifecycle()
        PreviewOverlay(
            open = ui.previewOpen,
            page = previewPage,
            holder = previewHolder,
            currentDoc = currentDoc,
            onClose = ::closePreview,
            onShare = { editorVm.shareCurrent() },
        )

        // T19: the Settings / About bottom sheets. About's dismiss returns to Settings; Settings' dismiss closes.
        val sheet by editorVm.sheet.collectAsStateWithLifecycle()
        val exportAll = rememberExportAllNotes(container.exporter, editorVm)
        when (sheet) {
            RootSheet.None -> {}

            RootSheet.Settings -> {
                SettingsSheet(
                    settings = settings,
                    showLineLength =
                        windowSizeClass.isWidthAtLeastBreakpoint(
                            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
                        ),
                    onUpdate = { transform -> scope.launch { container.settings.update(transform) } },
                    onExportAll = {
                        editorVm.showSheet(RootSheet.None)
                        exportAll()
                    },
                    onAbout = { editorVm.showSheet(RootSheet.About) },
                    onDismiss = { editorVm.showSheet(RootSheet.None) },
                )
            }

            RootSheet.About -> {
                AboutSheet(onDismiss = { editorVm.showSheet(RootSheet.Settings) })
            }
        }

        // Cold-start metric ("Fully drawn" in logcat, T21): the first document is installed.
        ReportDrawnWhen { !ui.loading && ui.doc != null }

        BackHandler(enabled = ui.findOpen) { closeFindBar() }
        BackHandler(enabled = selection.start != selection.end) { controller.collapseSelection() }
    }
}
