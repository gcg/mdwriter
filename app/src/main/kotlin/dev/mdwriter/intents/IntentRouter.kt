package dev.mdwriter.intents

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

/** What [IntentRouter.parse] made of a launch/new intent — never Android-specific beyond [Uri]/[Intent] itself, so
 * [dev.mdwriter.ui.editor.EditorViewModel]/[IntentHandler] can resolve it without touching an [Intent] again. */
sealed interface RoutedIntent {
    data class OpenExternal(
        val uri: Uri,
        val wantsWrite: Boolean,
        val persistable: Boolean,
    ) : RoutedIntent

    data class ShareText(
        val subject: String?,
        val text: String,
    ) : RoutedIntent

    data class ShareStream(
        val uri: Uri,
    ) : RoutedIntent

    data object None : RoutedIntent
}

/**
 * Turns a launch/new [Intent] into a [RoutedIntent] (01 §3/§8). Never opens anything itself — [IntentHandler] does
 * the actual resolving (adopting a grant, creating a note, reading a stream).
 */
object IntentRouter {
    /**
     * [ownAuthority] (`"$packageName.files"`) makes mdwriter ignore its own [dev.mdwriter.intents.ShareOut] exports
     * coming back as a VIEW/EDIT intent (e.g. a share-to-self loop through another app). A launch intent carrying
     * [Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY] (a plain relaunch from Recents) is always [RoutedIntent.None] —
     * otherwise shared text would be re-imported as a new note every time.
     */
    fun parse(
        intent: Intent?,
        ownAuthority: String? = null,
    ): RoutedIntent {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return RoutedIntent.None

        fun Uri.ok() = scheme == ContentResolver.SCHEME_CONTENT && authority != ownAuthority
        return when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
                intent.data?.takeIf { it.ok() }?.let { uri ->
                    RoutedIntent.OpenExternal(
                        uri,
                        wantsWrite =
                            intent.action == Intent.ACTION_EDIT ||
                                intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0,
                        persistable = intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0,
                    )
                } ?: RoutedIntent.None
            }

            Intent.ACTION_SEND -> {
                val stream =
                    intent
                        .getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        ?.takeIf { it.ok() }
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotEmpty() }
                when {
                    stream != null -> RoutedIntent.ShareStream(stream)
                    !text.isNullOrBlank() -> RoutedIntent.ShareText(subject, text)
                    else -> RoutedIntent.None
                }
            }

            else -> {
                RoutedIntent.None
            }
        }
    }
}
