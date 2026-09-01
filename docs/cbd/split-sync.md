---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/sync/SplitSync.kt
  kind: file
---
# SplitSync.kt

## 概述

活动编辑器与光标变化时同步右侧文档面板；无绑定显示新建入口。代码块选区进行中时不同步，以免工具窗抢走编辑器焦点。

`ToolWindow.show()` 与文档面板必须在 EDT 上调用。`ProjectActivity` 在后台协程执行，因此 `syncNow` / `openDoc` / `openHome` 会先切回 EDT，避免 `Assert: must be called on EDT`。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义