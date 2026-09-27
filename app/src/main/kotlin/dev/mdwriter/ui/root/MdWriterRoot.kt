package dev.mdwriter.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mdwriter.AppContainer
import dev.mdwriter.data.settings.Settings
import dev.mdwriter.editor.EditorController
import dev.mdwriter.editor.spans.EditorStyle
import dev.mdwriter.ui.editor.EditorScreen
import dev.mdwriter.ui.editor.EditorViewModel
import dev.mdwriter.ui.library.DeleteUndoSnackbarHost
import dev.mdwriter.ui.library.LibraryDrawer
import dev.mdwriter.ui.library.LibraryEvent
import dev.mdwriter.ui.library.LibraryViewModel
import dev.mdwriter.ui.theme.MdWriterTheme
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Root composable (T11 + T12, 01 §6.4): the app theme driven live from
 * [dev.mdwriter.data.settings.SettingsRepository], one [EditorViewModel] session for its whole lifetime, the
 * library drawer (`ModalNavigationDrawer`, T12) and a shared undo/message snackbar host. T13 adds `commands`/
 * intents and the real fading chrome; T16/T17/T19 add the remaining overlays (preview, find, settings).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MdWriterRoot(
    container: AppContainer,
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

        LaunchedEffect(editorVm) { editorVm.start() }

        LaunchedEffect(drawerState) {
            snapshotFlow { drawerState.targetValue }
                .drop(1)
                .distinctUntilChanged()
                .collect { target ->
                    if (target == DrawerValue.Open) {
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
                        drawerState.close()
                    }

                    is LibraryEvent.Message -> {
                        scope.launch { libraryHostState.showSnackbar(event.text) }
                    }
                }
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { libraryVm.onStop() }

        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val drawerWidth = minOf(WriterDimens.drawerMaxWidth.value, screenWidthDp - WriterDimens.drawerEdgeGap.value).dp

        Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
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
                        }
                    },
                ) {
                    EditorScreen(
                        vm = editorVm,
                        controller = controller,
                        onMessage = { text -> scope.launch { snackbarHostState.showSnackbar(text) } },
                        onOpenLibrary = { scope.launch { drawerState.open() } },
                    )
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
    }
}
