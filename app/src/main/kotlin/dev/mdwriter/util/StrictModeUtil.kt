package dev.mdwriter.util

import android.os.StrictMode

/** For framework-internal disk reads we cannot move off the main thread (WebView provider init, first font load). */
inline fun <T> permitDiskReads(block: () -> T): T {
    val old = StrictMode.allowThreadDiskReads()
    try {
        return block()
    } finally {
        StrictMode.setThreadPolicy(old)
    }
}

/** [permitDiskReads] plus writes: for the tiny one-off `mkdirs`/`filesDir` work in app start-up. */
inline fun <T> permitDiskIo(block: () -> T): T {
    val old = StrictMode.allowThreadDiskReads()
    val oldW = StrictMode.allowThreadDiskWrites()
    try {
        return block()
    } finally {
        StrictMode.setThreadPolicy(oldW)
        StrictMode.setThreadPolicy(old)
    }
}
