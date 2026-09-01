---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/ui/WebviewAssets.kt
  kind: file
---
# WebviewAssets.kt

## 概述

把插件内 pane.html/js 与 Vditor 解压到临时目录供 JCEF file:// 加载。每次启动都覆盖 pane.html/css/js，避免旧脚本留在缓存里导致按钮无响应。插件 ID 为 `com.codebinddocs.plugin`（市场不允许 ID 含 `intellij`）。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义