package dev.mdwriter.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.library.DocKey
import dev.mdwriter.editor.FocusModeKind
import dev.mdwriter.ui.theme.ThemeMode
import dev.mdwriter.ui.theme.WriterFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scopeJob: Job
    private lateinit var store: DataStore<Preferences>
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        scopeJob = Job()
        store =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.Unconfined + scopeJob),
                produceFile = { tmp.newFile("settings.preferences_pb") },
            )
        repo = SettingsRepository(store)
    }

    @After
    fun tearDown() {
        scopeJob.cancel()
    }

    @Test
    fun defaultsWhenEmpty() =
        runTest {
            assertThat(repo.current()).isEqualTo(Settings())
        }

    @Test
    fun roundTripsEveryField() =
        runTest {
            val custom =
                Settings(
                    themeMode = ThemeMode.Dark,
                    pureBlack = true,
                    typeface = WriterFont.Mono,
                    textSizeStep = 4,
                    lineLength = 80,
                    focusMode = FocusModeKind.Paragraph,
                    typewriter = true,
                    wordCount = true,
                    swipeNavigation = false,
                    highlightSyntax = true,
                    newNoteExtension = "txt",
                    showExtensions = true,
                    sortOrder = SortOrder.NameZToA,
                    lastOpenDoc = DocKey("i:Foo.md"),
                    linkedTrees = listOf("tree1", "tree2"),
                    autoNamed = setOf("i:Untitled.md"),
                    hintDismissed = true,
                    recentExternal = listOf("x:uri1", "x:uri2"),
                    welcomeCreated = true,
                )
            repo.update { custom }
            assertThat(repo.current()).isEqualTo(custom)
        }

    @Test
    fun keepsListOrder() =
        runTest {
            repo.update { it.copy(linkedTrees = listOf("c", "a", "b")) }
            assertThat(repo.current().linkedTrees).containsExactly("c", "a", "b").inOrder()
        }

    @Test
    fun unknownEnumFallsBack() =
        runTest {
            store.edit { it[stringPreferencesKey("theme_mode")] = "NotARealValue" }
            assertThat(repo.current().themeMode).isEqualTo(ThemeMode.System)
        }

    @Test
    fun concurrentUpdatesBothApply() =
        runTest {
            val a = async { repo.update { it.copy(pureBlack = true) } }
            val b = async { repo.update { it.copy(wordCount = true) } }
            a.await()
            b.await()
            val result = repo.current()
            assertThat(result.pureBlack).isTrue()
            assertThat(result.wordCount).isTrue()
        }
}
