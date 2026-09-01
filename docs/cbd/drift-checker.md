---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/drift/DriftChecker.kt
  kind: file
---
# DriftChecker.kt

## 概述

VFS 改名更新路径；保存后扫描缺失/越界/符号/重叠/哈希；优先按 symbol 重算。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义