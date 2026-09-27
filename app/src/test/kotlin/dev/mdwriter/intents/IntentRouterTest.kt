package dev.mdwriter.intents

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentRouterTest {
    private val ownAuthority = "dev.mdwriter.debug.files"

    @Test
    fun viewMarkdownContent() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3Aa.md")
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/markdown")
        val routed = IntentRouter.parse(intent, ownAuthority)
        assertThat(routed).isEqualTo(RoutedIntent.OpenExternal(uri, wantsWrite = false, persistable = false))
    }

    @Test
    fun editWantsWrite() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3Aa.md")
        val intent = Intent(Intent.ACTION_EDIT).setDataAndType(uri, "text/markdown")
        val routed = IntentRouter.parse(intent, ownAuthority) as RoutedIntent.OpenExternal
        assertThat(routed.wantsWrite).isTrue()
    }

    @Test
    fun viewWithWriteGrantFlagAlsoWantsWrite() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3Aa.md")
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "text/markdown")
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val routed = IntentRouter.parse(intent, ownAuthority) as RoutedIntent.OpenExternal
        assertThat(routed.wantsWrite).isTrue()
    }

    @Test
    fun persistableFlagRead() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3Aa.md")
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "text/markdown")
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        val routed = IntentRouter.parse(intent, ownAuthority) as RoutedIntent.OpenExternal
        assertThat(routed.persistable).isTrue()
    }

    @Test
    fun fileSchemeIgnored() {
        val uri = Uri.parse("file:///sdcard/Download/a.md")
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/markdown")
        assertThat(IntentRouter.parse(intent, ownAuthority)).isEqualTo(RoutedIntent.None)
    }

    @Test
    fun ownAuthorityIgnored() {
        val uri = Uri.parse("content://$ownAuthority/exports/a.md")
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/markdown")
        assertThat(IntentRouter.parse(intent, ownAuthority)).isEqualTo(RoutedIntent.None)
    }

    @Test
    fun sendTextWithSubject() {
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Idea")
                .putExtra(Intent.EXTRA_TEXT, "from adb")
        assertThat(IntentRouter.parse(intent, ownAuthority)).isEqualTo(RoutedIntent.ShareText("Idea", "from adb"))
    }

    @Test
    fun sendBlankTextIsNone() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "   ")
        assertThat(IntentRouter.parse(intent, ownAuthority)).isEqualTo(RoutedIntent.None)
    }

    @Test
    fun sendStreamWinsOverText() {
        val uri = Uri.parse("content://com.android.providers.downloads.documents/document/1")
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, "ignored")
        assertThat(IntentRouter.parse(intent, ownAuthority)).isEqualTo(RoutedIntent.ShareStream(uri))
    }

    @Test
    fun launchedFromHistoryIsNone() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3Aa.md")
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "text/markdown")
                .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertThat(IntentRouter.parse(intent, ownAuthority)).isEqualTo(RoutedIntent.None)
    }

    @Test
    fun mainIsNone() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        assertThat(IntentRouter.parse(intent, ownAuthority)).isEqualTo(RoutedIntent.None)
    }

    @Test
    fun nullIntentIsNone() {
        assertThat(IntentRouter.parse(null, ownAuthority)).isEqualTo(RoutedIntent.None)
    }
}
