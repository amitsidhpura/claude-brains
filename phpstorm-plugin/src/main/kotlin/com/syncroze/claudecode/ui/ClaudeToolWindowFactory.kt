package com.syncroze.claudecode.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory

/** Registers the right-side sidebar tool window and hosts the chat panel. */
class ClaudeToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ChatPanel(project, toolWindow.disposable)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)

        // The platform doesn't recolor our stripe icon on selection, so do it ourselves:
        // white while the tool window is open (blue stripe), grey otherwise.
        fun refreshIcon() { toolWindow.setIcon(if (toolWindow.isVisible) ICON_SELECTED else ICON_IDLE) }
        refreshIcon()
        project.messageBus.connect(toolWindow.disposable)
            .subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
                override fun stateChanged(manager: ToolWindowManager) = refreshIcon()
            })
    }

    override fun shouldBeAvailable(project: Project) = true

    companion object {
        private val ICON_IDLE = IconLoader.getIcon("/icons/claude.svg", ClaudeToolWindowFactory::class.java)
        private val ICON_SELECTED = IconLoader.getIcon("/icons/claude_white.svg", ClaudeToolWindowFactory::class.java)
    }
}
