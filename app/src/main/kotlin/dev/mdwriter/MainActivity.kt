package dev.mdwriter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import dev.mdwriter.debug.FrameWorkLogger
import dev.mdwriter.debug.SampleDocs
import dev.mdwriter.editor.EditorController
import dev.mdwriter.editor.InstallRequest
import dev.mdwriter.editor.MdEditableFactory
import dev.mdwriter.editor.spans.EditorStyle
import dev.mdwriter.editor.spans.toEditorColors
import dev.mdwriter.ui.editor.EditorHost
import dev.mdwriter.ui.theme.DesignGallery
import dev.mdwriter.ui.theme.MdWriterTheme
import dev.mdwriter.ui.theme.WriterTheme
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
            MdWriterTheme {
                if (BuildConfig.DEBUG && intent.getBooleanExtra("gallery", false)) {
                    DesignGallery()
                } else {
                    EditorDemo()
                }
            }
        }
    }

    // T11 replaces this with EditorScreen/EditorViewModel + real document loading.
    @Composable
    private fun EditorDemo() {
        val colors = WriterTheme.colors
        if (BuildConfig.DEBUG) MdEditableFactory.enabled = intent.getBooleanExtra("mdEditable", true)
        val controller = remember { EditorController(this, EditorStyle.create(this, colors)) }
        val frameLogger =
            remember {
                if (BuildConfig.DEBUG && intent.getBooleanExtra("frameLog", false)) FrameWorkLogger(window) else null
            }
        LaunchedEffect(Unit) {
            controller.install(
                InstallRequest(
                    text =
                        (if (BuildConfig.DEBUG) SampleDocs.forExtra(intent.getStringExtra("sample")) else null)
                            ?: "",
                    selection = 0,
                    scrollY = 0,
                    readOnly = false,
                ),
            )
        }
        LaunchedEffect(colors) {
            controller.setStyle(controller.style.also { it.colors = colors.toEditorColors() })
        }
        DisposableEffect(Unit) {
            onDispose {
                frameLogger?.stop()
                controller.release()
            }
        }
        EditorHost(controller)
    }

    private companion object {
        const val TAG = "MainActivity"
        const val LIFE_TAG = "MDLIFE"
    }
}
