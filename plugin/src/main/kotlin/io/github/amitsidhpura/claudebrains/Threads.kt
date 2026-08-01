package io.github.amitsidhpura.claudebrains

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable

/**
 * Blocking read action for our background/WS-handler threads. `Application.runReadAction` is
 * the ONE blocking-read API that is non-deprecated on every supported IDE (242 → 262, checked
 * in the platform bytecode): `ReadAction.compute` and Kotlin's `runReadAction {}` (ActionsKt)
 * are both deprecated as of 2026.1, and their replacements (`runReadActionBlocking`, suspend
 * `readAction`) don't exist on the 242 baseline.
 */
fun <T> readLocked(block: () -> T): T =
    ApplicationManager.getApplication().runReadAction(Computable(block))
