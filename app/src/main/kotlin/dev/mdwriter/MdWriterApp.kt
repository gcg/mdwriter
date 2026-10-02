package dev.mdwriter

import android.app.Application
import android.os.Build
import android.os.StrictMode
import dev.mdwriter.util.Log
import dev.mdwriter.util.PerfLog
import dev.mdwriter.util.permitDiskIo

/** Creates the manual DI graph; debug builds enable StrictMode (T18 + T20) and [PerfLog]. */
class MdWriterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        // Start-up only: resolving filesDir/noBackupFilesDir and creating the library/trash/recovery folders and
        // DataStore files is a handful of tiny disk calls that must finish before the first frame (permitted, T20).
        container = permitDiskIo { AppContainer(this) }
    }

    /** Debug builds only; log-only (never `penaltyDeath`). The VM policy extends T18's, never replaces it. */
    private fun enableStrictMode() {
        PerfLog.enabled = true
        val ex = mainExecutor
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .detectResourceMismatches()
                .detectUnbufferedIo()
                .penaltyLog()
                .penaltyListener(ex) { v -> Log.w("MdStrict", "thread", v) }
                .build(),
        )
        val vm =
            StrictMode.VmPolicy
                .Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
                .detectContentUriWithoutPermission()
                .detectFileUriExposure()
                .detectUnsafeIntentLaunch()
                .detectIncorrectContextUse()
                .penaltyLog()
                .penaltyListener(ex) { v -> Log.w("MdStrict", "vm", v) }
        // detectImplicitUriPermissionGrant is API 37: catches a share-out intent missing its URI grant (T18).
        if (Build.VERSION.SDK_INT >= 37) vm.detectImplicitUriPermissionGrant()
        StrictMode.setVmPolicy(vm.build())
    }
}
