package io.github.amitsidhpura.claudebrains

import io.github.amitsidhpura.claudebrains.ui.WebviewAssets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 1.34: the vendored KaTeX as the page receives it (WebviewAssets.vendorJs / vendorCss). */
class WebviewVendorTest {
    @Test fun `every font is inlined and no file url is left`() {
        val css = WebviewAssets.vendorCss()
        assertEquals(20, Regex("""url\(data:font/woff2;base64,[A-Za-z0-9+/=]+\)""").findAll(css).count(),
            "KaTeX ships 20 font faces; each must reach the page as a data: URI (loadHTML has no base URL)")
        assertFalse(css.contains("url(fonts/"), "a font still points at a file the page cannot load")
        assertEquals(20, Regex("""@font-face""").findAll(css).count())
        assertTrue(css.contains(".katex-display"), "not KaTeX's stylesheet?")
    }

    @Test fun `neither block can close its own tag early`() {
        // the same failure mode RenderLimitsTest guards for our own sources: a stray closer
        // truncates the spliced block and takes the page with it
        assertFalse(WebviewAssets.vendorJs().contains("</script", ignoreCase = true))
        assertFalse(WebviewAssets.vendorCss().contains("</style", ignoreCase = true))
    }

    @Test fun `the page carries both markers, vendor before ours, and the licence ships`() {
        val page = WebviewAssets.page()
        val vCss = page.indexOf("<!--VENDOR_CSS-->"); val css = page.indexOf("<!--CSS-->")
        assertTrue(vCss in 0 until css, "vendor styles must come before ours so ours can override")
        val vJs = page.indexOf("<!--VENDOR_JS-->"); val js = page.indexOf("/* ===== file: 00-core.js")
        assertTrue(vJs in 0 until js, "the library must be defined before the panel's script runs")
        assertNotNull(javaClass.getResource("/webview/vendor/katex/LICENSE"), "KaTeX's MIT licence must ship with it")
        assertTrue(WebviewAssets.vendorJs().contains("katex"), "not KaTeX's script?")
    }
}
