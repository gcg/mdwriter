package dev.mdwriter

import android.app.Application

/** Creates the manual DI graph. StrictMode (debug only) is added by T20. */
class MdWriterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
