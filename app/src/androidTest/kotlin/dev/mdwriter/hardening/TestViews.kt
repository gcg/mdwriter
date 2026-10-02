package dev.mdwriter.hardening

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import dev.mdwriter.editor.MarkdownEditText

internal fun Activity.findEditor(): MarkdownEditText? = window.decorView.findEditorIn()

private fun View.findEditorIn(): MarkdownEditText? {
    if (this is MarkdownEditText) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) getChildAt(i).findEditorIn()?.let { return it }
    }
    return null
}
