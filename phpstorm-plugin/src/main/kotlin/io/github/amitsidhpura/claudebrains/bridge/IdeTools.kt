package io.github.amitsidhpura.claudebrains.bridge

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import io.github.amitsidhpura.claudebrains.findVFile
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
 */
class IdeTools(private val project: Project) {

    private val diffReview = DiffReview(project)

    fun call(name: String, args: JsonObject): ToolResult = when (name) {
        "getWorkspaceFolders" -> one(getWorkspaceFolders())
        "getOpenEditors" -> one(getOpenEditors())
        "getCurrentSelection", "getLatestSelection" -> one(getCurrentSelection())
        "openFile" -> one(openFile(args.str("filePath") ?: args.str("path").orEmpty()))
        "saveDocument" -> one(saveDocument(args.str("filePath") ?: args.str("path").orEmpty()))
        "checkDocumentDirty" -> one(checkDocumentDirty(args.str("filePath") ?: args.str("path").orEmpty()))
        "openDiff" -> openDiff(args)
        "closeAllDiffTabs" -> one(closeAllDiffTabs())
        "close_tab" -> one(closeAllDiffTabs()) // best-effort: close diff tabs
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

    private fun getWorkspaceFolders(): String = ReadAction.compute<String, RuntimeException> {
        val roots = ProjectRootManager.getInstance(project).contentRoots.map { it.path }
        (listOfNotNull(project.basePath) + roots).distinct().joinToString("\n")
            .ifEmpty { "(no workspace folders)" }
    }

    private fun getOpenEditors(): String = ReadAction.compute<String, RuntimeException> {
        FileEditorManager.getInstance(project).openFiles.joinToString("\n") { it.path }
            .ifEmpty { "(no open editors)" }
    }

    private fun getCurrentSelection(): String = ReadAction.compute<String, RuntimeException> {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
            ?: return@compute "(no active editor)"
        val sel = editor.selectionModel.selectedText ?: ""
        val file = FileDocumentManager.getInstance().getFile(editor.document)?.path ?: "?"
        "file: $file\n$sel"
    }

    private fun openFile(rawPath: String): String {
        if (rawPath.isBlank()) return "error: missing filePath"
        val vf = findVFile(rawPath)
            ?: return "error: file not found: $rawPath"
        ApplicationManager.getApplication().invokeLater {
            OpenFileDescriptor(project, vf).navigate(true)
        }
        return "opened: $rawPath"
    }

    private fun saveDocument(rawPath: String): String {
        val vf = findVFile(rawPath)
            ?: return "error: file not found: $rawPath"
        ApplicationManager.getApplication().invokeLater {
            val fdm = FileDocumentManager.getInstance()
            fdm.getDocument(vf)?.let { fdm.saveDocument(it) }
        }
        return "saved: $rawPath"
    }

    private fun checkDocumentDirty(rawPath: String): String = ReadAction.compute<String, RuntimeException> {
        val vf = findVFile(rawPath)
            ?: return@compute "error: file not found: $rawPath"
        val doc = FileDocumentManager.getInstance().getDocument(vf)
        "dirty: ${doc != null && FileDocumentManager.getInstance().isDocumentUnsaved(doc)}"
    }

    private fun closeAllDiffTabs(): String {
        ApplicationManager.getApplication().invokeLater {
            val fem = FileEditorManager.getInstance(project)
            fem.openFiles
                .filter { it.javaClass.name.contains("Diff") }
                .forEach { fem.closeFile(it) }
        }
        return "closed diff tabs"
    }

    private fun getDiagnostics(pathOrUri: String?): String = ReadAction.compute<String, RuntimeException> {
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
                val infos = DaemonCodeAnalyzerImpl.getHighlights(doc, HighlightSeverity.WARNING, project)
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
            spec("getWorkspaceFolders", "Get all workspace folders currently open in the IDE"),
            spec("getOpenEditors", "Get information about currently open editors"),
            spec("getCurrentSelection", "Get the current text selection in the active editor"),
            spec("getLatestSelection", "Get the most recent text selection"),
            spec("openFile", "Open a file in the editor", "filePath"),
            spec("saveDocument", "Save a document with unsaved changes", "filePath"),
            spec("checkDocumentDirty", "Check if a document has unsaved changes", "filePath"),
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
