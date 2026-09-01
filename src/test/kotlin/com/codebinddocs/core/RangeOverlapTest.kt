package com.codebinddocs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RangeOverlapTest {
    private fun rangeBinding(doc: String, path: String, start: Int, end: Int) = Binding(
        id = doc,
        doc = doc,
        target = BindingTarget(path, BindingKind.RANGE, start, end),
    )

    @Test
    fun inclusiveOverlap() {
        assertTrue(lineRangesOverlap(1, 5, 5, 8))
        assertFalse(lineRangesOverlap(1, 4, 5, 8))
        assertTrue(lineRangesOverlap(10, 20, 1, 15))
    }

    @Test
    fun findsOnePairOnce() {
        val pairs = findOverlappingRangePairs(
            listOf(
                rangeBinding("a.md", "src/x.ts", 1, 10),
                rangeBinding("b.md", "src/x.ts", 8, 15),
                rangeBinding("c.md", "src/y.ts", 1, 3),
            ),
        )
        assertEquals(1, pairs.size)
        assertEquals("src/x.ts", pairs[0].path)
    }

    @Test
    fun ignoresSelfAndOtherFiles() {
        val existing = listOf(
            rangeBinding("a.md", "src/x.ts", 1, 10),
            rangeBinding("b.md", "src/x.ts", 20, 30),
        )
        assertEquals(0, findOverlapsWithExisting(existing, "src/x.ts", 8, 12, "a.md").size)
        val hits = findOverlapsWithExisting(existing, "src/x.ts", 8, 12)
        assertEquals(1, hits.size)
        assertEquals("a.md", hits[0].doc)
    }
}
