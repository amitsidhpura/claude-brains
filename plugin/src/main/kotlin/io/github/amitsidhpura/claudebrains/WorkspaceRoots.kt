package io.github.amitsidhpura.claudebrains

/**
 * Which project content roots the CLI must be told about beyond its working directory
 * (checklist 2.12). Pure, so the rule can be pinned without a Project.
 *
 * The CLI's file tools refuse paths outside its working directories: a Read of an attached
 * module or a monorepo parent asks with `decision_reason` "Path is outside allowed working
 * directories" on EVERY touch (measured 2026-09-05, 2.1.260). `--add-dir <path>` makes a directory
 * a working directory — the same Read then runs with no ask at all (measured, same day). VS Code
 * passes every workspace folder that is not the cwd (extension.js `Yg$`); a JetBrains project's
 * equivalent is `ProjectRootManager.contentRoots`, of which the ones INSIDE the base directory
 * are already covered by the cwd and the ones outside it are what the IDE shows in the tree but
 * the CLI cannot reach.
 */
object WorkspaceRoots {
    /**
     * Content roots that lie outside [basePath]: not the base itself and not nested under it.
     * A root nested under another extra root is dropped (its parent covers it). Order is kept,
     * duplicates removed, separators normalised to `/`, trailing slashes trimmed.
     */
    fun extraDirs(basePath: String?, contentRoots: List<String>): List<String> {
        val base = basePath?.let(::norm)
        val outside = contentRoots.map(::norm).distinct()
            .filter { base == null || !(it == base || it.startsWith("$base/")) }
        return outside.filter { r -> outside.none { o -> o != r && r.startsWith("$o/") } }
    }

    private fun norm(p: String): String = p.replace('\\', '/').trimEnd('/').ifEmpty { "/" }
}
