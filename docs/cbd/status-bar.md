---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/CbdStatusBarWidget.kt
  kind: file
---
# CbdStatusBarWidget.kt

## 概述

状态栏显示 CBD / 绑定警告 / 核对提醒，点击打开当前绑定文档。代码块选区进行中时文案改为「确认代码块选区」，点击即确认。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义