package com.codebinddocs.core

data class DocTemplateChoice(
    val id: String,
    val label: String,
    val description: String,
    val body: String,
    val sourceRel: String? = null,
)

private val BUILTIN = listOf(
    DocTemplateChoice(
        id = "design",
        label = "设计文档",
        description = "概述 · 约束与不变量 · 备注",
        body = listOf(
            "# {{title}}",
            "",
            "## 概述",
            "",
            "在此描述设计意图、职责边界与关键协作。",
            "",
            "## 约束与不变量",
            "",
            "-",
            "",
            "## 备注",
            "",
            "-",
            "",
        ).joinToString("\n"),
    ),
    DocTemplateChoice(
        id = "api",
        label = "API / 接口",
        description = "概述 · API · 使用注意 · 相关",
        body = listOf(
            "# {{title}}",
            "",
            "## 概述",
            "",
            "简述职责与调用方。",
            "",
            "## API / 接口",
            "",
            "### ",
            "",
            "| 参数 | 类型 | 说明 |",
            "| --- | --- | --- |",
            "|  |  |  |",
            "",
            "## 使用注意",
            "",
            "-",
            "",
            "## 相关",
            "",
            "-",
            "",
        ).joinToString("\n"),
    ),
    DocTemplateChoice(
        id = "minimal",
        label = "简洁",
        description = "仅标题，自行填写",
        body = "# {{title}}\n\n",
    ),
)

fun applyDocTemplate(body: String, title: String): String =
    body.replace(Regex("\\{\\{\\s*title\\s*\\}\\}", RegexOption.IGNORE_CASE), title)

fun builtinDocTemplates(): List<DocTemplateChoice> = BUILTIN.map { it.copy() }

fun serializeTemplateFile(t: DocTemplateChoice): String {
    val body = if (t.body.endsWith("\n")) t.body else t.body + "\n"
    return listOf("---", "label: ${t.label}", "description: ${t.description}", "---", "", body)
        .joinToString("\n")
}

fun parseTemplateFile(text: String, sourceRel: String): DocTemplateChoice? {
    val base = sourceRel.split('/').last().replace(Regex("\\.md$", RegexOption.IGNORE_CASE), "").ifEmpty { "template" }
    val (meta, body) = splitTemplateFrontmatter(text)
    val bodyTrim = body.replace(Regex("^\uFEFF"), "").replace(Regex("^\\r?\\n"), "")
    if (bodyTrim.isBlank()) return null
    return DocTemplateChoice(
        id = base,
        label = meta.label?.trim()?.ifEmpty { null } ?: base,
        description = meta.description?.trim()?.ifEmpty { null } ?: sourceRel,
        body = if (bodyTrim.endsWith("\n")) bodyTrim else "$bodyTrim\n",
        sourceRel = sourceRel,
    )
}

data class TemplateMeta(val label: String? = null, val description: String? = null)

fun splitTemplateFrontmatter(text: String): Pair<TemplateMeta, String> {
    val normalized = text.replace(Regex("^\uFEFF"), "")
    val m = Regex("^---\\r?\\n([\\s\\S]*?)\\r?\\n---\\r?\\n?([\\s\\S]*)$").find(normalized)
        ?: return TemplateMeta() to normalized
    if (Regex("^\\s*cbd\\s*:", RegexOption.MULTILINE).containsMatchIn(m.groupValues[1])) {
        return TemplateMeta() to normalized
    }
    var label: String? = null
    var description: String? = null
    for (line in m.groupValues[1].split(Regex("\\r?\\n"))) {
        val kv = Regex("^(\\w+)\\s*:\\s*(.*)$").find(line) ?: continue
        var value = kv.groupValues[2].trim()
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))
        ) {
            value = value.substring(1, value.length - 1)
        }
        when (kv.groupValues[1]) {
            "label" -> label = value
            "description" -> description = value
        }
    }
    return TemplateMeta(label, description) to m.groupValues[2]
}

fun defaultDocBody(title: String): String =
    "# $title\n\n## 概述\n\n在此描述设计意图、约束与不变量。\n\n## 备注\n\n-\n"
