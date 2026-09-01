# CodeBind Docs 文档汇总

共 **28** 个绑定。由仓库脚手架生成；绑定变更后请同步更新。

文档目录：`docs/cbd/`（设置项 `cbd.docsPath`）。

绑定声明在各文档 YAML 头的 `cbd.target`；本页仅作目录。

| 源文件 | 文档 | 类型 |
| --- | --- | --- |
| [`src/main/kotlin/com/codebinddocs/core/Types.kt`](../../src/main/kotlin/com/codebinddocs/core/Types.kt) | [`docs/cbd/types.md`](./types.md) | file |
| [`src/main/kotlin/com/codebinddocs/core/Frontmatter.kt`](../../src/main/kotlin/com/codebinddocs/core/Frontmatter.kt) | [`docs/cbd/frontmatter.md`](./frontmatter.md) | file |
| [`src/main/kotlin/com/codebinddocs/core/RangeOverlap.kt`](../../src/main/kotlin/com/codebinddocs/core/RangeOverlap.kt) | [`docs/cbd/range-overlap.md`](./range-overlap.md) | file |
| [`src/main/kotlin/com/codebinddocs/core/MdProtect.kt`](../../src/main/kotlin/com/codebinddocs/core/MdProtect.kt) | [`docs/cbd/md-protect.md`](./md-protect.md) | file |
| [`src/main/kotlin/com/codebinddocs/core/DocMedia.kt`](../../src/main/kotlin/com/codebinddocs/core/DocMedia.kt) | [`docs/cbd/doc-media.md`](./doc-media.md) | file |
| [`src/main/kotlin/com/codebinddocs/core/DocEmbed.kt`](../../src/main/kotlin/com/codebinddocs/core/DocEmbed.kt) | [`docs/cbd/doc-embed.md`](./doc-embed.md) | file |
| [`src/main/kotlin/com/codebinddocs/core/DocTemplates.kt`](../../src/main/kotlin/com/codebinddocs/core/DocTemplates.kt) | [`docs/cbd/doc-templates.md`](./doc-templates.md) | file |
| [`src/main/kotlin/com/codebinddocs/core/PathPlan.kt`](../../src/main/kotlin/com/codebinddocs/core/PathPlan.kt) | [`docs/cbd/path-plan.md`](./path-plan.md) | file |
| [`src/main/kotlin/com/codebinddocs/core/SymbolRange.kt`](../../src/main/kotlin/com/codebinddocs/core/SymbolRange.kt) | [`docs/cbd/symbol-range.md`](./symbol-range.md) | file |
| [`src/main/kotlin/com/codebinddocs/core/BindingIndex.kt`](../../src/main/kotlin/com/codebinddocs/core/BindingIndex.kt) | [`docs/cbd/binding-index.md`](./binding-index.md) | file |
| [`src/main/kotlin/com/codebinddocs/core/IndexStore.kt`](../../src/main/kotlin/com/codebinddocs/core/IndexStore.kt) | [`docs/cbd/index-store.md`](./index-store.md) | file |
| [`src/main/kotlin/com/codebinddocs/core/Scaffold.kt`](../../src/main/kotlin/com/codebinddocs/core/Scaffold.kt) | [`docs/cbd/scaffold.md`](./scaffold.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/CbdSettings.kt`](../../src/main/kotlin/com/codebinddocs/intellij/CbdSettings.kt) | [`docs/cbd/settings.md`](./settings.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/CbdUi.kt`](../../src/main/kotlin/com/codebinddocs/intellij/CbdUi.kt) | [`docs/cbd/ui-dialogs.md`](./ui-dialogs.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/CbdProjectService.kt`](../../src/main/kotlin/com/codebinddocs/intellij/CbdProjectService.kt) | [`docs/cbd/project-service.md`](./project-service.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/CbdCommands.kt`](../../src/main/kotlin/com/codebinddocs/intellij/CbdCommands.kt) | [`docs/cbd/commands.md`](./commands.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/CbdStartupActivity.kt`](../../src/main/kotlin/com/codebinddocs/intellij/CbdStartupActivity.kt) | [`docs/cbd/startup.md`](./startup.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/CbdConfigurable.kt`](../../src/main/kotlin/com/codebinddocs/intellij/CbdConfigurable.kt) | [`docs/cbd/configurable.md`](./configurable.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/CbdStatusBarWidget.kt`](../../src/main/kotlin/com/codebinddocs/intellij/CbdStatusBarWidget.kt) | [`docs/cbd/status-bar.md`](./status-bar.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/actions/CbdActions.kt`](../../src/main/kotlin/com/codebinddocs/intellij/actions/CbdActions.kt) | [`docs/cbd/actions.md`](./actions.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/drift/DriftChecker.kt`](../../src/main/kotlin/com/codebinddocs/intellij/drift/DriftChecker.kt) | [`docs/cbd/drift-checker.md`](./drift-checker.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/sync/SplitSync.kt`](../../src/main/kotlin/com/codebinddocs/intellij/sync/SplitSync.kt) | [`docs/cbd/split-sync.md`](./split-sync.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/editor/RangePicker.kt`](../../src/main/kotlin/com/codebinddocs/intellij/editor/RangePicker.kt) | [`docs/cbd/range-picker.md`](./range-picker.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/editor/CbdInlayController.kt`](../../src/main/kotlin/com/codebinddocs/intellij/editor/CbdInlayController.kt) | [`docs/cbd/inlays.md`](./inlays.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/store/PathMigration.kt`](../../src/main/kotlin/com/codebinddocs/intellij/store/PathMigration.kt) | [`docs/cbd/path-migration.md`](./path-migration.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/ui/CbdBindingsPanel.kt`](../../src/main/kotlin/com/codebinddocs/intellij/ui/CbdBindingsPanel.kt) | [`docs/cbd/bindings-tree.md`](./bindings-tree.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/ui/WebviewAssets.kt`](../../src/main/kotlin/com/codebinddocs/intellij/ui/WebviewAssets.kt) | [`docs/cbd/webview-assets.md`](./webview-assets.md) | file |
| [`src/main/kotlin/com/codebinddocs/intellij/ui/CbdMarkdownPane.kt`](../../src/main/kotlin/com/codebinddocs/intellij/ui/CbdMarkdownPane.kt) | [`docs/cbd/markdown-pane.md`](./markdown-pane.md) | file |

## 快捷操作

- 命令：`CBD: Open Docs Index` 打开本页
- 命令：`CBD: Bind Doc to Current File` 为当前源文件创建绑定
- 侧栏 **CBD Bindings** 可跳转源码 / 文档