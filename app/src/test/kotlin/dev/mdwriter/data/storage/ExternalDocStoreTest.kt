package dev.mdwriter.data.storage

import android.Manifest
import android.content.pm.ProviderInfo
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.testing.TestDocumentsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class ExternalDocStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val resolver = context.contentResolver
    private lateinit var store: ExternalDocStore
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        TestDocumentsProvider.flags = TestDocumentsProvider.DEFAULT_FLAGS
        TestDocumentsProvider.throwSecurity = false
        Robolectric
            .buildContentProvider(TestDocumentsProvider::class.java)
            .create(
                ProviderInfo().apply {
                    authority = TestDocumentsProvider.AUTHORITY
                    exported = true
                    grantUriPermissions = true
                    readPermission = Manifest.permission.MANAGE_DOCUMENTS
                    writePermission = Manifest.permission.MANAGE_DOCUMENTS
                },
            )
        settings =
            SettingsRepository(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(Dispatchers.Unconfined + Job()),
                    produceFile = { tmp.newFile("settings.preferences_pb") },
                ),
            )
        store = ExternalDocStore(context, SafIo(resolver), settings, io = Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        // `TestDocumentsProvider`'s flags are static — leaving `throwSecurity = true` here would silently break
        // every OTHER Saf*Test class sharing this JVM fork (neither resets it in its own `@Before`).
        TestDocumentsProvider.throwSecurity = false
    }

    private fun createExternal(
        name: String,
        writable: Boolean = true,
    ): DocRef.External {
        val uri =
            requireNotNull(DocumentsContract.createDocument(resolver, rootDocUri(), NoteFiles.mimeFor(name), name))
        return DocRef.External(uri.toString(), writable)
    }

    private fun rootDocUri() =
        DocumentsContract.buildDocumentUri(TestDocumentsProvider.AUTHORITY, TestDocumentsProvider.ROOT_DOC_ID)

    @Test
    fun readStatDisplayName() =
        runTest {
            val ref = createExternal("Note.md")
            store.write(ref, "hello".toByteArray())
            assertThat(String(store.read(ref))).isEqualTo("hello")
            assertThat(store.displayName(ref)).isEqualTo("Note.md")
            val stat = store.stat(ref)
            assertThat(stat?.size).isEqualTo(5L)
        }

    @Test
    fun writeWhenWritableTruncates() =
        runTest {
            val ref = createExternal("Note.md", writable = true)
            store.write(ref, ByteArray(100) { 'x'.code.toByte() })
            store.write(ref, ByteArray(10) { 'y'.code.toByte() })
            val bytes = store.read(ref)
            assertThat(bytes.size).isEqualTo(10)
        }

    @Test
    fun writeWhenReadOnlyThrowsReadOnly() =
        runTest {
            val ref = createExternal("Note.md", writable = false)
            val error =
                runCatching { store.write(ref, "nope".toByteArray()) }
                    .exceptionOrNull() as? StorageException
            assertThat(error?.error).isEqualTo(StorageError.ReadOnly)
        }

    @Test
    fun securityExceptionMapsToPermissionLost() =
        runTest {
            val ref = createExternal("Note.md")
            TestDocumentsProvider.throwSecurity = true
            val error = runCatching { store.stat(ref) }.exceptionOrNull() as? StorageException
            assertThat(error?.error).isEqualTo(StorageError.PermissionLost)
        }

    @Test
    fun refreshRecomputesWritable() =
        runTest {
            val ref = createExternal("Note.md", writable = false)
            val refreshed = store.refresh(ref)
            assertThat(refreshed.uri).isEqualTo(ref.uri)
        }

    @Test
    fun refreshThrowsPermissionLostWhenGone() =
        runTest {
            val ref = createExternal("Note.md")
            TestDocumentsProvider.throwSecurity = true
            val error = runCatching { store.refresh(ref) }.exceptionOrNull() as? StorageException
            assertThat(error?.error).isEqualTo(StorageError.PermissionLost)
        }
}
