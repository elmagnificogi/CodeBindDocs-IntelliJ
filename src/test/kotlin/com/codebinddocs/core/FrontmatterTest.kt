package com.codebinddocs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrontmatterTest {
    @Test
    fun splitMarkdownExtractsHeaderAndBody() {
        val md = """
            ---
            cbd:
              target: src/a.ts
              kind: file
            ---
            # Hello

            body
        """.trimIndent() + "\n"
        val (header, body) = splitMarkdown(md)
        assertTrue(header!!.startsWith("---"))
        assertTrue(body.startsWith("# Hello"))
    }

    @Test
    fun splitMarkdownStripsBomAndNestedFrontmatter() {
        val md = "\uFEFF---\ncbd:\n  target: src/a.ts\n  kind: file\n---\n---\ncbd:\n  target: src/a.ts\n  kind: file\n---\n# X\n"
        val body = splitMarkdown(md).body
        assertFalse(body.startsWith("---"))
        assertTrue(body.contains("# X"))
    }

    @Test
    fun parseRangeFields() {
        val md = """
            ---
            cbd:
              target: src/foo.ts
              kind: range
              startLine: 10
              endLine: 20
              symbol: bar
              contentHash: abc123
            ---
            # Doc
        """.trimIndent() + "\n"
        val (meta, body) = parseCbdFrontmatter(md)
        assertEquals("src/foo.ts", meta!!.target)
        assertEquals(BindingKind.RANGE, meta.kind)
        assertEquals(10, meta.startLine)
        assertEquals(20, meta.endLine)
        assertEquals("bar", meta.symbol)
        assertEquals("abc123", meta.contentHash)
        assertTrue(body.startsWith("# Doc"))
    }

    @Test
    fun serializeParseRoundTrip() {
        val md = serializeCbdFrontmatter(
            CbdFrontmatter(target = "src/x.ts", kind = BindingKind.FILE, symbol = "activate", contentHash = "deadbeef"),
            "# Title\n\ntext\n",
        )
        val meta = parseCbdFrontmatter(md).meta!!
        assertEquals("src/x.ts", meta.target)
        assertEquals("activate", meta.symbol)
        assertEquals("deadbeef", meta.contentHash)
    }

    @Test
    fun directoryKindRoundTrip() {
        val md = serializeCbdFrontmatter(CbdFrontmatter(target = "src/util", kind = BindingKind.DIRECTORY), "# Dir doc\n")
        val meta = parseCbdFrontmatter(md).meta!!
        assertEquals(BindingKind.DIRECTORY, meta.kind)
        assertNull(meta.startLine)
        assertNull(meta.endLine)
    }

    @Test
    fun bindingFrontmatterRoundTrip() {
        val binding = Binding(
            id = "docs/x.md",
            doc = "docs/x.md",
            target = BindingTarget("src/x.ts", BindingKind.RANGE, 1, 5),
            anchors = mutableListOf(BindingAnchor(symbol = "foo", contentHash = "h1")),
        )
        val again = frontmatterToBinding("docs/x.md", bindingToFrontmatter(binding))
        assertEquals("src/x.ts", again.target.path)
        assertEquals(BindingKind.RANGE, again.target.kind)
        assertEquals(1, again.target.startLine)
        assertEquals("foo", again.anchors[0].symbol)
    }
}
