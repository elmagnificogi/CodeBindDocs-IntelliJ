---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/CbdCommands.kt
  kind: file
---
# CbdCommands.kt

## 概述

绑定/删除/改绑等会弹窗的命令共用防重入：模态期间与结束后 1.5 秒忽略重复请求，避免 JCEF 双通道关框后再问一遍。成功结果用右下角通知。选「代码块」后在源码编辑器顶部确认选区；符号名按选区自动预填，留空不再二次确认。选文件/文件夹用 `FileChooserDescriptor` 构造函数，不用已废弃的 `FileChooserDescriptorFactory.createSingleFileDescriptor()`。

`revealSourceRange`（文档工具栏 **Code**）打开源文件后要把焦点还给编辑器：先短时抑制分栏同步，再 `openTextEditor`，最后 `IdeFocusManager.requestFocus`。否则 JCEF 工具窗会占着焦点，或 `FileEditorManagerListener` 立刻 `ToolWindow.show()` 把焦点抢回去，看起来像没有跳到代码。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义