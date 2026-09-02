---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/ui/WebviewAssets.kt
  kind: file
---
# WebviewAssets.kt

## 概述

把插件内 pane.html/js 与 Vditor 解压到 `PathManager.getSystemDir()/codebinddocs-webview`，供 JCEF file:// 加载（不用即将删除的 `getPluginTempPath()`）。每次启动都覆盖 pane.html/css/js，避免旧脚本留在缓存里导致按钮无响应。Vditor 是否重解压用 `pane.js` 内容指纹判断，不读 `PluginManagerCore.getPlugin`（2026.2 起为内部 API）。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义