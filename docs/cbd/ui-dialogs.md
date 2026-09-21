---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/CbdUi.kt
  kind: file
---
# CbdUi.kt

## 概述

模态对话框封装：信息/确认/输入/列表选择；`notify` 为非模态气球。保存后的哈希核对必须走气球，不要用 `choose`/`info`，否则会在 VFS 写锁上把 IDE 卡住。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义