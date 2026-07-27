package com.syncroze.claudecode.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.syncroze.claudecode.ClaudeSessionService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.nio.charset.StandardCharsets
import javax.swing.JComponent

/**
 * JCEF host for the chat UI. Bridges the web UI to the session service.
 *
 * JS -> Kotlin: a single `window.__bridge(json)` channel carrying `{kind:"user"|"perm", ...}`.
 * Kotlin -> JS: `window.onClaudeEvent(rawStreamJsonLine)` — including synthetic
 * `{type:"permission_request"}` frames for tool approvals.
 */
class ChatPanel(project: Project, parent: Disposable) {

    private val browser = JBCefBrowser()
    private val jsToKotlin = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val session = project.getService(ClaudeSessionService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    val component: JComponent get() = browser.component

    init {
        Disposer.register(parent, browser)

        jsToKotlin.addHandler { raw ->
            handleFromWeb(raw)
            null
        }

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                browser.cefBrowser.executeJavaScript(
                    "window.__bridge = function(p){ ${jsToKotlin.inject("p")} };",
                    browser.cefBrowser.url, 0,
                )
                startSession()
            }
        }, browser.cefBrowser)

        loadUi()
    }

    private fun handleFromWeb(raw: String) {
        val msg = runCatching { json.parseToJsonElement(raw) as JsonObject }.getOrNull() ?: return
        when (msg["kind"]?.jsonPrimitive?.content) {
            "user" -> {
                val text = msg["text"]?.jsonPrimitive?.content ?: ""
                val id = msg["id"]?.jsonPrimitive?.content
                val images = (msg["images"] as? JsonArray)?.mapNotNull { e ->
                    val o = e.jsonObject
                    val mt = o["media_type"]?.jsonPrimitive?.content
                    val data = o["data"]?.jsonPrimitive?.content
                    if (mt != null && data != null) mt to data else null
                } ?: emptyList()
                session.sendUser(text, images, id)
            }
            "rewind" -> msg["id"]?.jsonPrimitive?.content?.let {
                session.rewind(it, msg["dry"]?.jsonPrimitive?.content == "true")
            }
            "perm" -> {
                val id = msg["id"]?.jsonPrimitive?.content ?: return
                val allow = msg["allow"]?.jsonPrimitive?.content == "true"
                session.respondPermission(id, allow)
            }
            "answer" -> {
                val id = msg["id"]?.jsonPrimitive?.content ?: return
                session.answerQuestion(id, msg["answers"]?.jsonPrimitive?.content ?: "{}")
            }
            "mode" -> msg["mode"]?.jsonPrimitive?.content?.let { session.setPermissionMode(it) }
            "model" -> msg["model"]?.jsonPrimitive?.content?.let { session.setModel(it) }
            "stop" -> session.interrupt()
            "new" -> { clearLog(); session.newConversation() }
            "resume" -> msg["id"]?.jsonPrimitive?.content?.let { id ->
                clearLog(); pushTranscript(id); session.resumeSession(id)
            }
            "history" -> pushSessions()
        }
    }

    private fun frameOf(type: String, items: JsonElement): String =
        json.encodeToString(JsonObject.serializer(), buildJsonObject { put("type", type); put("items", items) })

    private fun clearLog() = pushEvent("""{"type":"__clear"}""")

    private fun pushTranscript(id: String) {
        val frame = buildJsonObject {
            put("type", "__transcript")
            put("items", buildJsonArray {
                session.readTranscript(id).forEach { t ->
                    add(buildJsonObject { put("role", t.role); put("text", t.text) })
                }
            })
        }
        pushEvent(json.encodeToString(JsonObject.serializer(), frame))
    }

    private fun pushSessions() {
        val frame = buildJsonObject {
            put("type", "sessions")
            put("items", buildJsonArray {
                session.listSessions().forEach { s ->
                    add(buildJsonObject {
                        put("id", s.id)
                        put("title", s.title)
                        put("time", s.lastModified)
                    })
                }
            })
        }
        pushEvent(json.encodeToString(JsonObject.serializer(), frame))
    }

    private fun loadUi() {
        val html = javaClass.getResourceAsStream("/webview/chat.html")!!
            .readBytes().toString(StandardCharsets.UTF_8)
        browser.loadHTML(html)
    }

    private var started = false
    private fun startSession() {
        if (started) return
        started = true
        session.start(
            onEvent = { line -> pushEvent(line) },
            onPermission = { requestId, toolName, inputJson ->
                val frame = buildJsonObject {
                    put("type", "permission_request")
                    put("id", requestId)
                    put("tool", toolName)
                    put("input", inputJson) // raw JSON string; the page parses/pretty-prints
                }
                pushEvent(json.encodeToString(JsonObject.serializer(), frame))
            },
            onInit = { metaJson ->
                val meta = runCatching { json.parseToJsonElement(metaJson).jsonObject }.getOrNull()
                if (meta != null) {
                    meta["commands"]?.let { pushEvent(frameOf("__commands", it)) }
                    meta["models"]?.let { models ->
                        val frame = buildJsonObject {
                            put("type", "__models"); put("items", models)
                            session.selectedModel()?.let { put("selected", it) }
                        }
                        pushEvent(json.encodeToString(JsonObject.serializer(), frame))
                    }
                }
            },
            onExit = { code -> pushEvent("""{"type":"__exit","code":$code}""") },
        )

        // Feed the file list for @-mention autocomplete.
        runCatching {
            val files = session.listProjectFiles()
            val frame = buildJsonObject {
                put("type", "files")
                put("items", buildJsonArray { files.forEach { add(JsonPrimitive(it)) } })
            }
            pushEvent(json.encodeToString(JsonObject.serializer(), frame))
        }
    }

    /** Deliver a raw stream-json line to the page's window.onClaudeEvent(json). */
    private fun pushEvent(jsonLine: String) {
        // Encode as a fully-escaped JS string literal (robust for large/complex payloads like init).
        val jsArg = JsonPrimitive(jsonLine).toString()
        ApplicationManager.getApplication().invokeLater {
            browser.cefBrowser.executeJavaScript(
                "window.onClaudeEvent && window.onClaudeEvent($jsArg);",
                browser.cefBrowser.url, 0,
            )
        }
    }
}
