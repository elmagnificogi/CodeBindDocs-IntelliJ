package com.codebinddocs.core

enum class BindingKind {
    FILE,
    RANGE,
    DIRECTORY;

    val yaml: String
        get() = when (this) {
            FILE -> "file"
            RANGE -> "range"
            DIRECTORY -> "directory"
        }

    companion object {
        fun fromYaml(raw: String?): BindingKind = when (raw) {
            "range" -> RANGE
            "directory" -> DIRECTORY
            else -> FILE
        }
    }
}

data class BindingTarget(
    var path: String,
    var kind: BindingKind,
    var startLine: Int? = null,
    var endLine: Int? = null,
)

data class BindingAnchor(
    var symbol: String? = null,
    var startHint: Int? = null,
    var contentHash: String? = null,
)

data class Binding(
    var id: String,
    var target: BindingTarget,
    var doc: String,
    var anchors: MutableList<BindingAnchor> = mutableListOf(),
)

data class CbdIndex(
    val version: Int = 1,
    val bindings: MutableList<Binding> = mutableListOf(),
)

fun emptyIndex(): CbdIndex = CbdIndex()

fun normalizeRelPath(p: String): String =
    p.replace('\\', '/').replace(Regex("^\\./"), "")
