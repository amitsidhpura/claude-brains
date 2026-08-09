package io.github.amitsidhpura.claudebrains.bridge

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import io.github.amitsidhpura.claudebrains.findVFile
import io.github.amitsidhpura.claudebrains.readLocked
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shows Claude's proposed change as a real IntelliJ diff and resolves (via the returned future)
 * when the user decides. Matches the VS Code host's openDiff contract — read from the installed
 * extension (2.1.222) and cross-checked against the CLI's consumer in the 2.1.226 binary,
 * 2026-08-09:
 *   accept     -> ["FILE_SAVED", <final right-pane text>]  (the user may have edited the pane)
 *   reject     -> ["DIFF_REJECTED", <tab_name>]
 *   tab closed -> ["TAB_CLOSED"]
 *
 * The IDE deliberately does NOT write the file. In the reference implementation BOTH diff panes
 * are temp-provider documents — the real file is never touched — and the CLI applies the accepted
 * content itself: its consumer maps FILE_SAVED -> {newContent: <returned text>}, TAB_CLOSED ->
 * {newContent: <as proposed>}, DIFF_REJECTED -> {newContent: <old content>} and feeds that into
 * its own Edit/Write machinery. "FILE_SAVED" is the accept TOKEN, not a claim about disk.
 * (Manual-test 10.5's original "accept never writes" ISSUE misread this contract from a
 * caller-less direct-WS probe: nothing wrote because the probe, unlike the CLI, never acts on
 * the verdict.)
 */
class DiffReview(private val project: Project) {

    /** Reviews still awaiting a verdict — so close_tab or a dying caller can resolve them. */
    private val pending = ConcurrentHashMap.newKeySet<CompletableFuture<List<String>>>()

    /**
     * The diff editor's virtual file per pending review — the ONLY reliable handle for closing
     * the tab. `FileEditorManager.openFiles` does NOT report diff editors (measured live
     * 2026-08-09: a visible "Claude: …" diff tab alongside "(no open editors)"), so any
     * find-then-close approach silently closes nothing; the file must be held from creation.
     */
    private val files = ConcurrentHashMap<CompletableFuture<List<String>>, ChainDiffVirtualFile>()

    /**
     * [readOnly] locks both panes. The bridge flow leaves the right pane editable because its
     * final text travels back in the FILE_SAVED verdict; the permission flow answers allow/deny
     * with the ORIGINAL input, so an editable pane there would silently discard the user's typing
     * — read-only keeps it honest until tweak-travel is built for that flow.
     */
    fun open(
        oldPath: String,
        newContent: String,
        tabName: String,
        readOnly: Boolean = false,
    ): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()
        pending.add(future)
        val vf = findVFile(oldPath)
        val fileName = vf?.name ?: File(oldPath).name
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

        ApplicationManager.getApplication().invokeLater {
            val current = if (vf != null) {
                readLocked {
                    FileDocumentManager.getInstance().getDocument(vf)?.text
                        ?: String(vf.contentsToByteArray(), vf.charset)
                }
            } else ""

            val factory = DiffContentFactory.getInstance()
            // Kept as a DocumentContent: the right pane is editable, and accept must return its
            // FINAL text — the reference sends the pane's getText(), user tweaks included.
            val proposed: DocumentContent = factory.create(project, newContent, fileType)
            val assigned = AtomicBoolean(false)
            val title = "Claude: $fileName"
            val request = object : SimpleDiffRequest(
                title,
                factory.create(project, current, fileType),
                proposed,
                "Current",
                "Proposed by Claude",
            ) {
                override fun onAssigned(isAssigned: Boolean) {
                    super.onAssigned(isAssigned)
                    assigned.set(isAssigned)
                    // Closing the diff tab/window resolves TAB_CLOSED, like the reference's
                    // tab-watcher (its CLI-side meaning: accept as proposed). A viewer SWITCH
                    // (side-by-side <-> unified) also unassigns before reassigning, so only a
                    // close stays unassigned — check again after a beat rather than on the edge.
                    if (!isAssigned && !future.isDone) {
                        AppExecutorUtil.getAppScheduledExecutorService().schedule({
                            if (!assigned.get()) future.complete(listOf("TAB_CLOSED"))
                        }, 500, TimeUnit.MILLISECONDS)
                    }
                }
            }
            if (readOnly) request.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true)
            // Opened as OUR OWN ChainDiffVirtualFile through the ordinary editor API rather
            // than DiffManager.showDiff, so the tab can be closed deterministically later —
            // see [files]. The platform's diff editor provider picks the file up like any other.
            if (!future.isDone) {
                val diffFile = ChainDiffVirtualFile(SimpleDiffRequestChain(request), title)
                files[future] = diffFile
                FileEditorManager.getInstance(project).openFile(diffFile, true)
            }

            val group = NotificationGroupManager.getInstance().getNotificationGroup("Claude Brains")
            val notif = group.createNotification(
                "Claude proposes changes to $fileName",
                "Review the diff, then Accept or Reject.",
                NotificationType.INFORMATION,
            )
            notif.addAction(NotificationAction.createSimple("Accept") {
                if (!future.isDone) {
                    future.complete(listOf("FILE_SAVED", readLocked { proposed.document.text }))
                }
            })
            notif.addAction(NotificationAction.createSimple("Reject") {
                if (!future.isDone) future.complete(listOf("DIFF_REJECTED", tabName))
            })
            // No auto-verdict on balloon expiry: dismissing a notification is not a decision,
            // and the diff tab remains the decision surface (closing IT resolves TAB_CLOSED).
            // The balloon's lifecycle is tied to the future instead: however the review ends —
            // buttons, tab close, close_tab, caller death — the balloon dies with it, so a
            // stale Accept can never fire into a finished (or abandoned) call.
            expireWith(future, notif)
            if (!future.isDone) notif.notify(project)
        }
        return future
    }

    /** close_tab / closeAllDiffTabs: an undecided review resolves TAB_CLOSED, as the reference's own close path does. */
    fun completeAllTabClosed() {
        pending.forEach { it.complete(listOf("TAB_CLOSED")) }
    }

    /** The caller is gone (socket closed, server shutdown): unblock the dispatch threads and clean up. */
    fun cancelAll() {
        pending.forEach { it.cancel(true) }
    }

    /**
     * The question was answered somewhere else (the panel card, in the permission flow): this
     * review's verdict can no longer matter. Cancelling routes through the same completion hook
     * as every other ending, which closes the diff tab and expires the balloon.
     */
    fun dismiss(future: CompletableFuture<List<String>>) {
        future.cancel(true)
    }

    /**
     * However a review ends — verdict, dismissal, caller death — its diff tab closes with it,
     * as the reference's own accept/reject path does (it saves the temp doc and closes the tab).
     * Closed by the exact file handle held since creation; on a TAB_CLOSED verdict the tab is
     * already gone and closeFile is a silent no-op.
     */
    private fun expireWith(future: CompletableFuture<List<String>>, notif: Notification) {
        future.whenComplete { _, _ ->
            pending.remove(future)
            val diffFile = files.remove(future)
            ApplicationManager.getApplication().invokeLater {
                notif.expire()
                diffFile?.let { FileEditorManager.getInstance(project).closeFile(it) }
            }
        }
    }
}
