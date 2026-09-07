# CodeBind Docs for JetBrains IDEs 使用说明

面向最终用户。产品全称 **CodeBind Docs**，简称 **CBD**。命令名与 [VS Code / Cursor 扩展](https://github.com/elmagnificogi/CodeBindDocs) 一致，绑定格式共用同一套 `cbd:` YAML 头。

---

## 1. 安装

1. 从 [GitHub Releases](https://github.com/elmagnificogi/CodeBindDocs-IntelliJ/releases) 下载 `CodeBindDocs-JetBrains-*.zip`，或本地 `gradlew.bat buildPlugin`（需要 **JDK 21**，产物在 `build/distributions/`）
2. 任意 JetBrains IDE：**Settings → Plugins → 齿轮 → Install Plugin from Disk**，选该 zip
3. 重启 IDE，打开一个**项目文件夹**（单文件模式无法扫描绑定）

环境：基于 IntelliJ 平台 **2024.1+** 的 JetBrains IDE（IntelliJ IDEA、Android Studio、PyCharm、WebStorm、GoLand、CLion、PhpStorm、Rider、DataGrip 等）。

**2026.2 起**即时渲染依赖捆绑插件 **Web Browser (JCEF)**。若右侧面板空白或提示无法加载浏览器：Settings → Plugins 启用该插件后重启。未启用时仍可从左侧 **CBD Bindings** 打开 Markdown。

---

## 2. 五分钟上手

1. **Tools → CodeBind Docs → CBD: Initialize**（或 Search Everywhere 搜 `CBD: Initialize`）
   - 创建默认 `docs/cbd/`、`docs/cbd/assets/`、`docs/cbd/_templates/`
   - 写入 `AGENTS.md`、`.cursor/rules/cbd.mdc`、Junie guidelines
   - 可多次执行；已有文件会尽量保留
2. 打开一个源文件，运行 **CBD: Bind Doc to Current File**
   - **整文件**：整个文件共用一篇文档
   - **代码块**：在编辑器中拖选范围，点顶部横幅「确认选区」，并建议填写符号名（函数/类）
3. 右侧 **CodeBind Docs** 工具窗出现文档；切换源文件会自动跟随（可关）

也可在项目树对文件夹右键 **CBD: Bind Doc to Folder**，给整个目录一篇说明文档。

---

## 3. 日常

### 3.1 分栏同步

| 操作 | 说明 |
|------|------|
| 自动分栏 | 默认开启。切换源文件时右侧文档跟随。设置 `splitSyncEnabled`；快捷键 `Ctrl+Alt+Shift+D` |
| 打开当前绑定 | `Ctrl+Alt+D` / 状态栏 CBD / 源码上方 Inlay（`CBD: Reveal Bound Doc`） |
| 主页 | `CBD: Open Docs Index`：绑定树、覆盖率、漂移提醒 |
| 侧栏 | 左侧 **CBD Bindings**：已绑定 / 待绑定 |
| 跳回代码 | 文档工具栏 **Code** 或 `CBD: Reveal Source Range` |
| 代码块选区 | 绑定 range 时在编辑器拖选，点编辑器顶部「确认选区」（非模态） |

关闭自动分栏后，打开代码不再强制弹出文档窗；仍可用 `Ctrl+Alt+D` 或状态栏打开。无绑定时会显示「无关联文档」，可从中新建（由设置「无绑定时显示新建入口」控制）。

同一文件可有多个代码块绑定，外加至多一个整文件绑定。光标落在某 range 内时优先显示**最窄**文档。

### 3.2 编辑文档

右侧面板：

| 模式 | 说明 |
|------|------|
| 即时渲染 | Vditor IR，类 Typora；可开右侧大纲 |
| 文档源码 | 纯 Markdown 文本，适合精细改写 |
| 目录文档 | 当前代码的祖先目录有目录绑定时，工具栏出现「目录文档」 |

- YAML 文件头在面板中隐藏，保存时自动拼回
- 粘贴/上传图片 → 写入 `docs/cbd/assets/`（或设置的 `assetsPath`），正文用相对路径
- 导航：主页 / 后退 / 前进
- 红色 **删除**：删绑定文档（需确认；不可删自动生成的 `cbd-index.md`）

正文示例里尽量避免裸写一行 `---`（会干扰即时渲染）；需要展示时放在代码围栏内。

### 3.3 只读嵌入其它文档

在旁路文档中：

````markdown
```cbd-include
doc: docs/cbd/foo.md
heading: 概述
```
````

| 字段 | 含义 |
|------|------|
| `doc` / `path` / `file` | 目标文档（相对当前文档，或文档目录下的文件名） |
| `heading` | 只嵌入该标题及其下属内容 |
| `lines` | 如 `10-40`，按正文行号切片 |

面板中展开为只读预览；保存仍写回紧凑 `cbd-include`。

---

## 4. 漂移与维护

代码会变，绑定可能失效。策略是**提醒 + 可操作修复**，不强制改你的文档正文。

| 类型 | 含义 | 常见处理 |
|------|------|----------|
| 源文件缺失 | 文档指向的路径不存在 | 重新绑定 / 删除失效文档 |
| 文档缺失 | 索引里有路径但文件没了 | 重新绑定（新建） |
| 行范围失效 | range 行号越界 | **按 symbol 重算行号** / 改绑 |
| 符号变动 | symbol 找不到或移出原范围 | 同上 |
| 范围重叠 | 同文件多个 range 相交 | 打开文档调整范围 |
| 源码已变（hash） | 仅提醒文档可能过时 | 打开核对，或「标记已核对」 |

相关命令：`CBD: Show Binding Drift`、`CBD: Retighten Range by Symbol`、`CBD: Refresh Doc contentHash` / `Refresh All …`、`CBD: Rebind Doc to Source`。

改绑：`CBD: Rebind Doc to Source` 可选新源文件、整文件或代码块，允许 range ↔ file 互改。删除后文件通常进入回收站；不可删除 `cbd-index.md`。

---

## 5. 设置

**Settings → Tools → CodeBind Docs**：

| 设置 | 默认 | 说明 |
|------|------|------|
| `docsPath` | `docs/cbd` | 文档根目录（相对项目根） |
| `assetsPath` | 空 → `{docsPath}/assets` | 图片等资源目录 |
| `templatesPath` | 空 → `{docsPath}/_templates` | 新建文档模板；有 `.md` 则用磁盘模板 |
| 打开源文件时自动显示绑定文档 | 开 | 即 `splitSyncEnabled` |
| 无绑定时显示新建入口 | 开 | 自动分栏开启时是否提示新建 |
| 文档面板位置 | `Beside` | `Beside` 或 `Two` |
| 编辑模式 | `ir` | `ir` 即时渲染 / `source` 纯文本 |
| 即时渲染右侧显示大纲 | 开 | 修改后对已打开面板即时生效 |

模板正文可用占位符 `{{title}}`。更改文档/资源/模板路径时，插件会询问是否迁移旧目录内容。

快捷键（可在 **Settings → Keymap** 里改）：

| 快捷键 | 命令 |
|--------|------|
| `Ctrl+Alt+D` | 打开当前代码的绑定文档 |
| `Ctrl+Alt+Shift+D` | 开关自动分栏 |

---

## 6. 命令

均在 **Tools → CodeBind Docs** 与 Search Everywhere 中；命令名与 VS Code 版一致。

| 命令 | 作用 |
|------|------|
| `CBD: Initialize` | 初始化文档目录与 Agent 脚手架 |
| `CBD: Bind Doc to Current File` | 为当前源文件新建绑定 |
| `CBD: Bind Doc to Folder` | 为文件夹新建目录绑定（项目树右键也有） |
| `CBD: Rebind Doc to Source` | 改绑到新源文件或新粒度 |
| `CBD: Delete Bound Doc` | 删除绑定文档 |
| `CBD: Reveal Bound Doc` | 打开当前文件的旁路文档（`Ctrl+Alt+D`）；无绑定时打开「无关联文档」 |
| `CBD: Reveal Source Range` | 从文档跳到源码选区 |
| `CBD: Retighten Range by Symbol` | 按 symbol 重算行号 |
| `CBD: Open Docs Index` | 打开文档主页 |
| `CBD: Show Binding Drift` | 查看并处理漂移 |
| `CBD: Refresh Doc contentHash` | 单篇标记已核对 |
| `CBD: Mark All Docs Checked (contentHash)` | 全部标记已核对 |
| `CBD: Toggle Split Sync` | 开关自动分栏（`Ctrl+Alt+Shift+D`） |
| `CBD: Refresh Doc Tree` | 重扫绑定并刷新侧栏、主页、`cbd-index.md` 与漂移 |
| `CBD: 确认代码块选区` / `CBD: 取消代码块选区` | 代码块绑定进行中时确认或取消选区 |

---

## 7. 与 AI Agent 协作

Initialize 会生成：

- **`AGENTS.md`**：布局说明 + 模块→文档对照表
- **`.cursor/rules/cbd.mdc`**：提示改绑定源文件前先读文档
- **Junie guidelines**：JetBrains AI 助手同样可读

约定：

1. 改行为前先读对应旁路文档
2. 行为变更时同一提交更新文档
3. 不要在源码里塞 CodeBind Docs 标记；绑定只在文档头
4. 不要手改 `cbd-index.md`（会被覆盖）

文档本身是普通 Markdown，任何能读仓库的 Agent 都能直接打开。

---

## 8. 数据与 Git

建议提交：

- `docs/cbd/**/*.md`（含绑定头；路径以设置为准）
- 需要的图片（`assets/`）
- `AGENTS.md`、`.cursor/rules/cbd.mdc`

`docs/cbd/cbd-index.md`（或 `docs/cbd-index.md`）为自动汇总，**勿手改**。

---

## 9. 常见问题

**Q: 装了插件但没有反应？**  
确认打开的是项目文件夹；Search Everywhere 能搜到 `CBD: Initialize`。

**Q: 右侧不出现文档 / 面板空白？**  
先看自动分栏是否开启、该文件是否已绑定。2026.2+ 请启用 **Web Browser (JCEF)** 后重启。无 JCEF 时仍可用左侧 **CBD Bindings** 打开 Markdown。

**Q: 点文档工具栏 Code 没有跳到源码？**  
请用 0.1.16 及以上版本；应打开并聚焦对应源文件，range 绑定还会选中行范围。

**Q: 即时渲染卡住或顶部出现奇怪代码块？**  
正文里裸 `---` 易触发；示例 YAML 请放进代码围栏。可切到「文档源码」再改。

**Q: 改了代码行号全乱了？**  
绑定 range 时填好 symbol，用 **按 symbol 重算行号**，或重新选区改绑。

**Q: 和注释、云端文档有何不同？**  
强调**本地、源码零侵入、旁路 Markdown + 分栏**；不做强制云端。

**Q: 能否把文档目录里的文件当作绑定目标？**  
不能；文档目录内文件是「文档侧」，不能作为 `cbd.target`。

---

## 10. 更多文档

| 文档 | 用途 |
|------|------|
| [readme.md](../readme.md) | 简介 / 快速开始 |
| [REQUIREMENTS.md](REQUIREMENTS.md) | 产品范围与数据模型 |
| [DEVELOPMENT.md](DEVELOPMENT.md) | 插件开发、调试与发版 |
| [VS Code 使用说明](https://github.com/elmagnificogi/CodeBindDocs/blob/main/docs/USER_GUIDE.md) | 同一套绑定格式的 VS Code / Cursor 版 |

问题与建议欢迎在仓库提 Issue。
