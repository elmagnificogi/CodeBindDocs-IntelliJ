---
cbd:
  target: src/main/kotlin/com/codebinddocs/core/IndexStore.kt
  kind: file
---
# IndexStore.kt

## 概述

扫描文档目录、读写绑定、删除进回收站、contentHash、模板与源文件扫描。基于 java.nio，不改源码。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义