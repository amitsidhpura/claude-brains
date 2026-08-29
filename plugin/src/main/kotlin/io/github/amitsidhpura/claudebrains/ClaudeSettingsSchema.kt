package io.github.amitsidhpura.claudebrains

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.remote.JsonFileResolver

/**
 * JSON schema for Claude Code's settings files (checklist 13.2): completion, hover docs and
 * validation while editing `.claude/settings.json` / `.claude/settings.local.json` by hand — the
 * files the permission cards' "don't ask again" choices write into.
 *
 * NOTHING IS BUNDLED. Anthropic publishes the schema through SchemaStore
 * (`https://json.schemastore.org/claude-code-settings.json`, the `$schema` their docs recommend),
 * and the IDE downloads and caches it like any other remote schema. That keeps the
 * never-redistribute rule intact and means a CLI release that adds keys needs no plugin release.
 *
 * WHY A PROVIDER AT ALL: the IDE already consults the SchemaStore catalog, but its entry matches
 * only the shared `.claude/settings.json` (fileMatch checked 2026-08-29). `settings.local.json` — the file the CLI
 * itself writes rules into, and the one edited most — gets nothing. This provider names both, and
 * still works when the user has turned the catalog off.
 *
 * Packaging: on the 2024.2 baseline these classes live in the platform core; 2024.3+ moved them
 * into the bundled `com.intellij.modules.json` plugin, hence the optional depends in plugin.xml
 * (same pattern as JCEF).
 */
class ClaudeSettingsSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(Provider())

    private class Provider : JsonSchemaFileProvider {
        override fun isAvailable(file: VirtualFile): Boolean = matches(file.name, file.parent?.name)
        override fun getName(): String = NAME
        override fun getSchemaFile(): VirtualFile? = JsonFileResolver.urlToFile(URL)
        override fun getSchemaType(): SchemaType = SchemaType.remoteSchema
        override fun getRemoteSource(): String = URL
        override fun isUserVisible(): Boolean = true
    }

    companion object {
        const val URL = "https://json.schemastore.org/claude-code-settings.json"
        const val NAME = "Claude Code settings"

        /** The two settings files, only when they sit in a `.claude` directory (project or user). */
        fun matches(fileName: String, parentName: String?): Boolean =
            parentName == ".claude" && (fileName == "settings.json" || fileName == "settings.local.json")
    }
}
