package dev.mdwriter.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mdwriter.R
import dev.mdwriter.data.document.Snapshot
import dev.mdwriter.editor.EditorController
import dev.mdwriter.editor.spans.toEditorColors
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterTheme

/**
 * Hosts [controller] (created once by `MdWriterRoot` for the app's lifetime) and wires it to [vm] (T11 step 11,
 * Reference G): events -> controller, edits -> vm, an [EditorBinding] bound while the controller is alive (unbound
 * in `onDispose`, per 01 §5 — no View ever lives in the ViewModel), and the two lifecycle hooks the session needs.
 * [onOpenLibrary] backs a TEMPORARY top-start glyph (T13 replaces this with the fading chrome + swipe gesture).
 */
@Composable
fun EditorScreen(
    vm: EditorViewModel,
    controller: EditorController,
    onMessage: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnMessage by rememberUpdatedState(onMessage)
    val colors = WriterTheme.colors

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
    LaunchedEffect(vm, controller) {
        controller.edits.collect { vm.onEdit(it.version) }
    }
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

    Box(modifier.fillMaxSize()) {
        EditorHost(controller, Modifier.fillMaxSize())
        // TEMPORARY (T12): a plain glyph opens the drawer. T13 replaces this with the fading chrome + swipe gesture.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 4.dp)
                .size(WriterDimens.touchTarget)
                .clip(CircleShape)
                .clickable(onClick = onOpenLibrary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_left_panel_open),
                contentDescription = stringResource(R.string.library_open_library),
                tint = colors.textSecondary,
                modifier = Modifier.size(WriterDimens.icon),
            )
        }
        uiState.conflict?.let { conflict ->
            ConflictBanner(
                conflict = conflict,
                onAction = vm::resolveConflict,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
