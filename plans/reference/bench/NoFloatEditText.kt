package dev.bench.mdtext

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText

class NoFloatEditText(ctx: Context, private val variant: String) : EditText(ctx) {
    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        Log.i(TAG, "TB startActionMode type=$type variant=$variant")
        if (variant == "null" && type == ActionMode.TYPE_FLOATING) return null
        return super.startActionMode(callback, type)
    }

    var focusStart = -1
    var focusEnd = -1
    private val focusPath = Path()
    private val dimPaint = Paint().apply { color = 0xB3FAFAFA.toInt() } // bg @ 70%

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = layout ?: return
        if (focusStart < 0) return
        focusPath.reset()
        l.getSelectionPath(focusStart, focusEnd, focusPath)
        canvas.save()
        canvas.translate(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
        canvas.clipOutPath(focusPath)
        canvas.drawRect(-totalPaddingLeft.toFloat(), (scrollY - totalPaddingTop).toFloat(), width.toFloat(), (scrollY + height).toFloat(), dimPaint)
        canvas.restore()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        Log.i(TAG, "TB sel=$selStart..$selEnd")
    }

    companion object {
        fun clearingCallback() = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { menu.clear(); return true }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { menu.clear(); return true }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
            override fun onDestroyActionMode(mode: ActionMode) {}
        }
    }
}
