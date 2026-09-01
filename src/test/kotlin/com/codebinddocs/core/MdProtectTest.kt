package com.codebinddocs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MdProtectTest {
    @Test
    fun protectsBareHrInsideFences() {
        val md = listOf("# T", "", "```yaml", "---", "cbd:", "  target: x", "---", "```", "", "done").joinToString("\n")
        val protectedMd = protectHrInFences(md)
        assertTrue(protectedMd.contains("\u200B---"))
        val inside = protectedMd.split("```")[1]
        assertFalse(Regex("^---$", RegexOption.MULTILINE).containsMatchIn(inside))
        assertEquals(md, unprotectHrInFences(protectedMd))
    }

    @Test
    fun doesNotProtectOutsideFences() {
        val md = "before\n---\nafter\n"
        assertEquals(md, protectHrInFences(md))
    }
}
