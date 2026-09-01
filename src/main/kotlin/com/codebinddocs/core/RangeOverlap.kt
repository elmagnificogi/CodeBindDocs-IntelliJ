package com.codebinddocs.core

data class OverlapPair(
    val a: Binding,
    val b: Binding,
    val path: String,
)

private fun isRangeBinding(b: Binding): Boolean =
    b.target.kind == BindingKind.RANGE &&
        b.target.startLine != null &&
        b.target.endLine != null

fun lineRangesOverlap(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean =
    aStart <= bEnd && bStart <= aEnd

fun findOverlappingRangePairs(bindings: List<Binding>): List<OverlapPair> {
    val byPath = linkedMapOf<String, MutableList<Binding>>()
    for (b in bindings) {
        if (!isRangeBinding(b)) continue
        val path = normalizeRelPath(b.target.path)
        byPath.getOrPut(path) { mutableListOf() }.add(b)
    }
    val pairs = mutableListOf<OverlapPair>()
    for ((path, list) in byPath) {
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                val a = list[i]
                val b = list[j]
                if (lineRangesOverlap(
                        a.target.startLine!!,
                        a.target.endLine!!,
                        b.target.startLine!!,
                        b.target.endLine!!,
                    )
                ) {
                    pairs += OverlapPair(a, b, path)
                }
            }
        }
    }
    return pairs
}

fun findOverlapsWithExisting(
    existing: List<Binding>,
    path: String,
    startLine: Int,
    endLine: Int,
    excludeDoc: String? = null,
): List<Binding> {
    val norm = normalizeRelPath(path)
    val exclude = excludeDoc?.let { normalizeRelPath(it) }
    return existing.filter { b ->
        isRangeBinding(b) &&
            normalizeRelPath(b.target.path) == norm &&
            (exclude == null || normalizeRelPath(b.doc) != exclude) &&
            lineRangesOverlap(startLine, endLine, b.target.startLine!!, b.target.endLine!!)
    }
}
