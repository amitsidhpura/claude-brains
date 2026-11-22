package io.github.amitsidhpura.claudebrains

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

/** 15.7: the trust record's read rule. The config shape is the CLI's own (`~/.claude.json`, read 2026-11-22). */
class WorkspaceTrustTest {
    private val cfg = """{"numStartups":3,"projects":{
        "/home/u/Sites/app":{"hasTrustDialogAccepted":true,"lastSessionId":"x"},
        "/home/u/Sites/app-testing":{"hasTrustDialogAccepted":false},
        "/home/u/work":{"hasTrustDialogAccepted":true},
        "D:/sites/app":{"hasTrustDialogAccepted":true},
        "/home/u/odd":{"allowedTools":[]}}}"""

    @Test fun `a folder with the flag is trusted, one without is not`() {
        assertEquals(true, WorkspaceTrust.isTrusted(cfg, "/home/u/Sites/app"))
        assertEquals(false, WorkspaceTrust.isTrusted(cfg, "/home/u/Sites/app-testing"))
        assertEquals(false, WorkspaceTrust.isTrusted(cfg, "/home/u/odd"))
        assertEquals(false, WorkspaceTrust.isTrusted(cfg, "/home/u/never-seen"))
    }

    @Test fun `a trusted folder above the key counts for nothing`() {
        // measured 2026-11-22 on 2.1.330: ~/Sites trusted, a repository under it still untrusted
        assertEquals(false, WorkspaceTrust.isTrusted(cfg, "/home/u/work/a/b"))
        assertEquals(false, WorkspaceTrust.isTrusted(cfg, "/home/u/Sites/app-other"))
        assertEquals(true, WorkspaceTrust.isTrusted(cfg, "/home/u/Sites/app/"))
    }

    @Test fun `a folder inside a trusted repository is trusted through its git root`() {
        val git = setOf(File("/home/u/Sites/app").absoluteFile)
        val key = WorkspaceTrust.attestDir(File("/home/u/Sites/app/plugin/src")) { it in git }
        assertEquals(true, WorkspaceTrust.isTrusted(cfg, key.path))
    }

    @Test fun `windows separators match the config's forward slashes`() {
        assertEquals(true, WorkspaceTrust.isTrusted(cfg, "D:\\sites\\app\\"))
        assertEquals(false, WorkspaceTrust.isTrusted(cfg, "D:\\sites\\other"))
    }

    @Test fun `a config that cannot say gives null`() {
        assertNull(WorkspaceTrust.isTrusted(null, "/x"))
        assertNull(WorkspaceTrust.isTrusted("not json", "/x"))
        assertNull(WorkspaceTrust.isTrusted("""{"numStartups":1}""", "/x"))
        assertNull(WorkspaceTrust.isTrusted("""{"projects":[]}""", "/x"))
    }

    @Test fun `the attestation names the git root, else the folder itself`() {
        val git = setOf(File("/home/u/Sites/app").absoluteFile)
        assertEquals(File("/home/u/Sites/app").absoluteFile,
            WorkspaceTrust.attestDir(File("/home/u/Sites/app/plugin/src")) { it in git })
        assertEquals(File("/home/u/plain").absoluteFile,
            WorkspaceTrust.attestDir(File("/home/u/plain")) { it in git })
    }

    @Test fun `the config file follows CLAUDE_CONFIG_DIR`() {
        assertEquals(File("/home/u/.claude.json"), WorkspaceTrust.configFile({ null }, "/home/u"))
        assertEquals(File("/scratch/cfg/.claude.json"),
            WorkspaceTrust.configFile({ if (it == "CLAUDE_CONFIG_DIR") "/scratch/cfg" else null }, "/home/u"))
    }

    @Test fun `display folds the home prefix`() {
        assertEquals("~/Sites/app", WorkspaceTrust.display("/home/u/Sites/app", "/home/u"))
        assertEquals("~", WorkspaceTrust.display("/home/u", "/home/u"))
        assertEquals("/home/user2/x", WorkspaceTrust.display("/home/user2/x", "/home/u"))
    }
}
