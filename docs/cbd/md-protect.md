---
cbd:
  target: src/main/kotlin/com/codebinddocs/core/MdProtect.kt
  kind: file
---
# MdProtect.kt

## 概述

围栏内裸 --- 加 ZWSP，避免 Vditor IR 把示例 YAML 当分隔线。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义