package dev.mdwriter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.mdwriter.ui.root.MdWriterRoot
import dev.mdwriter.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // T02 and T20 count this line in logcat to prove the activity was NOT recreated. Keep the tag and wording.
        Log.d(TAG) { "onCreate restored=${savedInstanceState != null}" }
        // T05 rotation check (Acceptance 10): configChanges keeps this Activity alive, so this logs exactly once
        // per process, never once per rotation.
        if (BuildConfig.DEBUG) Log.i(LIFE_TAG) { "onCreate" }
        enableEdgeToEdge()
        setContent {
            MdWriterRoot((application as MdWriterApp).container)
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val LIFE_TAG = "MDLIFE"
    }
}
