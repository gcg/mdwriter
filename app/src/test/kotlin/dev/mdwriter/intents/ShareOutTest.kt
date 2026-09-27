package dev.mdwriter.intents

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.LibraryRepository
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.testing.FakeDocumentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShareOutTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var store: FakeDocumentStore
    private lateinit var library: LibraryRepository
    private lateinit var shareOut: ShareOut

    @Before
    fun setUp() {
        // FileProvider caches a PathStrategy per authority in a static field that Robolectric never resets between
        // test methods in the same class (each method gets its own fresh cacheDir, but the FIRST test's cached
        // strategy would otherwise leak into every later one, "Failed to find configured root" for a real,
        // freshly-written file). Real production code runs the app once per process, so this is a test-environment
        // artifact only, not a bug in ShareOut/FileProvider itself.
        FileProvider::class.java
            .getDeclaredField("sCache")
            .apply { isAccessible = true }
            .let { (it.get(null) as MutableMap<*, *>).clear() }
        store = FakeDocumentStore()
        val settings =
            SettingsRepository(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(Dispatchers.Unconfined + Job()),
                    produceFile = { tmp.newFile("settings.preferences_pb") },
                ),
            )
        library = LibraryRepository(store, settings)
        shareOut = ShareOut(context as android.app.Application, library, io = Dispatchers.Unconfined)
    }

    @Test
    fun buildsChooserForAMarkdownNote() =
        runTest {
            val ref = DocRef.InternalFile("Walk.md")
            store.write(ref, "# Walk\n\nA short one.".toByteArray())

            val chooser = shareOut.intentFor(ref)
            val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            assertThat(send).isNotNull()
            assertThat(send!!.action).isEqualTo(Intent.ACTION_SEND)
            assertThat(send.type).isEqualTo("text/markdown")

            val streamUri = send.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            assertThat(streamUri).isNotNull()
            assertThat(send.clipData?.getItemAt(0)?.uri).isEqualTo(streamUri)
            assertThat(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
            assertThat(send.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo("# Walk\n\nA short one.")

            val exportsDir = File(context.cacheDir, "exports")
            assertThat(exportsDir.walkTopDown().any { it.name == "Walk.md" }).isTrue()
        }

    @Test
    fun noExtraTextAboveTheBinderLimit() =
        runTest {
            val ref = DocRef.InternalFile("Huge.md")
            store.write(ref, "x".repeat(150_000).toByteArray())

            val chooser = shareOut.intentFor(ref)
            val send = requireNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
            assertThat(send.hasExtra(Intent.EXTRA_TEXT)).isFalse()
        }

    @Test
    fun txtExtensionSharesAsPlainText() =
        runTest {
            val ref = DocRef.InternalFile("Notes.txt")
            store.write(ref, "plain text".toByteArray())
            val chooser = shareOut.intentFor(ref)
            val send = requireNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
            assertThat(send.type).isEqualTo("text/plain")
        }
}
