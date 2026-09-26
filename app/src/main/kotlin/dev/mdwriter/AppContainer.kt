package dev.mdwriter

import android.app.Application
import dev.mdwriter.util.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual DI root (01-architecture §5). ViewModels read it via `(application as MdWriterApp).container`.
 * Later tasks add: settings, positions (T11/T19), internalStore, recovery (T10), library, documents, autosave (T11+).
 */
class AppContainer(
    val app: Application,
) {
    /** Outlives every ViewModel: saves and flushes run here so a finishing activity never drops a write. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val dispatchers: AppDispatchers =
        AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default, main = Dispatchers.Main)
}
