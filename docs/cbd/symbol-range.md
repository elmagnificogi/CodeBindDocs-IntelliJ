---
cbd:
  target: src/main/kotlin/com/codebinddocs/core/SymbolRange.kt
  kind: file
---
# SymbolRange.kt

## 概述

按符号名启发式定位声明行与花括号块，供漂移重算行号。`findSymbolLine` / `guessDeclName` 能识别 Java/Kotlin 方法签名（`Type name(`），避免刚绑定就报「未找到符号」。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义