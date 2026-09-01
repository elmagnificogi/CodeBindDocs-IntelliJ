---
cbd:
  target: src/main/kotlin/com/codebinddocs/core/BindingIndex.kt
  kind: file
---
# BindingIndex.kt

## 概述

配置默认路径、可绑定过滤、覆盖率、光标行解析最窄 range、生成 cbd-index.md。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义