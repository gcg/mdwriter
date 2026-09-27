package dev.mdwriter

import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.mdwriter.intents.IntentRouter
import dev.mdwriter.intents.RoutedIntent
import dev.mdwriter.ui.root.AppCommand
import dev.mdwriter.ui.root.AppShortcuts
import dev.mdwriter.ui.root.MdWriterRoot
import dev.mdwriter.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    /** T13: app-level shortcuts (Ctrl+N/O/L), emitted here and collected by `MdWriterRoot`. */
    private val commands = MutableSharedFlow<AppCommand>(extraBufferCapacity = 8)

    /** T18: new intents arriving while this (`singleTask`) activity is already running — SEND/VIEW/EDIT from
     * another app. `MdWriterRoot` collects this and calls `EditorViewModel.handle` for each one. */
    private val newIntents = Channel<RoutedIntent>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // T02 and T20 count this line in logcat to prove the activity was NOT recreated. Keep the tag and wording.
        Log.d(TAG) { "onCreate restored=${savedInstanceState != null}" }
        // T05 rotation check (Acceptance 10): configChanges keeps this Activity alive, so this logs exactly once
        // per process, never once per rotation.
        if (BuildConfig.DEBUG) Log.i(LIFE_TAG) { "onCreate" }
        enableEdgeToEdge()
        val ownAuthority = "$packageName.files"
        // Parse the LAUNCH intent only on a genuine cold start (never on recreation — rotation, process death
        // restore, etc. — or the same shared text/file would be re-imported every time). A relaunch from Recents
        // carries FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY, which IntentRouter.parse also always treats as None.
        val launch =
            if (savedInstanceState == null) IntentRouter.parse(intent, ownAuthority) else RoutedIntent.None
        addOnNewIntentListener { newIntent -> newIntents.trySend(IntentRouter.parse(newIntent, ownAuthority)) }
        setContent {
            MdWriterRoot((application as MdWriterApp).container, commands, launch, newIntents.receiveAsFlow())
        }
    }

    /** Called only when no View (the focused `MarkdownEditText` first, per `MarkdownEditText.onKeyShortcut`
     * falling back to `super`) consumed the shortcut. */
    override fun onKeyShortcut(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean =
        AppShortcuts
            .map(keyCode, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed)
            ?.let { commands.tryEmit(it) } ?: super.onKeyShortcut(keyCode, event)

    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>?,
        menu: Menu?,
        deviceId: Int,
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data?.add(
            KeyboardShortcutGroup(
                getString(R.string.app_name),
                listOf(
                    KeyboardShortcutInfo(
                        getString(R.string.shortcut_new_note),
                        KeyEvent.KEYCODE_N,
                        KeyEvent.META_CTRL_ON,
                    ),
                    KeyboardShortcutInfo(
                        getString(R.string.shortcut_toggle_library),
                        KeyEvent.KEYCODE_L,
                        KeyEvent.META_CTRL_ON,
                    ),
                    KeyboardShortcutInfo(
                        getString(R.string.shortcut_preview),
                        KeyEvent.KEYCODE_R,
                        KeyEvent.META_CTRL_ON,
                    ),
                    KeyboardShortcutInfo(
                        getString(R.string.shortcut_find),
                        KeyEvent.KEYCODE_F,
                        KeyEvent.META_CTRL_ON,
                    ),
                ),
            ),
        )
    }

    private companion object {
        const val TAG = "MainActivity"
        const val LIFE_TAG = "MDLIFE"
    }
}
