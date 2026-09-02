---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/editor/RangePicker.kt
  kind: file
---
# RangePicker.kt

## 概述

非模态代码块选区：只在源码编辑器顶部显示「确认选区 / 取消」，不再发右下角通知，也不再往状态栏动态 `addWidget`（那是内部 API）。选区期间暂停分栏同步，避免文档工具窗抢走焦点。已注册的 CBD 状态栏控件会改成「确认代码块选区」，点击即可确认。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义