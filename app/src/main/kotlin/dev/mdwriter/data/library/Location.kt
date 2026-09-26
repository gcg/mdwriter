package dev.mdwriter.data.library

sealed interface LocationId {
    data object Internal : LocationId

    data class Tree(
        val treeUri: String,
    ) : LocationId
}

sealed interface DocRef {
    /** '/'-separated path relative to filesDir/library, e.g. "Drafts/Walk.md". Never starts with '/'. */
    data class InternalFile(
        val relPath: String,
    ) : DocRef

    data class TreeDoc(
        val treeUri: String,
        val documentId: String,
    ) : DocRef

    data class External(
        val uri: String,
        val writable: Boolean,
    ) : DocRef
}

/** Internal: rel path of the folder ("" = library root). Tree: SAF documentId (root = tree document id). */
data class FolderRef(
    val location: LocationId,
    val id: String,
) {
    companion object {
        val INTERNAL_ROOT = FolderRef(LocationId.Internal, "")
    }
}

/** Stable string id: "i:<relPath>" | "t:<treeUri>|<documentId>" | "x:<uri>". Used for settings, positions, recovery. */
@JvmInline
value class DocKey(
    val value: String,
)

fun DocRef.key(): DocKey =
    when (this) {
        is DocRef.InternalFile -> DocKey("i:$relPath")
        is DocRef.TreeDoc -> DocKey("t:$treeUri|$documentId")
        is DocRef.External -> DocKey("x:$uri") // 'writable' is NOT part of the identity
    }

/** Inverse of [key]. External refs come back with writable=false; the caller re-checks the grant (T18). */
fun DocKey.toRef(): DocRef? =
    when {
        value.startsWith("i:") -> {
            DocRef.InternalFile(value.substring(2)).takeIf { it.relPath.isNotEmpty() }
        }

        value.startsWith("t:") -> {
            val rest = value.substring(2)
            val bar = rest.indexOf('|') // tree URIs are percent-encoded, so the FIRST '|' separates tree and docId
            if (bar <= 0 || bar == rest.lastIndex) {
                null
            } else {
                DocRef.TreeDoc(rest.substring(0, bar), rest.substring(bar + 1))
            }
        }

        value.startsWith("x:") && value.length > 2 -> {
            DocRef.External(value.substring(2), writable = false)
        }

        else -> {
            null
        }
    }

val DocRef.location: LocationId?
    get() =
        when (this) {
            is DocRef.InternalFile -> LocationId.Internal
            is DocRef.TreeDoc -> LocationId.Tree(treeUri)
            is DocRef.External -> null
        }

val DocRef.InternalFile.fileName: String get() = relPath.substringAfterLast('/')
val DocRef.InternalFile.parentPath: String get() = relPath.substringBeforeLast('/', missingDelimiterValue = "")

/** Internal only: child rel path of this folder. */
fun FolderRef.childPath(name: String): String = if (id.isEmpty()) name else "$id/$name"
