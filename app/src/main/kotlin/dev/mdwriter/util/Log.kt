package dev.mdwriter.util

import dev.mdwriter.BuildConfig

/**
 * The only logging entry point: import dev.mdwriter.util.Log, never android.util.Log.
 * d/i are debug-only (and R8 strips android.util.Log.v/d from release anyway, see keepRules/app.keep).
 * Note: plain JVM unit tests (no Robolectric) cannot call android.util.Log ("Method ... not mocked"),
 * so pure classes that are tested on the JVM must not log.
 */
object Log {
    inline fun d(
        tag: String,
        message: () -> String,
    ) {
        if (BuildConfig.DEBUG) android.util.Log.d(tag, message())
    }

    inline fun i(
        tag: String,
        message: () -> String,
    ) {
        if (BuildConfig.DEBUG) android.util.Log.i(tag, message())
    }

    fun w(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        android.util.Log.w(tag, message, error)
    }

    fun e(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        android.util.Log.e(tag, message, error)
    }
}
