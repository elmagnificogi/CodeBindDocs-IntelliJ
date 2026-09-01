package com.codebinddocs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiscCoreTest {
    @Test
    fun planMovesDetectsConflicts() {
        val plan = planMoves(listOf("docs/a.md", "docs/b.md"), "docs", "notes") { it == "notes/b.md" }
        assertEquals(1, plan.moves.size)
        assertEquals("docs/a.md" to "notes/a.md", plan.moves[0])
        assertEquals(listOf("notes/b.md"), plan.conflicts)
    }

    @Test
    fun embedCollapseRoundTrip() {
        val expanded = """
            ```cbd-include-view
            doc: docs/cbd/foo.md
            heading: 概述

            > **嵌入（只读）** `docs/cbd/foo.md`
            > hello
            ```
        """.trimIndent()
        val compact = collapseDocIncludes(expanded)
        assertTrue(compact.contains("```cbd-include"))
        assertTrue(compact.contains("doc: docs/cbd/foo.md"))
        assertTrue(compact.contains("heading: 概述"))
        val spec = parseIncludeMeta("doc: docs/cbd/foo.md\nheading: 概述\nlines: 2-4")!!
        assertEquals(2, spec.startLine)
        assertEquals(4, spec.endLine)
    }

    @Test
    fun symbolAndTemplate() {
        val text = "class Foo {\n  fun bar() {}\n}\n"
        assertEquals(1, findSymbolLine(text, "Foo"))
        val span = resolveSymbolLineRangeFromText(text, "Foo")!!
        assertEquals(1, span.startLine)
        assertTrue(span.endLine >= 1)
        assertEquals("# Hello\n\n", applyDocTemplate("# {{title}}\n\n", "Hello"))
        assertEquals("img.png", safeAssetFileName("img.png"))
        assertEquals("createOrder", guessDeclName("    public CompletableFuture<Order> createOrder(OrderRequest req) {"))
        assertEquals("process", guessDeclName("    void process();"))
        assertEquals("main", guessDeclName("    public static void main(String[] args) {"))
        assertEquals("bar", guessDeclName("  override fun bar() {}"))
        assertEquals("Foo", guessDeclName("public class Foo {"))
        assertEquals(null, guessDeclName("        log.info(\"hi\");"))
        assertEquals(null, guessDeclName("        if (ready) {"))
        assertEquals("createOrder", suggestSymbolFromLines(
            listOf("public class Svc {", "    public void createOrder(Req req) {", "    }", "}"),
            2,
            3,
        ))
        val javaIface = """
            public interface Svc {
                /**
                 * 获取仓库列表
                 */
                List<UavEquipmentWarehouse> getWarehouseList(String path);
                List<UavEquipmentWarehouse> getWarehouseListByOrgCode(String path);
            }
        """.trimIndent().trim()
        assertEquals(5, findSymbolLine(javaIface, "getWarehouseList"))
        assertEquals(6, findSymbolLine(javaIface, "getWarehouseListByOrgCode"))
        assertEquals(null, findSymbolLine("        foo.getWarehouseList(path);\n", "getWarehouseList"))
    }

    @Test
    fun bindableFilters() {
        assertTrue(isBindableSourceRel("src/Foo.kt", "docs/cbd"))
        assertTrue(!isBindableSourceRel("docs/cbd/x.md", "docs/cbd"))
        assertTrue(!isBindableSourceRel("node_modules/a.js", "docs/cbd"))
        assertTrue(isBindableDirectoryRel("src/util", "docs/cbd"))
    }
}
