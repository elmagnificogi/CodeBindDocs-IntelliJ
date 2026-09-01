package com.codebinddocs.core

data class MovePlan(
    val moves: List<Pair<String, String>>,
    val conflicts: List<String>,
)

data class MigrationResult(
    val moved: Int,
    val conflicts: List<String>,
)

fun planMoves(
    relPaths: List<String>,
    oldNorm: String,
    newNorm: String,
    destExists: (String) -> Boolean,
): MovePlan {
    val moves = mutableListOf<Pair<String, String>>()
    val conflicts = mutableListOf<String>()
    for (raw in relPaths) {
        val rel = normalizeRelPath(raw)
        if (rel != oldNorm && !rel.startsWith("$oldNorm/")) continue
        val to = newNorm + rel.substring(oldNorm.length)
        if (destExists(to)) {
            conflicts += to
            continue
        }
        moves += rel to to
    }
    return MovePlan(moves, conflicts)
}
