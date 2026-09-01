# CodeBind Docs（IntelliJ）产品需求

## 定位

CodeBind Docs（**CBD**）IntelliJ Platform 插件：用旁路绑定把设计文档与源码关联，在 IDE 中左右分栏同步查看与编辑，且不修改原始源码。绑定格式与 VS Code / Cursor 扩展一致，同一仓库可两边共用文档。

## 目标

- 文档与代码同仓库、可 Git、本地可用。
- 打开带绑定的源文件时，右侧 **CodeBind Docs** 工具窗打开对应 Markdown。
- 支持整文件、代码块（行范围）、目录绑定；光标进入代码块时切换最窄文档。
- Agent 可通过仓库 Markdown + `AGENTS.md` / Cursor rules / Junie guidelines 读取设计上下文。
- 本仓库自身 `src/main/kotlin/**` 均有旁路文档。

## 非目标

- 真混排 Webview
- 云端同步后端
- 基于 Git 历史自动重写全部绑定

## MVP 范围

与 VS Code 扩展对齐：Initialize、绑定、分栏同步、Vditor 文档面板、Bindings 树、行内入口（Inlay）、主页/覆盖率、漂移检测、删除、重新绑定、模板、资源、大纲、cbd-include、Agent 脚手架。

数据模型见 VS Code 仓库 `docs/REQUIREMENTS.md` 的「数据模型」；`cbd:` YAML 头完全相同。
