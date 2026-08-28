package io.github.amitsidhpura.claudebrains.bridge

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContentFactoryEx
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import io.github.amitsidhpura.claudebrains.findVFile
import io.github.amitsidhpura.claudebrains.readLocked
import java.awt.Color
import java.awt.FlowLayout
import java.io.File
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shows Claude's proposed change as a real IntelliJ diff — Accept/Reject as prominent buttons
 * on a bar under the diff editor — and resolves (via the returned future) when the user
 * decides. Matches the VS Code host's openDiff contract — read from the installed extension
 * (2.1.222) and cross-checked against the CLI's consumer in the 2.1.226 binary, 2026-08-09:
 *   accept     -> ["FILE_SAVED", <final right-pane text>]  (the user may have edited the pane)
 *   reject     -> ["DIFF_REJECTED", <tab_name>]
 *   tab closed -> ["TAB_CLOSED"]
 *
 * One local EXTENSION on top of that contract: when the caller passes [acceptAllLabel] (the
 * permission flow, mirroring its panel card's suggestion button), the bar grows a third button
 * that resolves ["FILE_SAVED_ALL", <final right-pane text>]. The bridge flow never passes it,
 * so bridge verdicts stay exactly the reference set above.
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

    /** The `tab_name` each pending review was opened under — what `close_tab` names it by. */
    private val tabNames = ConcurrentHashMap<CompletableFuture<List<String>>, String>()

    /**
     * [readOnly] locks both panes. Both flows leave the right pane editable: the bridge flow's
     * final text travels back in the FILE_SAVED verdict, and since 2026-08-28 the permission flow
     * turns the same text into `updatedInput` (tweak-travel, checklist 3.5 — EditProposals).
     *
     * [current] is the left pane's text when the caller already read it. The permission flow
     * passes the text its proposal was built from, so the whole-file `old_string` it may send
     * back is exactly what the CLI will find on disk; null reads the file here (bridge flow).
     */
    fun open(
        oldPath: String,
        newContent: String,
        tabName: String,
        readOnly: Boolean = false,
        acceptAllLabel: String? = null,
        current: String? = null,
    ): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()
        pending.add(future)
        tabNames[future] = tabName
        val vf = findVFile(oldPath)
        val fileName = vf?.name ?: File(oldPath).name
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

        ApplicationManager.getApplication().invokeLater {
            val current = current ?: if (vf != null) {
                readLocked {
                    FileDocumentManager.getInstance().getDocument(vf)?.text
                        ?: String(vf.contentsToByteArray(), vf.charset)
                }
            } else ""

            val factory = DiffContentFactory.getInstance()
            // The right pane must be EDITABLE, and accept returns its FINAL text (the reference
            // sends the pane's getText(), user tweaks included). `DiffContentFactory.create(project,
            // text, type)` builds a READ-ONLY document — the pane showed a lock and "Failed to make
            // diff.md writable" on the first hand test of tweak-travel (2026-08-28), which also
            // means the bridge flow's pane had never been editable despite the comment above it.
            // `createEditable` is the platform's own editable variant (API since 2019, 242 ok).
            val proposed: DocumentContent = DiffContentFactoryEx.getInstanceEx().createEditable(project, newContent, fileType)
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
            // The verdict buttons ride a bar attached UNDER the diff editor via
            // addBottomComponent — the same slot the platform hangs its own below-editor strips
            // on, attached to the FileEditor so it survives viewer switches (side-by-side <->
            // unified) and is disposed with the tab. Chosen after three dead ends (2026-08-09,
            // chain in gotchas.md): toolbar CONTEXT_ACTIONS icons drowned among the standard
            // diff icons, toolbar TEXT buttons can't be had warning-free across 242→262, and
            // the NOTIFICATION_PROVIDERS banner renders only at the TOP (user wants bottom, no
            // prose, no tint). isDone guards make a late click a no-op, and however the review
            // ends the tab closes (closeWith), taking the bar with it — so a stale Accept can
            // never fire into a finished (or abandoned) call.
            if (!future.isDone) {
                val diffFile = ChainDiffVirtualFile(SimpleDiffRequestChain(request), title)
                files[future] = diffFile
                val fem = FileEditorManager.getInstance(project)
                fem.openFile(diffFile, true).forEach { editor ->
                    fem.addBottomComponent(editor, verdictBar(
                        onAccept = {
                            if (!future.isDone) {
                                future.complete(listOf("FILE_SAVED", readLocked { proposed.document.text }))
                            }
                        },
                        acceptAllLabel = acceptAllLabel,
                        onAcceptAll = {
                            if (!future.isDone) {
                                future.complete(listOf("FILE_SAVED_ALL", readLocked { proposed.document.text }))
                            }
                        },
                        onReject = {
                            if (!future.isDone) future.complete(listOf("DIFF_REJECTED", tabName))
                        },
                    ))
                }
            }
            closeWith(future)
        }
        return future
    }

    /**
     * "Files changed · Review" (3.6): one diff tab holding a CHAIN of requests — every file the
     * turn changed, baseline on the left (empty for a created file), the current file on the
     * right — navigable with the diff editor's own prev/next. Read-only, no verdict bar: this is
     * a look, not a decision. Opened the same way [open] does (our own ChainDiffVirtualFile) so it
     * can be found and closed like the review tabs.
     */
    fun openChain(title: String, pairs: List<io.github.amitsidhpura.claudebrains.TurnChanges.Pair>) {
        if (pairs.isEmpty()) return
        ApplicationManager.getApplication().invokeLater {
            val factory = DiffContentFactory.getInstance()
            val requests = pairs.map { p ->
                val name = File(p.path).name
                val fileType = FileTypeManager.getInstance().getFileTypeByFileName(name)
                val vf = findVFile(p.path)
                // Right pane: the live file when the VFS has it (so a later edit is reflected),
                // else the text read at turn end. Left: the baseline text, or nothing for a new file.
                val right = if (vf != null) factory.create(project, vf) else factory.create(project, p.after, fileType)
                val req = SimpleDiffRequest(
                    "$title — $name",
                    factory.create(project, p.before ?: "", fileType), right,
                    if (p.before == null) "New file" else "Before this turn", "Now",
                )
                req.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true)
                req
            }
            val diffFile = ChainDiffVirtualFile(SimpleDiffRequestChain(requests), title)
            FileEditorManager.getInstance(project).openFile(diffFile, true)
        }
    }

    /**
     * The bar hung under the diff editor: no prose, no tint (user's spec 2026-08-09) — just
     * Accept ✓ / Reject ✕ buttons, centered, separated from the diff by a themed hairline.
     * Both wear the panel card's button colours (chat.css: .ok --ok/--ok-hover for Accept,
     * .no #3a3d41/#474a4f/--fg for Reject — mirrored here because Swing can't read CSS
     * tokens; keep in sync) AND its exact glyphs: /icons/accept.svg + reject.svg are the
     * card's own SVG_CHECK / SVG_X from chat.html, bundled because the closest platform
     * icons don't match (Actions.Commit is the VCS -o- glyph in the new UI, Actions.Cancel's
     * ✕ differs from the card's — user screenshots 2026-08-09). [acceptAllLabel] non-null adds
     * the card's suggestion button between them — .alt is "styled exactly like Accept, only
     * the double-check icon tells them apart" (chat.css), so it shares Accept's colours and
     * wears SVG_CHECKS (accept-all.svg). Button order matches cardBtns(): ok, alt, no.
     */
    private fun verdictBar(
        onAccept: () -> Unit,
        acceptAllLabel: String? = null,
        onAcceptAll: () -> Unit = {},
        onReject: () -> Unit,
    ): JComponent {
        val bar = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(6), JBUI.scale(5)))
        bar.border = JBUI.Borders.customLineTop(JBColor.border())
        bar.add(cardButton("Accept", IconLoader.getIcon("/icons/accept.svg", DiffReview::class.java),
            base = Color(0x0D542B), hover = Color(0x146E3A), text = Color.WHITE, onClick = onAccept))
        acceptAllLabel?.let { label ->
            bar.add(cardButton(label, IconLoader.getIcon("/icons/accept-all.svg", DiffReview::class.java),
                base = Color(0x0D542B), hover = Color(0x146E3A), text = Color.WHITE, onClick = onAcceptAll))
        }
        bar.add(cardButton("Reject", IconLoader.getIcon("/icons/reject.svg", DiffReview::class.java),
            base = Color(0x3A3D41), hover = Color(0x474A4F), text = Color(0xD5D8DD), onClick = onReject))
        return bar
    }

    /**
     * A JButton in the panel card's clothes. Colored via the "JButton.*" client properties,
     * which DarculaButtonUI/DarculaButtonPainter honor on both 242 and 262 (checked in
     * bytecode) — a plain setBackground() is IGNORED by the LAF. Hover is hand-rolled off the
     * button model's rollover state, matching the card's :hover swap.
     */
    private fun cardButton(
        label: String,
        icon: javax.swing.Icon,
        base: Color,
        hover: Color,
        text: Color,
        onClick: () -> Unit,
    ): JButton = JButton(label, icon).apply {
        putClientProperty("JButton.backgroundColor", base)
        putClientProperty("JButton.borderColor", base)
        putClientProperty("JButton.textColor", text)
        model.addChangeListener {
            putClientProperty("JButton.backgroundColor", if (model.isRollover) hover else base)
        }
        addActionListener { onClick() }
    }

    /**
     * closeAllDiffTabs: every undecided review resolves TAB_CLOSED, as the reference's own close
     * path does. Returns how many were still open, for the reference's `CLOSED_<n>_DIFF_TABS` reply.
     */
    fun completeAllTabClosed(): Int {
        val open = pending.toList()
        open.forEach { it.complete(listOf("TAB_CLOSED")) }
        return open.size
    }

    /**
     * close_tab: only the review opened under [tabName] resolves TAB_CLOSED — the reference
     * closes the one tab whose label equals `tab_name` and leaves the rest alone. Matched on the
     * name the caller passed to [open], the same string it hands back here. Returns whether one
     * was found; the reply is TAB_CLOSED either way, as in the reference.
     */
    fun completeTabClosed(tabName: String): Boolean {
        val hits = tabNames.filterValues { it == tabName }.keys
        hits.forEach { it.complete(listOf("TAB_CLOSED")) }
        return hits.isNotEmpty()
    }

    /** The caller is gone (socket closed, server shutdown): unblock the dispatch threads and clean up. */
    fun cancelAll() {
        pending.forEach { it.cancel(true) }
    }

    /**
     * The question was answered somewhere else (the panel card, in the permission flow): this
     * review's verdict can no longer matter. Cancelling routes through the same completion hook
     * as every other ending, which closes the diff tab.
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
    private fun closeWith(future: CompletableFuture<List<String>>) {
        future.whenComplete { _, _ ->
            pending.remove(future)
            tabNames.remove(future)
            val diffFile = files.remove(future)
            ApplicationManager.getApplication().invokeLater {
                diffFile?.let { FileEditorManager.getInstance(project).closeFile(it) }
            }
        }
    }
}
