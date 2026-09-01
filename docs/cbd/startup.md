---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/CbdStartupActivity.kt
  kind: file
---
# CbdStartupActivity.kt

## 概述

项目打开后启动 CbdProjectService.start()（`ProjectActivity` 不在 EDT 上；UI 由 start 内部切回）。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义