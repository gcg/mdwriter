package dev.mdwriter.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mdwriter.data.document.Snapshot
import dev.mdwriter.editor.EditorController
import dev.mdwriter.editor.spans.EditorStyle
import dev.mdwriter.editor.spans.toEditorColors
import dev.mdwriter.ui.theme.WriterTheme

/**
 * Hosts one [EditorController] for the lifetime of this composable and wires it to [vm] (T11 step 11, Reference
 * G): events -> controller, edits -> vm, an [EditorBinding] bound while the controller is alive (unbound in
 * `onDispose`, per 01 §5 — no View ever lives in the ViewModel), and the two lifecycle hooks the session needs.
 */
@Composable
fun EditorScreen(
    vm: EditorViewModel,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = WriterTheme.colors
    val controller = remember { EditorController(context, EditorStyle.create(context, colors)) }
    val currentOnMessage by rememberUpdatedState(onMessage)

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
        uiState.conflict?.let { conflict ->
            ConflictBanner(
                conflict = conflict,
                onAction = vm::resolveConflict,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
