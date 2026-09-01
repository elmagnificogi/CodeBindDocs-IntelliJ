---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/editor/SymbolSuggest.kt
  kind: file
---
# SymbolSuggest.kt

## 概述

从代码块选区推断函数/类名：优先沿 PSI 向上找方法/类，失败再走 `guessDeclName` 启发式（含 Java 方法签名）。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义
