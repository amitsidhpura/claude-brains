package io.github.amitsidhpura.claudebrains.bridge

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import io.github.amitsidhpura.claudebrains.findVFile
import io.github.amitsidhpura.claudebrains.findVFileOnDisk
import io.github.amitsidhpura.claudebrains.readLocked
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A tool result is one or more text parts (openDiff returns two: status + content). */
data class ToolResult(val texts: List<String>)

/**
 * IntelliJ-backed implementations of the IDE tools the CLI calls.
 *
 * Six tools the VS Code extension once served are gone since 2026-11-22 (backlog #62; VS Code
 * dropped them at 2.1.330): getWorkspaceFolders, getOpenEditors, getCurrentSelection,
 * getLatestSelection, saveDocument, checkDocumentDirty. Neither the oldest CLI the panel supports
 * (2.1.200) nor 2.1.330 names any of them in its binary, and the model is only ever offered
 * getDiagnostics / executeCode from this server. `openFile` STAYS although VS Code dropped it
 * too: the 2.1.200 binary still calls it (2.1.330 does not).
 */
class IdeTools(private val project: Project) {

    private val diffReview = DiffReview(project)

    fun call(name: String, args: JsonObject): ToolResult = when (name) {
        "openFile" -> one(openFile(args.str("filePath") ?: args.str("path").orEmpty()))
        "openDiff" -> openDiff(args)
        "closeAllDiffTabs" -> one(closeAllDiffTabs())
        "close_tab" -> one(closeTab(args.str("tab_name").orEmpty()))
        // (both close tools also resolve the reviews still waiting — see closeAllDiffTabs)
        "getDiagnostics" -> one(getDiagnostics(args.str("uri")))
        "executeCode" -> one("TODO: executeCode (Jupyter) not implemented")
        else -> throw IllegalArgumentException("Unknown tool: $name")
    }

    private fun openDiff(args: JsonObject): ToolResult {
        val oldPath = args.str("old_file_path") ?: args.str("new_file_path").orEmpty()
        val newContent = args.str("new_file_contents").orEmpty()
        val tabName = args.str("tab_name") ?: oldPath
        // Blocks until the user accepts/rejects; runs on a tool-dispatch thread, not the EDT.
        return ToolResult(diffReview.open(oldPath, newContent, tabName).get())
    }

    private fun openFile(rawPath: String): String {
        if (rawPath.isBlank()) return "error: missing filePath"
        // Disk-consulting lookup: the CLI routinely asks to open a file it created moments ago,
        // which the VFS snapshot has not seen yet (Vfs.kt). Runs on the WebSocket thread, no
        // read lock held — unlike getDiagnostics below, which must not refresh.
        val vf = findVFileOnDisk(rawPath)
            ?: return "error: file not found: $rawPath"
        ApplicationManager.getApplication().invokeLater {
            OpenFileDescriptor(project, vf).navigate(true)
        }
        return "opened: $rawPath"
    }

    private fun closeAllDiffTabs(): String {
        // Resolving the reviews TAB_CLOSED closes their tabs through each review's completion
        // hook, which holds the diff file handle from creation. That handle is the ONLY close
        // that works: FileEditorManager.openFiles does not report diff editors (measured live
        // 2026-08-09 — a visible diff tab alongside an empty openFiles), so the old
        // find-by-class-name filter here had been closing nothing. Every diff we ever open is
        // a tracked review; other plugins' diffs are not ours to close.
        val n = diffReview.completeAllTabClosed()
        return "CLOSED_${n}_DIFF_TABS" // the reference's exact reply shape
    }

    /**
     * Closes ONE review, by the `tab_name` its openDiff was given — the reference closes the tab
     * whose label equals the name and replies TAB_CLOSED whether or not it found one. Every tab
     * the CLI can name is a diff we opened (it never learns other tab titles), so an unknown name
     * is a no-op, not an error. Used to sweep every diff tab; with two reviews open that resolved
     * both from one close.
     */
    private fun closeTab(tabName: String): String {
        diffReview.completeTabClosed(tabName)
        return "TAB_CLOSED"
    }

    /** The WS client vanished: nothing can consume a verdict, so unblock any waiting openDiff. */
    fun cancelPendingReviews() = diffReview.cancelAll()

    private fun getDiagnostics(pathOrUri: String?): String = readLocked {
        val fem = FileEditorManager.getInstance(project)
        val files = if (pathOrUri.isNullOrBlank()) {
            fem.openFiles.toList()
        } else {
            val p = pathOrUri.removePrefix("file://").replace('\\', '/')
            listOfNotNull(findVFile(p))
        }
        val arr = buildJsonArray {
            for (vf in files) {
                val doc = FileDocumentManager.getInstance().getDocument(vf) ?: continue
                // Same data DaemonCodeAnalyzerImpl.getHighlights returns, via public API:
                // the daemon publishes its results into the document's markup model.
                val infos = DocumentMarkupModel.forDocument(doc, project, false)
                    ?.allHighlighters.orEmpty()
                    .mapNotNull { HighlightInfo.fromRangeHighlighter(it) }
                    .filter { it.severity >= HighlightSeverity.WARNING }
                    .sortedBy { it.startOffset }
                add(buildJsonObject {
                    put("uri", vf.url)
                    put("diagnostics", buildJsonArray {
                        infos.forEach { hi ->
                            add(buildJsonObject {
                                put("message", hi.description ?: "")
                                put("severity", hi.severity.toString())
                                put("line", doc.getLineNumber(hi.startOffset))
                                put("startOffset", hi.startOffset)
                                put("endOffset", hi.endOffset)
                            })
                        }
                    })
                })
            }
        }
        Json.encodeToString(JsonArray.serializer(), arr)
    }

    private fun one(s: String) = ToolResult(listOf(s))

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    companion object {
        /** tools/list specs. */
        val SPECS: List<JsonObject> = listOf(
            spec("openFile", "Open a file in the editor", "filePath"),
            spec("openDiff", "Open a diff for the file and wait for accept/reject",
                "old_file_path", "new_file_path", "new_file_contents", "tab_name"),
            spec("closeAllDiffTabs", "Close all diff tabs in the editor"),
            spec("close_tab", "Close a tab by name", "tab_name"),
            spec("getDiagnostics", "Get language diagnostics from the IDE", "uri"),
        )

        private fun spec(name: String, description: String, vararg stringProps: String): JsonObject =
            buildJsonObject {
                put("name", name)
                put("description", description)
                put("inputSchema", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        stringProps.forEach { p -> put(p, buildJsonObject { put("type", "string") }) }
                    })
                })
            }
    }
}
