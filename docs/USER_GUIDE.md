# CodeBind Docs for JetBrains IDEs 使用说明

面向最终用户。产品全称 **CodeBind Docs**，简称 **CBD**。

## 1. 安装

1. 构建插件：`gradlew buildPlugin`
2. 任意 JetBrains IDE：**Settings → Plugins → 齿轮 → Install Plugin from Disk**，选 `build/distributions/*.zip`
3. 重启 IDE，打开一个项目文件夹

环境：基于 IntelliJ 平台 2024.1+ 的 JetBrains IDE（IntelliJ IDEA、Android Studio、PyCharm、WebStorm、GoLand、CLion、PhpStorm、Rider、DataGrip 等）。

**2026.2 起**即时渲染依赖捆绑插件 **Web Browser (JCEF)**。若右侧面板空白或提示无法加载浏览器：Settings → Plugins 启用该插件后重启。未启用时仍可从左侧 **CBD Bindings** 打开 Markdown。

## 2. 五分钟上手

1. **Tools → CodeBind Docs → CBD: Initialize**（或 Search Everywhere 搜 `CBD: Initialize`）
2. 打开一个源文件，运行 **CBD: Bind Doc to Current File**，选择整文件或代码块
3. 右侧 **CodeBind Docs** 工具窗出现文档；切换源文件会自动跟随（可关）

## 3. 日常

| 操作 | 说明 |
|------|------|
| 自动分栏 | 设置 `splitSyncEnabled`；快捷键 `Ctrl+Alt+Shift+D` |
| 打开当前绑定 | `Ctrl+Alt+D` / 状态栏 CBD / 源码上方 Inlay |
| 主页 | `CBD: Open Docs Index` |
| 侧栏 | 左侧 **CBD Bindings**：已绑定 / 待绑定 |
| 跳回代码 | 文档工具栏 **Code** 或 `CBD: Reveal Source Range` |
| 代码块选区 | 绑定 range 时在编辑器拖选，点编辑器顶部「确认选区」（非模态） |

即时渲染与文档源码切换、YAML 头隐藏、粘贴图片、`cbd-include`、漂移处理与 VS Code 版相同，详见原扩展 [USER_GUIDE](https://github.com/elmagnificogi/CodeBindDocs/blob/main/docs/USER_GUIDE.md)。

## 4. 设置

**Settings → Tools → CodeBind Docs**：

- `docsPath` 默认 `docs/cbd`
- `assetsPath` / `templatesPath` 可空
- 自动分栏、无绑定提示、IR/source 模式、大纲

## 5. 命令

与 VS Code 命令名一致，均在 **Tools → CodeBind Docs** 与 Search Everywhere 中。
