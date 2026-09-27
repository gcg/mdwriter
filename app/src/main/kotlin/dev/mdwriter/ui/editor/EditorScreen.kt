package dev.mdwriter.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mdwriter.R
import dev.mdwriter.data.document.Snapshot
import dev.mdwriter.data.settings.Settings
import dev.mdwriter.editor.EditorController
import dev.mdwriter.editor.FocusModeKind
import dev.mdwriter.editor.StatsInput
import dev.mdwriter.editor.spans.toEditorColors
import dev.mdwriter.ui.gesture.SwipeDir
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Hosts [controller] (created once by `MdWriterRoot` for the app's lifetime) and wires it to [vm] (T11 step 11,
 * Reference G): events -> controller, edits -> vm (+ [ChromeVisibility] via [EditorViewModel.chrome]), an
 * [EditorBinding] bound while the controller is alive (unbound in `onDispose`, per 01 §5 — no View ever lives in
 * the ViewModel), and the two lifecycle hooks the session needs.
 *
 * T13 replaces the T12 temporary glyph with the real fading [EditorChrome] (library + overflow glyphs) and the
 * swipe-navigation gesture (wired through [EditorHost]); [onOpenLibrary]/[libraryIcon]/[onNewNote]/[swipeEnabled]/
 * [swipeAccepts]/[onSwipeArmedDown]/[onSwipe] all come from `MdWriterRoot`, which is the only place that knows
 * about the drawer/pane/preview/find/settings state the swipe and the library glyph need to react to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    vm: EditorViewModel,
    controller: EditorController,
    settings: Settings,
    onMessage: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    libraryIcon: Int,
    onNewNote: () -> Unit,
    swipeEnabled: () -> Boolean,
    swipeAccepts: (SwipeDir) -> Boolean,
    onSwipeArmedDown: () -> Unit,
    onSwipe: (SwipeDir) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnMessage by rememberUpdatedState(onMessage)
    val colors = WriterTheme.colors
    val density = LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { WriterDimens.chromeScrollUpThreshold.toPx() } }

    // ---- T15: push Focus Mode / typewriter settings straight onto the controller ---------------------------------
    LaunchedEffect(controller, settings.focusMode) { controller.focusMode = settings.focusMode }
    LaunchedEffect(controller, settings.typewriter) { controller.typewriter = settings.typewriter }

    // ---- T15: stats pipeline (01 §7 — debounced, computed on Default, never main) ----------------------------------
    LaunchedEffect(controller, settings.wordCount) {
        if (!settings.wordCount) {
            vm.onStats(null)
        } else {
            val source =
                object : StatsSource {
                    override val triggers: Flow<Unit> =
                        merge(
                            controller.edits.map { },
                            controller.selection
                                .map { it.start to it.end }
                                .distinctUntilChanged()
                                .map { },
                        )
                    override val version: Long get() = controller.version
                    override val selection: Pair<Int, Int> get() = controller.selection.value.let { it.start to it.end }

                    override fun snapshot(): StatsInput = controller.statsInput()
                }
            StatsPipeline(source).flow().collect(vm::onStats)
        }
    }

    LaunchedEffect(vm, controller) {
        vm.events.collect { e ->
            when (e) {
                is EditorEvent.Install -> {
                    controller.install(e.request)
                    vm.onInstalled(controller.version)
                }

                is EditorEvent.AfterOpen -> {
                    if (e.showIme) {
                        controller.requestFocus()
                        controller.showIme()
                    } else {
                        controller.hideIme()
                    }
                }

                is EditorEvent.Message -> {
                    currentOnMessage(e.text)
                }
            }
        }
    }
    // One collector for controller.edits (a SharedFlow — unlike the Channel-backed events above, a SharedFlow
    // broadcasts to every collector, so a second `collect` here would be safe too; kept as one for symmetry).
    LaunchedEffect(vm, controller) {
        controller.edits.collect {
            vm.onEdit(it.version)
            vm.chrome.onEdit()
        }
    }
    LaunchedEffect(vm, controller, scrollThresholdPx) {
        controller.scrollChanges.collect { change ->
            vm.chrome.onScroll(change.dy.toFloat(), scrollThresholdPx)
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(vm, imeVisible) { vm.chrome.onImeVisibility(imeVisible) }
    DisposableEffect(vm, controller) {
        vm.bindEditor(
            object : EditorBinding {
                override fun snapshot() = Snapshot(controller.version, controller.snapshot())

                override fun caret() = controller.caret()

                override fun scrollY() = controller.scrollY()
            },
        )
        onDispose { vm.unbindEditor() }
    }
    LaunchedEffect(colors) {
        controller.setStyle(controller.style.also { it.colors = colors.toEditorColors() })
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val canUndo by controller.canUndo.collectAsStateWithLifecycle()
    val canRedo by controller.canRedo.collectAsStateWithLifecycle()
    var overflowExpanded by remember { mutableStateOf(false) }
    var statsDisplay by rememberSaveable { mutableStateOf(StatsDisplay.Words) }

    val focusLabels =
        listOf(
            stringResource(R.string.focus_off),
            stringResource(R.string.focus_sentence),
            stringResource(R.string.focus_paragraph),
        )

    Box(modifier.fillMaxSize()) {
        EditorHost(
            controller,
            Modifier.fillMaxSize(),
            swipeEnabled = swipeEnabled,
            swipeAccepts = swipeAccepts,
            onSwipeArmedDown = onSwipeArmedDown,
            onSwipe = onSwipe,
            onTopTap = { vm.chrome.onTopTap() },
            onOpenLibrary = onOpenLibrary,
        )
        EditorChrome(
            visible = uiState.chromeVisible,
            widthClass = controller.style.widthClass,
            libraryIcon = libraryIcon,
            onLibrary = onOpenLibrary,
            overflowExpanded = overflowExpanded,
            onOverflow = { overflowExpanded = true },
            onOverflowDismiss = { overflowExpanded = false },
            overflow =
                OverflowActions(
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndo = controller::undo,
                    onRedo = controller::redo,
                    onNewNote = onNewNote,
                    focus =
                        OverflowChoice(
                            options = focusLabels,
                            selected = settings.focusMode.ordinal,
                            onSelect = { index -> vm.setFocusMode(FocusModeKind.entries[index]) },
                        ),
                    typewriter = OverflowToggle(checked = settings.typewriter, onChange = vm::setTypewriter),
                    wordCount = OverflowToggle(checked = settings.wordCount, onChange = vm::setWordCount),
                ),
            stats = { chromeVisible ->
                StatsLine(
                    stats = uiState.stats,
                    isSelection = uiState.statsSelection,
                    display = statsDisplay,
                    typing = !chromeVisible,
                    onCycle = { statsDisplay = statsDisplay.next() },
                )
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        uiState.conflict?.let { conflict ->
            ConflictBanner(
                conflict = conflict,
                onAction = vm::resolveConflict,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
