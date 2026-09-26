package dev.mdwriter.ui.root

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mdwriter.AppContainer
import dev.mdwriter.data.settings.Settings
import dev.mdwriter.ui.editor.EditorScreen
import dev.mdwriter.ui.editor.EditorViewModel
import dev.mdwriter.ui.theme.MdWriterTheme
import kotlinx.coroutines.launch

/**
 * Root composable (T11, 01 §6.4): the app theme driven live from [dev.mdwriter.data.settings.SettingsRepository],
 * one [EditorViewModel] session for its whole lifetime, and a [SnackbarHost] for its one-shot
 * [dev.mdwriter.ui.editor.EditorEvent.Message]s. T13 adds `commands`/intents; T12 adds the library drawer; T16/
 * T17/T19 add the remaining overlays (preview, find, settings).
 */
@Composable
fun MdWriterRoot(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    MdWriterTheme(themeMode = settings.themeMode, pureBlack = settings.pureBlack, font = settings.typeface) {
        val vm: EditorViewModel = viewModel(factory = EditorViewModel.Factory)
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(vm) { vm.start() }

        Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            EditorScreen(
                vm = vm,
                onMessage = { text -> scope.launch { snackbarHostState.showSnackbar(text) } },
                modifier = Modifier.padding(padding),
            )
        }
    }
}
