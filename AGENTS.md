# AGENTS

本仓库使用 **CodeBind Docs（CBD）** 旁路文档。

## 布局

- 文档目录由设置 `cbd.docsPath` 决定，**默认 `docs/cbd/`**
- `docs/cbd/*.md` — 设计文档；**绑定写在 Markdown YAML 文件头**
- `docs/cbd/cbd-index.md` / `docs/cbd-index.md` — 全部绑定的汇总目录
- `docs/cbd/assets/` — 可选媒体
- `docs/cbd/_templates/` — 新建文档模板（`{{title}}`）
- `docs/REQUIREMENTS.md` — 产品需求（无绑定头）
- `docs/USER_GUIDE.md` — 最终用户说明
- `docs/DEVELOPMENT.md` — 插件开发与调试

## 模块文档（源码 → 文档）

改代码前请先读对应文档（详见 `docs/cbd-index.md`）：

| 源码 | 文档 |
| --- | --- |
| `src/main/kotlin/com/codebinddocs/core/Types.kt` | `docs/cbd/types.md` |
| `src/main/kotlin/com/codebinddocs/core/Frontmatter.kt` | `docs/cbd/frontmatter.md` |
| `src/main/kotlin/com/codebinddocs/core/RangeOverlap.kt` | `docs/cbd/range-overlap.md` |
| `src/main/kotlin/com/codebinddocs/core/MdProtect.kt` | `docs/cbd/md-protect.md` |
| `src/main/kotlin/com/codebinddocs/core/DocMedia.kt` | `docs/cbd/doc-media.md` |
| `src/main/kotlin/com/codebinddocs/core/DocEmbed.kt` | `docs/cbd/doc-embed.md` |
| `src/main/kotlin/com/codebinddocs/core/DocTemplates.kt` | `docs/cbd/doc-templates.md` |
| `src/main/kotlin/com/codebinddocs/core/PathPlan.kt` | `docs/cbd/path-plan.md` |
| `src/main/kotlin/com/codebinddocs/core/SymbolRange.kt` | `docs/cbd/symbol-range.md` |
| `src/main/kotlin/com/codebinddocs/core/BindingIndex.kt` | `docs/cbd/binding-index.md` |
| `src/main/kotlin/com/codebinddocs/core/IndexStore.kt` | `docs/cbd/index-store.md` |
| `src/main/kotlin/com/codebinddocs/core/Scaffold.kt` | `docs/cbd/scaffold.md` |
| `src/main/kotlin/com/codebinddocs/intellij/CbdSettings.kt` | `docs/cbd/settings.md` |
| `src/main/kotlin/com/codebinddocs/intellij/CbdUi.kt` | `docs/cbd/ui-dialogs.md` |
| `src/main/kotlin/com/codebinddocs/intellij/CbdProjectService.kt` | `docs/cbd/project-service.md` |
| `src/main/kotlin/com/codebinddocs/intellij/CbdCommands.kt` | `docs/cbd/commands.md` |
| `src/main/kotlin/com/codebinddocs/intellij/CbdStartupActivity.kt` | `docs/cbd/startup.md` |
| `src/main/kotlin/com/codebinddocs/intellij/CbdConfigurable.kt` | `docs/cbd/configurable.md` |
| `src/main/kotlin/com/codebinddocs/intellij/CbdStatusBarWidget.kt` | `docs/cbd/status-bar.md` |
| `src/main/kotlin/com/codebinddocs/intellij/actions/CbdActions.kt` | `docs/cbd/actions.md` |
| `src/main/kotlin/com/codebinddocs/intellij/drift/DriftChecker.kt` | `docs/cbd/drift-checker.md` |
| `src/main/kotlin/com/codebinddocs/intellij/sync/SplitSync.kt` | `docs/cbd/split-sync.md` |
| `src/main/kotlin/com/codebinddocs/intellij/editor/RangePicker.kt` | `docs/cbd/range-picker.md` |
| `src/main/kotlin/com/codebinddocs/intellij/editor/SymbolSuggest.kt` | `docs/cbd/symbol-suggest.md` |
| `src/main/kotlin/com/codebinddocs/intellij/editor/CbdInlayController.kt` | `docs/cbd/inlays.md` |
| `src/main/kotlin/com/codebinddocs/intellij/store/PathMigration.kt` | `docs/cbd/path-migration.md` |
| `src/main/kotlin/com/codebinddocs/intellij/ui/CbdBindingsPanel.kt` | `docs/cbd/bindings-tree.md` |
| `src/main/kotlin/com/codebinddocs/intellij/ui/WebviewAssets.kt` | `docs/cbd/webview-assets.md` |
| `src/main/kotlin/com/codebinddocs/intellij/ui/CbdMarkdownPane.kt` | `docs/cbd/markdown-pane.md` |

## 绑定格式

文件头用三连短横线围栏包裹；正文示例勿写裸分隔线。

```yaml
cbd:
  target: src/main/kotlin/com/codebinddocs/core/Frontmatter.kt
  kind: file
```

## Agent 规则

1. 可先打开 `docs/cbd-index.md` 查看全部绑定。
2. 改源文件前，阅读 `cbd.target` 等于该路径的 Markdown。
3. 行为/设计意图变更时同步更新对应文档。
4. 保持普通 Markdown；绑定只放在文件头 `cbd:` 下。
5. 不要在源码中插入 CodeBind Docs 标记。
6. 不要手改 `cbd-index.md`（会被覆盖）。

## 人类命令

- `CBD: Initialize` — 创建文档目录与脚手架
- `CBD: Open Docs Index` — 打开文档主页 / 汇总
- `CBD: Bind Doc to Current File` — 新建绑定
- `CBD: Rebind Doc to Source` — 失效文档改绑源文件
- `CBD: Delete Bound Doc` — 删除绑定文档
- `CBD: Show Binding Drift` — 查看绑定漂移
- `CBD: Reveal Bound Doc` — 打开当前文件的绑定文档（`Ctrl+Alt+D`）
- `CBD: Reveal Source Range` — 从文档跳到源码
- `CBD: Toggle Split Sync` — 开关自动分栏（`Ctrl+Alt+Shift+D`）
