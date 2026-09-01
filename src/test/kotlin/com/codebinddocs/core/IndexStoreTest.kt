package com.codebinddocs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText

class IndexStoreTest {
    @Test
    fun writeAndReadBinding() {
        val root = Files.createTempDirectory("cbd-test")
        val store = IndexStore(root, CbdConfig())
        store.ensureLayout()
        store.writeBinding(
            Binding(
                id = "docs/cbd/foo.md",
                doc = "docs/cbd/foo.md",
                target = BindingTarget("src/Foo.kt", BindingKind.FILE),
            ),
            title = "Foo",
        )
        val index = store.read()
        assertEquals(1, index.bindings.size)
        assertEquals("src/Foo.kt", index.bindings[0].target.path)
        val rendered = store.workspacePath(store.indexDocPath).readText()
        assertTrue(rendered.contains("src/Foo.kt"))
        assertEquals("docs/cbd/Foo.md", suggestDocPath(store.docsPath, "src/Foo.kt"))
        val resolved = resolveBindingForLine(index, "src/Foo.kt", 1)
        assertEquals("docs/cbd/foo.md", resolved?.doc)
    }

    @Test
    fun resolveTightestRange() {
        val index = emptyIndex()
        index.bindings += Binding("a.md", BindingTarget("src/A.kt", BindingKind.FILE), "a.md")
        index.bindings += Binding("b.md", BindingTarget("src/A.kt", BindingKind.RANGE, 10, 40), "b.md")
        index.bindings += Binding("c.md", BindingTarget("src/A.kt", BindingKind.RANGE, 12, 20), "c.md")
        assertEquals("c.md", resolveBindingForLine(index, "src/A.kt", 15)?.doc)
        assertEquals("a.md", resolveBindingForLine(index, "src/A.kt", 2)?.doc)
    }

    @Test
    fun hashAndTemplates() {
        val root = Files.createTempDirectory("cbd-hash")
        val file = root.resolve("src").resolve("A.kt")
        Files.createDirectories(file.parent)
        file.writeText("class A {}\n")
        val store = IndexStore(root)
        val hash = store.hashFileContent(file)
        assertEquals(12, hash.length)
        assertEquals(3, store.ensureDefaultTemplates())
        assertEquals(0, store.ensureDefaultTemplates())
        assertTrue(store.listDocTemplates().any { it.id == "design" })
    }
}
