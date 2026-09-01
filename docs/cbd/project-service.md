---
cbd:
  target: src/main/kotlin/com/codebinddocs/intellij/CbdProjectService.kt
  kind: file
---
# CbdProjectService.kt

## 概述

项目服务枢纽：组装 IndexStore、漂移、分栏、面板、Inlay，启动扫描与脚手架写入。

`start()` 里写索引、漂移扫描可在后台；路径迁移对话框、Inlay、分栏同步切到 EDT 再跑。

## 约束

- 不在被绑定的源码中写入 CodeBind Docs 标记
- 绑定只放在文档 YAML 头 `cbd:` 下
- 行为变更时同步更新本文档

## 备注

- 与 VS Code 扩展共用同一套 `cbd:` 文件头语义