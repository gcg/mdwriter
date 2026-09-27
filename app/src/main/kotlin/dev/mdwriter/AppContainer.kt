package dev.mdwriter

import android.app.Application
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.mdwriter.data.document.AutosaveCoordinator
import dev.mdwriter.data.document.DocumentRepository
import dev.mdwriter.data.library.AutoNamer
import dev.mdwriter.data.library.LibraryRepository
import dev.mdwriter.data.library.TreeGrants
import dev.mdwriter.data.settings.PositionStore
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.storage.InternalStore
import dev.mdwriter.data.storage.RecoveryStore
import dev.mdwriter.data.storage.SafIo
import dev.mdwriter.data.storage.SafTreeStore
import dev.mdwriter.data.storage.TrashBin
import dev.mdwriter.util.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Manual DI root (01-architecture §5). ViewModels read it via `(application as MdWriterApp).container`. */
class AppContainer(
    val app: Application,
) {
    /** Outlives every ViewModel: saves and flushes run here so a finishing activity never drops a write. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val dispatchers: AppDispatchers =
        AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default, main = Dispatchers.Main)

    // Each DataStore owns its own file (never opened twice) via `preferencesDataStoreFile`, ending in
    // `.preferences_pb`, under `filesDir/datastore/`.
    private val settingsDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { app.preferencesDataStoreFile("settings") },
        )
    val settings: SettingsRepository = SettingsRepository(settingsDataStore)

    private val positionsDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { app.preferencesDataStoreFile("positions") },
        )
    val positions: PositionStore = PositionStore(positionsDataStore)

    // Shared by InternalStore and every SafTreeStore (T14): a linked folder's deleted files land in the SAME
    // `.trash/` as internal deletes, so T10's `internalStore.purgeTrash()` 30-day sweep purges both.
    private val trashBin: TrashBin = TrashBin(File(app.filesDir, ".trash"))

    val internalStore: InternalStore = InternalStore(File(app.filesDir, "library"), trashBin)

    val recovery: RecoveryStore = RecoveryStore(File(app.noBackupFilesDir, "recovery"))

    // T14
    val safIo: SafIo = SafIo(app.contentResolver)
    val treeGrants: TreeGrants = TreeGrants(app.contentResolver)

    val library: LibraryRepository =
        LibraryRepository(
            internalStore = internalStore,
            settings = settings,
            io = dispatchers.io,
            treeStoreFactory = { treeUriString ->
                SafTreeStore(Uri.parse(treeUriString), app.contentResolver, safIo, trashBin, dispatchers.io)
            },
            treeGrants = treeGrants,
        )

    val documents: DocumentRepository = DocumentRepository(library, recovery, dispatchers.io)

    val autosave: AutosaveCoordinator = AutosaveCoordinator(applicationScope, dispatchers.default)

    val autoNamer: AutoNamer = AutoNamer(library, documents, settings, positions)

    init {
        // T10's own STATUS entry: "T11 must call internalStore.purgeTrash() on app start."
        applicationScope.launch(dispatchers.io) { internalStore.purgeTrash() }
        // T14: populate `library.locations` before the UI's own ON_START revalidate() runs (MdWriterRoot calls it
        // too, on every ON_START; this pre-seeds it for the very first frame after a cold start).
        applicationScope.launch(dispatchers.io) { library.revalidate() }
    }
}
