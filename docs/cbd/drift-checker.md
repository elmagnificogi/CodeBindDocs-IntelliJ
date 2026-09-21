---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/drift/DriftChecker.kt
  kind: file
---
# DriftChecker.kt

## 概述

VFS 改名更新路径；保存后扫描缺失/越界/符号/重叠/哈希；优先按 symbol 重算。

保存触发的提醒**不得**在 `BulkFileListener.after` 里弹模态框（写锁未释放时 `Messages.showDialog` 会把整个 IDE 冻死）。哈希软提醒用右下角气球（打开文档核对 / 标记已核对）；其它漂移也要 `invokeLater(ModalityState.nonModal)` 后再提示。主动执行 `CBD: Show Binding Drift` 仍可用对话框。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义