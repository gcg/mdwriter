package dev.mdwriter.data.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocationTest {
    @Test
    fun internalFileRoundTrips() {
        val ref = DocRef.InternalFile("Drafts/Walk.md")
        assertThat(ref.key()).isEqualTo(DocKey("i:Drafts/Walk.md"))
        assertThat(ref.key().toRef()).isEqualTo(ref)
    }

    @Test
    fun treeDocRoundTrips() {
        val ref = DocRef.TreeDoc("content://tree/abc%3A123", "abc%3A456")
        assertThat(ref.key().toRef()).isEqualTo(ref)
    }

    @Test
    fun treeDocWithPipeInDocumentIdRoundTrips() {
        val ref = DocRef.TreeDoc("content://tree/abc", "abc|def|ghi")
        assertThat(ref.key().toRef()).isEqualTo(ref)
    }

    @Test
    fun externalRoundTripsWithWritableFalse() {
        val ref = DocRef.External("content://docs/1", writable = true)
        val key = ref.key()
        assertThat(key).isEqualTo(DocKey("x:content://docs/1"))
        val back = key.toRef()
        assertThat(back).isEqualTo(DocRef.External("content://docs/1", writable = false))
    }

    @Test
    fun malformedKeysReturnNull() {
        assertThat(DocKey("t:").toRef()).isNull()
        assertThat(DocKey("i:").toRef()).isNull()
        assertThat(DocKey("zz").toRef()).isNull()
    }

    @Test
    fun treeDocMissingPipeReturnsNull() {
        assertThat(DocKey("t:content-only").toRef()).isNull()
    }

    @Test
    fun childPathBuilding() {
        assertThat(FolderRef.INTERNAL_ROOT.childPath("Walk.md")).isEqualTo("Walk.md")
        assertThat(FolderRef(LocationId.Internal, "Drafts").childPath("Walk.md")).isEqualTo("Drafts/Walk.md")
    }

    @Test
    fun internalFileNameAndParentPath() {
        val ref = DocRef.InternalFile("Drafts/Walk.md")
        assertThat(ref.fileName).isEqualTo("Walk.md")
        assertThat(ref.parentPath).isEqualTo("Drafts")
        val rootFile = DocRef.InternalFile("Walk.md")
        assertThat(rootFile.parentPath).isEqualTo("")
    }
}
