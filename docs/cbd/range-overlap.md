---
cbd:
  target: src/main/kotlin/com/codebinddocs/core/RangeOverlap.kt
  kind: file
---
# RangeOverlap.kt

## 概述

判断 range 绑定行范围是否相交；新建/改绑时二次确认。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义