---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/ui/CbdMarkdownPane.kt
  kind: file
---
# CbdMarkdownPane.kt

## 概述

右侧文档面板：主页、覆盖率、无关联、Vditor IR、保存、导航、资源上传。

优先用 JCEF 加载 Vditor。平台 **2026.2** 起 JCEF 不在 core 里，必须声明 `com.intellij.modules.jcef`（optional + `config-file`），否则 `JBCefApp` 对插件 classloader 不可见，Initialize 会直接炸掉。探测时捕获 `ClassNotFoundException` / `LinkageError`；不可用时退到简易 HTML，避免工具窗创建失败。

`pane.js` 点按钮时用 `console.log('__CBD_MSG__'+json)` 回传（不依赖 JSQuery 是否已注入）；主页 / 后退 / 新建关联走同一条通道。不在主页时「后退」可回到主页。JSQuery 用 `JBCefJSQuery.create(JBCefBrowserBase)`，避免即将删除的 `create(JBCefBrowser)` 重载。文档防抖保存用 Swing `Timer`，不用已废弃的 `Alarm`。

工具栏 **Code** 发 `openTarget`。file 绑定的 `startLine`/`endLine` 是 JSON `null`，不能对 `JsonNull` 调 Gson `asInt`（会抛错，看起来像点了没反应）；用跳过 null 的取值后再调用 `revealSourceRange`。

Vditor 默认把表格设成 `display:block; overflow:auto`，滚轮在表头附近会被截胡，文档滚不回顶部。面板 CSS 取消表格独立滚动，并把嵌套元素上的纵向滚轮交给 `.vditor-reset`。

Vditor 3.11.2 的 `resize()` 不接受高度参数；构造时若写入像素高度（预热时 `#editorRoot.warming` 只有 700px），编辑区会永远矮于大纲。面板用 `height: 100%` 加 flex 拉满工具窗，大纲与 IR 同高。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义