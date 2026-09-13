package io.github.amitsidhpura.claudebrains.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager

/**
 * "Mention in Claude Brains" — right-click in the Project view, the editor, or an editor tab with
 * any files or folders selected, and each lands in the composer as an `@path` token (checklist
 * 6.5, user's request 2026-09-04: "select items, right click, first option: add these files as
 * mention"; the tab menu on 2026-09-13).
 *
 * The IDE half is deliberately thin: turn the selection into project-relative paths
 * ([MentionPaths.tokens]), bring the tool window up, hand the list to the panel. The composer
 * decides where the tokens go (at the caret, with the spacing rules fixture 75 pins). The CLI
 * expands `@path` mentions itself, so nothing is read here.
 *
 * No shortcut, like every action of this plugin (plugin.xml). Enabled whenever the event carries
 * a non-empty VIRTUAL_FILE_ARRAY or a VIRTUAL_FILE ([files]) — the tool window need not be open:
 * [ToolWindow.activate]'s runnable delivers after the panel exists, and [ChatPanel.insertMentions]
 * parks the list until the page can take it.
 */
class MentionAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val paths = MentionPaths.tokens(project.basePath, files(e).map { it.path to it.isDirectory })
        if (paths.isEmpty()) return
        val deliver = Runnable { project.getUserData(ChatPanel.PANEL_KEY)?.insertMentions(paths) }
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        if (toolWindow != null) toolWindow.activate(deliver, true) else deliver.run()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && files(e).isNotEmpty()
    }

    /**
     * The selection as files: the multi-select array where the context offers one (Project view,
     * editor body), else the single file an editor TAB's context carries (`EditorTabPopupMenu`,
     * 2026-09-13) — a tab is one file, and its DataContext is not obliged to provide the array.
     */
    private fun files(e: AnActionEvent): List<VirtualFile> =
        e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList()?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(e.getData(CommonDataKeys.VIRTUAL_FILE))

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private companion object {
        /** Mirrors `<toolWindow id="Claude Brains">` in plugin.xml — keep the two in step. */
        const val TOOL_WINDOW_ID = "Claude Brains"
    }
}

/**
 * Selection → mention tokens, as a pure function so it can be pinned by [MentionPathsTest]
 * without a VirtualFile in sight. Paths under the project base become project-relative (the same
 * shape the @-picker inserts, see ClaudeSessionService.listProjectFiles); anything outside stays
 * absolute, which the CLI reads just as well. A folder gets a trailing slash so the token reads
 * as one — the CLI lists a `@dir/` mention. Duplicates collapse, order is the selection's.
 */
object MentionPaths {
    /** @param entries (absolute path, isDirectory) per selected item */
    fun tokens(basePath: String?, entries: List<Pair<String, Boolean>>): List<String> {
        val base = basePath?.replace('\\', '/')?.trimEnd('/')
        return entries.map { (raw, isDir) ->
            val p = raw.replace('\\', '/')
            val rel = if (base != null && (p == base || p.startsWith("$base/"))) p.removePrefix(base).trimStart('/') else p
            val shown = rel.ifEmpty { "." }
            if (isDir && !shown.endsWith("/")) "$shown/" else shown
        }.distinct()
    }
}
