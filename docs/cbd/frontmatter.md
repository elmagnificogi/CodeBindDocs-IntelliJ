---
cbd:
  target: src/main/kotlin/com/codebinddocs/core/Frontmatter.kt
  kind: file
---
# Frontmatter.kt

## 概述

解析/序列化 Markdown YAML 头中的 cbd: 块；剥离 BOM 与重复文件头。与 VS Code 扩展格式兼容。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义