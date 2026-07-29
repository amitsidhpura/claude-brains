package com.syncroze.claudecode.bridge

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.syncroze.claudecode.findVFile
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * Shows Claude's proposed change as a real IntelliJ diff and blocks (via the returned future)
 * until the user accepts or rejects. Matches the VS Code host's openDiff contract:
 *   accept -> ["FILE_SAVED", <final content>]   reject -> ["DIFF_REJECTED", <tab_name>]
 */
class DiffReview(private val project: Project) {

    fun open(oldPath: String, newContent: String, tabName: String): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()
        val vf = findVFile(oldPath)
        val fileName = vf?.name ?: File(oldPath).name
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

        ApplicationManager.getApplication().invokeLater {
            val current = if (vf != null) {
                ReadAction.compute<String, RuntimeException> {
                    FileDocumentManager.getInstance().getDocument(vf)?.text
                        ?: String(vf.contentsToByteArray(), vf.charset)
                }
            } else ""

            val factory = DiffContentFactory.getInstance()
            val request = SimpleDiffRequest(
                "Claude: $fileName",
                factory.create(project, current, fileType),
                factory.create(project, newContent, fileType),
                "Current",
                "Proposed by Claude",
            )
            DiffManager.getInstance().showDiff(project, request)

            val group = NotificationGroupManager.getInstance().getNotificationGroup("Claude Code")
            val notif = group.createNotification(
                "Claude proposes changes to $fileName",
                "Review the diff, then Accept or Reject.",
                NotificationType.INFORMATION,
            )
            notif.addAction(NotificationAction.createSimple("Accept") {
                if (!future.isDone) future.complete(listOf("FILE_SAVED", newContent))
                notif.expire()
            })
            notif.addAction(NotificationAction.createSimple("Reject") {
                if (!future.isDone) future.complete(listOf("DIFF_REJECTED", tabName))
                notif.expire()
            })
            notif.whenExpired {
                // if dismissed without a choice, treat as reject so the CLI isn't left hanging
                if (!future.isDone) future.complete(listOf("DIFF_REJECTED", tabName))
            }
            notif.notify(project)
        }
        return future
    }
}
