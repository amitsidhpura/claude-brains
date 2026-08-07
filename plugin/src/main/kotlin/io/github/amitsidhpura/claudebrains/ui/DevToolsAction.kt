package io.github.amitsidhpura.claudebrains.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Find Action → "Claude Brains: Open DevTools" — Chrome DevTools for the chat webview.
 * An IDE action rather than only a webview keystroke because keyboard routes into JCEF
 * fail silently in too many ways (window-manager grabs, IDE keymap, OSR focus); Find
 * Action works from anywhere, panel focus not required.
 */
class DevToolsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.getUserData(ChatPanel.PANEL_KEY)?.openDevtools()
    }

    override fun update(e: AnActionEvent) {
        // Enabled once the tool window has been opened (that's when the panel exists).
        e.presentation.isEnabled = e.project?.getUserData(ChatPanel.PANEL_KEY) != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
