package io.github.amitsidhpura.claudebrains.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager

/**
 * "Mention in Claude Brains" — right-click in the Project view (or the editor) with any files or
 * folders selected, and each lands in the composer as an `@path` token (checklist 6.5, user's
 * request 2026-09-04: "select items, right click, first option: add these files as mention").
 *
 * The IDE half is deliberately thin: turn the selection into project-relative paths
 * ([MentionPaths.tokens]), bring the tool window up, hand the list to the panel. The composer
 * decides where the tokens go (at the caret, with the spacing rules fixture 75 pins). The CLI
 * expands `@path` mentions itself, so nothing is read here.
 *
 * No shortcut, like every action of this plugin (plugin.xml). Enabled whenever the event carries
 * a non-empty VIRTUAL_FILE_ARRAY — the tool window need not be open: [ToolWindow.activate]'s
 * runnable delivers after the panel exists, and [ChatPanel.insertMentions] parks the list until
 * the page can take it.
 */
class MentionAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList().orEmpty()
        val paths = MentionPaths.tokens(project.basePath, files.map { it.path to it.isDirectory })
        if (paths.isEmpty()) return
        val deliver = Runnable { project.getUserData(ChatPanel.PANEL_KEY)?.insertMentions(paths) }
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        if (toolWindow != null) toolWindow.activate(deliver, true) else deliver.run()
    }

    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = e.project != null && !files.isNullOrEmpty()
    }

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
