package io.github.amitsidhpura.claudebrains

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/**
 * VFS lookup with the path spelling the CLI uses normalized (Windows backslashes -> forward
 * slashes, which findFileByPath requires). The one shared front door for every path that
 * arrives from the CLI, the webview or a transcript.
 */
fun findVFile(path: String): VirtualFile? =
    LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/'))

/**
 * Make the IDE's picture of [path] match what is on disk, after the CLI wrote it out of band.
 *
 * The plugin never writes files itself — the CLI does, behind the IDE's back — and nothing was
 * refreshing afterwards, so an accepted edit still needed "Reload from disk" to appear in an open
 * editor and a newly created file never showed up in the project tree (verified 2026-08-09: no
 * refresh call anywhere in the plugin; the sandbox has no native file watcher, and frame-activation
 * sync never fires while the user stays inside the IDE).
 *
 * ASYNC refresh, deliberately: this is called from the CLI's reader thread, where a synchronous
 * refresh is both slower and more constrained. The VFS events it fires are what update open editors
 * and the project tree.
 *
 * Two cases, and the second is the one that is easy to miss:
 *  - the file is already known to the VFS (an EDIT) -> refresh it and open editors reload;
 *  - it is not (an ADD) -> refreshing the file itself is impossible, because there is nothing to
 *    refresh. The VFS only discovers a new child when its PARENT is refreshed, so walk up to the
 *    nearest directory it already knows. If that meant climbing past directories the CLI also
 *    created, the refresh has to be recursive or the new file inside them stays invisible.
 */
fun refreshFromDisk(path: String) {
    val norm = path.replace('\\', '/').trimEnd('/')
    if (norm.isEmpty()) return
    // A DIRECTORY is only ever passed here by the end-of-turn sweep, which wants everything under
    // it; a file wants only itself.
    findVFile(norm)?.let { it.refresh(true, it.isDirectory); return }

    val parent = norm.substringBeforeLast('/', "")
    var dir = parent
    while (dir.isNotEmpty()) {
        val vf = findVFile(dir)
        if (vf != null) {
            // Recursive only when we had to climb: the CLI created intermediate directories too.
            vf.refresh(true, dir != parent)
            return
        }
        val up = dir.substringBeforeLast('/', "")
        if (up == dir) return   // hit the root without finding anything known
        dir = up
    }
}
