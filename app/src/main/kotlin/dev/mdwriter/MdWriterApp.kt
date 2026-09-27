package dev.mdwriter

import android.app.Application
import android.os.Build
import android.os.StrictMode

/** Creates the manual DI graph. T18 adds a minimal debug-only StrictMode VmPolicy (just the URI-grant check share-
 * out needs to stay honest); T20 extends it (via `Builder(StrictMode.getVmPolicy())`, never replacing it). */
class MdWriterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // detectImplicitUriPermissionGrant is API 37 — guard it (same debug-build gate as util/Log.kt). Catches a
        // share-out intent that forgot FLAG_GRANT_READ_URI_PERMISSION/ClipData (01 pitfalls) the moment it's fired,
        // not just when a receiving app happens to choke on it.
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 37) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy
                    .Builder(StrictMode.getVmPolicy())
                    .detectImplicitUriPermissionGrant()
                    .penaltyLog()
                    .build(),
            )
        }
    }
}
