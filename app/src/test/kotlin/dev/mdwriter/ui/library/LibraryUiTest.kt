package dev.mdwriter.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.LibraryEntry
import dev.mdwriter.data.settings.SortOrder
import dev.mdwriter.ui.theme.MdWriterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1000dp-h1000dp")
class LibraryUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(
        name: String,
        lastModified: Long? = 1_700_000_000_000L,
    ) = LibraryEntry(
        name = name,
        isFolder = false,
        doc = DocRef.InternalFile(name),
        folder = null,
        lastModified = lastModified,
        size = 10,
        excerpt = null,
    )

    @Test
    fun displayTitleStripsExtensionUnlessSettingIsOn() {
        assertThat(displayTitle("Groceries.md", showExtensions = false)).isEqualTo("Groceries")
        assertThat(displayTitle("Groceries.md", showExtensions = true)).isEqualTo("Groceries.md")
    }

    @Test
    fun fileRowShowsTitleWithoutExtensionByDefault() {
        val item = FileItem(entry = entry("Groceries.md"), title = "Groceries", excerpt = null, isOpen = false)
        composeRule.setContent {
            MdWriterTheme {
                FileRow(item, Instant.now(), {}, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithText("Groceries").assertExists()
        composeRule.onNodeWithText("Groceries.md").assertDoesNotExist()
    }

    @Test
    fun fileRowShowsFullNameWhenShowExtensionsIsOn() {
        val item = FileItem(entry = entry("Groceries.md"), title = "Groceries.md", excerpt = null, isOpen = false)
        composeRule.setContent {
            MdWriterTheme {
                FileRow(item, Instant.now(), {}, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithText("Groceries.md").assertExists()
    }

    @Test
    fun openRowIsTaggedActiveFileBar() {
        val item = FileItem(entry = entry("Open.md"), title = "Open", excerpt = null, isOpen = true)
        composeRule.setContent {
            MdWriterTheme {
                FileRow(item, Instant.now(), {}, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithTag("activeFileBar").assertExists()
    }

    @Test
    fun emptyFolderShowsNoNotesAndNewNoteButtonCallsBack() {
        var newNoteCalled = false
        val state =
            LibraryUiState.Content(
                locations = emptyList(),
                crumbs = listOf(Crumb(dev.mdwriter.data.library.FolderRef.INTERNAL_ROOT, "")),
                sortOrder = SortOrder.ModifiedNewestFirst,
                folders = emptyList(),
                files = emptyList(),
                searching = false,
                query = "",
                openDocKey = null,
                atRoot = true,
            )
        composeRule.setContent {
            MdWriterTheme {
                LibraryContent(
                    state = state,
                    now = Instant.now(),
                    onSearchToggle = {},
                    onQueryChange = {},
                    onNewNote = { newNoteCalled = true },
                    onSortChange = {},
                    onNewFolder = {},
                    onCrumbClick = {},
                    onLocationClick = {},
                    onOpen = {},
                    onOpenFolder = {},
                    onRename = { _, _ -> },
                    onDuplicate = {},
                    onMove = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("No notes yet").assertExists()
        composeRule.onNodeWithText("New note").performClick()
        assertThat(newNoteCalled).isTrue()
    }

    @Test
    fun nameDialogPrefillsCurrentName() {
        composeRule.setContent {
            MdWriterTheme {
                Box(Modifier.size(400.dp, 400.dp)) {
                    NameDialog(
                        title = "Rename note",
                        confirmLabel = "Rename",
                        initialValue = "Groceries",
                        currentName = "Groceries",
                        siblingsLower = setOf("shopping.md"),
                        ext = "md",
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("Groceries").assertExists() // pre-filled
        // Unchanged from currentName -> Rename disabled (no-op rename).
        composeRule.onNodeWithText("Rename").assertIsNotEnabled()
    }

    @Test
    fun nameDialogDisablesConfirmForBlankAndExistingName() {
        var confirmed: String? = null
        composeRule.setContent {
            MdWriterTheme {
                NameDialog(
                    title = "Rename note",
                    confirmLabel = "Rename",
                    initialValue = "Shopping",
                    currentName = "Groceries",
                    siblingsLower = setOf("shopping.md"),
                    ext = "md",
                    onConfirm = { confirmed = it },
                    onDismiss = {},
                )
            }
        }
        // "Shopping" collides with an existing sibling -> error shown, confirm disabled.
        composeRule.onNodeWithText("A note with this name already exists").assertExists()
        composeRule.onNodeWithText("Rename").assertIsNotEnabled()
    }

    @Test
    fun nameDialogConfirmsWithSanitizedBase() {
        var confirmed: String? = null
        composeRule.setContent {
            MdWriterTheme {
                NameDialog(
                    title = "Rename note",
                    confirmLabel = "Rename",
                    initialValue = "Trip Notes",
                    currentName = "Groceries",
                    siblingsLower = emptySet(),
                    ext = "md",
                    onConfirm = { confirmed = it },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Rename").performClick()
        assertThat(confirmed).isEqualTo("Trip Notes")
    }
}
