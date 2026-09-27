package dev.mdwriter.intents

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.LibraryRepository
import dev.mdwriter.data.library.LocationId
import dev.mdwriter.data.library.LocationState
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.storage.DecodeResult
import dev.mdwriter.data.storage.ExternalDocStore
import dev.mdwriter.data.storage.NoteFiles
import dev.mdwriter.data.storage.StorageError
import dev.mdwriter.data.storage.StorageException
import dev.mdwriter.data.storage.TextCodec
import dev.mdwriter.data.storage.TextFormat
import dev.mdwriter.data.storage.userMessage

/** What resolving a [RoutedIntent] produced: either a document to open, a message to show, or nothing at all. */
sealed interface IntentOutcome {
    data class Open(
        val ref: DocRef,
        val showIme: Boolean,
    ) : IntentOutcome

    data class Message(
        val text: String,
    ) : IntentOutcome

    data object Nothing : IntentOutcome
}

/**
 * Resolves a [RoutedIntent] into an [IntentOutcome] (T18 step 6): opening an external file (preferring an already-
 * linked SAF tree over a fresh [ExternalDocStore] grant), importing shared text as a new note, or importing a
 * shared file's bytes UNCHANGED. Every [StorageException]/[SecurityException] becomes a [IntentOutcome.Message].
 */
class IntentHandler(
    private val context: Context,
    private val library: LibraryRepository,
    private val externalStore: ExternalDocStore,
    private val settings: SettingsRepository,
) {
    suspend fun resolve(r: RoutedIntent): IntentOutcome =
        try {
            when (r) {
                is RoutedIntent.OpenExternal -> resolveOpenExternal(r)
                is RoutedIntent.ShareText -> resolveShareText(r)
                is RoutedIntent.ShareStream -> resolveShareStream(r)
                RoutedIntent.None -> IntentOutcome.Nothing
            }
        } catch (e: StorageException) {
            IntentOutcome.Message(
                if (e.error is StorageError.TooLarge) "Too large for mdwriter" else e.error.userMessage(),
            )
        } catch (_: SecurityException) {
            IntentOutcome.Message(StorageError.PermissionLost.userMessage())
        }

    private suspend fun resolveOpenExternal(r: RoutedIntent.OpenExternal): IntentOutcome {
        val ref = treeDocFor(r.uri) ?: externalStore.adopt(r.uri, r.persistable)
        return IntentOutcome.Open(ref, showIme = false)
    }

    /** True iff [uri] lives inside an already-linked, Ready [LocationId.Tree] (same authority + `isChildDocument`)
     * — opening it that way keeps write access, live external-change watching etc. working exactly like any other
     * file already in that folder, instead of a second, disconnected [DocRef.External] grant on the same file. */
    private fun treeDocFor(uri: Uri): DocRef.TreeDoc? {
        for (loc in library.locations.value) {
            val id = loc.id
            if (loc.state != LocationState.Ready || id !is LocationId.Tree) continue
            val treeUri = runCatching { Uri.parse(id.treeUri) }.getOrNull() ?: continue
            if (treeUri.authority != uri.authority) continue
            val rootDocUri =
                DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            val isChild =
                runCatching {
                    DocumentsContract.isChildDocument(context.contentResolver, rootDocUri, uri)
                }.getOrDefault(false)
            if (isChild) return DocRef.TreeDoc(id.treeUri, DocumentsContract.getDocumentId(uri))
        }
        return null
    }

    private suspend fun resolveShareText(r: RoutedIntent.ShareText): IntentOutcome {
        val shared = SharedNote.compose(r.subject, r.text)
        val ext = settings.current().newNoteExtension
        val ref = library.createUnique(library.rootOf(LocationId.Internal), shared.baseName, ext)
        library.storeFor(ref).write(ref, TextCodec.encode(shared.body, TextFormat.DEFAULT))
        return IntentOutcome.Open(ref, showIme = true)
    }

    private suspend fun resolveShareStream(r: RoutedIntent.ShareStream): IntentOutcome {
        val tempRef = DocRef.External(r.uri.toString(), writable = false)
        val rawName = externalStore.displayName(tempRef)
        val name =
            if (NoteFiles.isSupported(rawName)) {
                rawName
            } else {
                "${NoteFiles.baseName(rawName).ifBlank { "Shared file" }}.md"
            }
        val bytes = externalStore.read(tempRef)
        if (TextCodec.decode(bytes) is DecodeResult.Binary) {
            return IntentOutcome.Message("Only text files can be imported")
        }
        val ref =
            library.createUnique(
                library.rootOf(LocationId.Internal),
                NoteFiles.baseName(name),
                NoteFiles.extensionOf(name).ifEmpty { "md" },
            )
        library.storeFor(ref).write(ref, bytes) // bytes unchanged (BOM/CRLF preserved) — 01 pitfalls.
        return IntentOutcome.Open(ref, showIme = false)
    }
}
