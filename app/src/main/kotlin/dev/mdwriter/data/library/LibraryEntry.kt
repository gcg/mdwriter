package dev.mdwriter.data.library

/** What the UI may offer for an entry. Internal entries are always ALL; T14 fills these from SAF COLUMN_FLAGS. */
data class EntryCaps(
    val write: Boolean = true,
    val rename: Boolean = true,
    val delete: Boolean = true,
    val createChildren: Boolean = true, // folders only: may create notes/folders inside
) {
    companion object {
        val ALL = EntryCaps()
    }
}

data class LibraryEntry(
    val name: String, // display name WITH extension ("Walk.md") or folder name
    val isFolder: Boolean,
    val doc: DocRef?, // non-null for files
    val folder: FolderRef?, // non-null for folders
    val lastModified: Long?,
    val size: Long?,
    val excerpt: String?, // DocTitle.excerpt of the first 2 KB; null for folders / empty files
    val caps: EntryCaps = EntryCaps.ALL,
)
