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
