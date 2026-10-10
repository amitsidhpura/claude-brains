package io.github.amitsidhpura.claudebrains.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import io.github.amitsidhpura.claudebrains.RenderLimits

/**
 * The active editor's selection, as the composer pill shows it (checklist 6.6).
 *
 * Watches the three things that change it — the selection itself, the caret (a bare cursor is
 * still "the user is looking at line N of this file"), and the editor tab (switch, close) — and
 * re-reads the selected text editor after a short quiet period, calling [onChange] only when the
 * result differs from the last one delivered. The debounce is what keeps a drag or a held arrow
 * key from streaming a frame per pixel into the webview; the compare is what keeps a caret move
 * inside an unchanged selection silent. VS Code's extension does the same (300 ms, change-only)
 * before its `selection_changed` notification.
 *
 * Lines are 1-based, as the page labels them and as the tag names them. A selection that ends at
 * column 0 of a line (a whole-line drag that took the trailing newline) names the line above as
 * its end, the way the CLI's own TUI counts it. Text is capped at [RenderLimits.SELECTION_MAX_CHARS]
 * here, so a select-all of a large file never crosses the JS bridge whole. Only files with a path
 * count: a console or a light virtual file has nothing the model could open.
 *
 * Everything runs on the EDT — the listeners fire there and the alarm is a Swing-thread alarm —
 * so the editor reads need no explicit read action.
 */
class SelectionTracker(
    private val project: Project,
    parent: Disposable,
    private val onChange: (Sel?) -> Unit,
) {
    data class Sel(val path: String, val start: Int, val end: Int, val text: String)

    @Volatile private var last: Sel? = null
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parent)

    init {
        val mc = EditorFactory.getInstance().eventMulticaster
        mc.addSelectionListener(object : SelectionListener {
            override fun selectionChanged(e: SelectionEvent) = schedule()
        }, parent)
        mc.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) = schedule()
        }, parent)
        project.messageBus.connect(parent).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) = schedule()
            override fun fileClosed(source: FileEditorManager, file: VirtualFile) = schedule()
        })
        schedule()
    }

    /** The last selection delivered — what the page should show after a reload. */
    fun current(): Sel? = last

    private fun schedule() {
        if (project.isDisposed || alarm.isDisposed) return
        alarm.cancelAllRequests()
        alarm.addRequest({ recompute() }, DEBOUNCE_MS)
    }

    private fun recompute() {
        if (project.isDisposed) return
        val now = runCatching { read() }.getOrNull()
        if (now == last) return
        last = now
        onChange(now)
    }

    private fun read(): Sel? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val doc = editor.document
        val path = FileDocumentManager.getInstance().getFile(doc)
            ?.takeIf { it.isInLocalFileSystem && it.isValid }?.path ?: return null
        val sm = editor.selectionModel
        if (sm.hasSelection()) {
            val s = sm.selectionStart
            val e = sm.selectionEnd
            val start = doc.getLineNumber(s) + 1
            var end = doc.getLineNumber(e) + 1
            if (end > start && doc.getLineStartOffset(end - 1) == e) end--
            val text = (sm.selectedText ?: "").let {
                if (it.length > RenderLimits.SELECTION_MAX_CHARS) it.substring(0, RenderLimits.SELECTION_MAX_CHARS) else it
            }
            return Sel(path, start, end, text)
        }
        val line = doc.getLineNumber(editor.caretModel.offset) + 1
        return Sel(path, line, line, "")
    }

    private companion object {
        const val DEBOUNCE_MS = 150
    }
}
