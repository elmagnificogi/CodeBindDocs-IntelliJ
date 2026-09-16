---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/sync/SplitSync.kt
  kind: file
---
# SplitSync.kt

## 概述

跟随与强制弹出分开：文档工具窗**已经可见**且分栏开启时，切换源文件/光标才更新面板内容；工具窗隐藏时打开或切换代码**不会** `show()` 弹窗。用户再次打开文档工具窗时，按**当前**源文件同步一次（不再沿用隐藏期间的旧文档）。`forceFocus`（`Ctrl+Alt+D`、Inlay、Initialize、主页点文档等）才 `activate()` 打开面板。无绑定且面板已开时显示新建入口。代码块选区进行中、以及从文档跳到源码的短窗口内不同步，以免抢走编辑器焦点。

`ProjectActivity` 在后台协程执行，因此 `syncNow` / `openDoc` / `openHome` 会先切回 EDT，避免 `Assert: must be called on EDT`。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义