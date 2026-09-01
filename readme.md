# CodeBind Docs for JetBrains IDEs

![image-20260719232659382](https://img.elmagnifico.tech/static/upload/elmagnifico/202607192327543.png)

**代码文档插件**（适用于所有基于 IntelliJ 平台的 JetBrains IDE：IntelliJ IDEA、Android Studio、PyCharm、WebStorm、GoLand、CLion、PhpStorm、Rider、DataGrip 等）：源码零侵入，文档落在仓库 Markdown 里，打开代码时右侧工具窗同步查看与编辑。适合人与 AI Agent 共用同一套设计上下文。

绑定格式与 [VS Code / Cursor 扩展](https://github.com/elmagnificogi/CodeBindDocs) **完全兼容**，同一仓库可两边共用文档。

命令与菜单前缀为 **CBD**（CodeBind Docs 简称）。

> 详细步骤见 [使用说明](docs/USER_GUIDE.md) · 产品需求见 [REQUIREMENTS](docs/REQUIREMENTS.md)

---

## 安装

目前从磁盘安装插件 zip（尚未上 JetBrains Marketplace）：

1. 构建：`gradlew.bat buildPlugin`（需要 **JDK 21**）
2. IDE：**Settings → Plugins → 齿轮 → Install Plugin from Disk**，选 `build/distributions/CodeBindDocs-JetBrains-*.zip`
3. 重启 IDE，打开一个**项目文件夹**

环境：基于 IntelliJ 平台 **2024.1+** 的 JetBrains IDE。

**2026.2 起**即时渲染依赖捆绑插件 **Web Browser (JCEF)**。若右侧文档面板空白：Settings → Plugins 启用该插件后重启。未启用时仍可从左侧 **CBD Bindings** 打开 Markdown。

---

## 为什么用 CodeBind Docs

| 痛点 | CodeBind Docs 做法 |
|------|----------|
| 文档散落、和代码对不上 | 绑定写在文档 YAML 头，跟文件 / 行范围 / 目录走 |
| 注释污染源码 | **不改源码**，旁路 Markdown |
| 云端文档难版本控制 | 纯本地、可 Git，无强制云端 |
| Agent 不知道读哪 | 生成 `AGENTS.md` / Cursor rules / Junie guidelines，文档就在仓库里 |
| VS Code 与 JetBrains IDE 各写一套 | 同一套 `cbd:` 头，两边都能打开 |

---

## 功能一览

- **分栏同步**：打开/切换源文件时，右侧 **CodeBind Docs** 工具窗显示绑定文档；可关。`Ctrl+Alt+D` 一键打开当前代码对应文档；`Ctrl+Alt+Shift+D` 开关自动同步
- **整文件 / 代码块 / 目录绑定**：`kind: file` · `range`（行范围 + 建议填 symbol）· `directory`（整目录说明，`CBD: Bind Doc to Folder`）
- **即时渲染**：JCEF + Vditor 类 Typora 编辑；可切纯文本源码；可选大纲 TOC
- **主页与侧栏**：绑定目录树、覆盖率、待绑定列表、漂移提醒；左侧 **CBD Bindings**
- **漂移治理**：改名（含目录）自动改路径；哈希软提醒；按 symbol 一键重算行号
- **资源与嵌入**：粘贴图片进 `assets/`；`cbd-include` 只读嵌入本仓库其它文档
- **Agent 友好**：Initialize 写入对照表与规则，改代码前可读绑定文档

---

## 快速开始

1. 安装插件后，打开任意**项目文件夹**（单文件模式无法扫描绑定）
2. **Tools → CodeBind Docs → CBD: Initialize**（或 Search Everywhere 搜 `CBD: Initialize`），创建默认 `docs/cbd/`、`AGENTS.md`、Cursor rules、Junie guidelines
3. 打开一个源文件，运行 **`CBD: Bind Doc to Current File`**（整文件或代码块）；或在项目树对文件夹 **`CBD: Bind Doc to Folder`**
4. 自动分栏开启时，切换源文件即可右侧跟随；也可随时 `Ctrl+Alt+D` 打开对应文档
5. 左侧有 **CBD Bindings** 工具窗（已绑定 / 待绑定）

常用入口：

| 入口 | 作用 |
|------|------|
| `CBD: Open Docs Index` | 文档主页 |
| `Ctrl+Alt+D` / 源码 Inlay / 状态栏 CBD | 打开当前源文件的旁路文档 |
| `Ctrl+Alt+Shift+D` | 开关自动分栏 |
| 侧栏 **已绑定 / 待绑定** | 浏览与补绑 |
| 代码块绑定 | 在编辑器中拖选，点顶部横幅 **确认选区** |

完整流程、设置项、命令表见 **[docs/USER_GUIDE.md](docs/USER_GUIDE.md)**。

---

## 绑定长什么样

文档目录默认 **`docs/cbd/`**（设置 `cbd.docsPath` 可改）。绑定写在 Markdown **文件头**：

```yaml
cbd:
  target: src/foo.ts
  kind: file          # file | range | directory
  startLine: 15       # 仅 range
  endLine: 44         # 仅 range
  symbol: activate    # range 强烈建议填
  contentHash: abc    # 插件维护，一般不用手改
```

（实际文件头需用 YAML 围栏包裹。）

目录绑定示例：

```yaml
cbd:
  target: src/store
  kind: directory
```

各字段含义：

| 字段 | 必填 | 含义 |
|------|------|------|
| `target` | 是 | 绑定的源路径（相对项目根）：文件、或 `directory` 时的目录 |
| `kind` | 是 | `file` = 整文件；`range` = 代码块；`directory` = 整个目录 |
| `startLine` / `endLine` | `range` 时 | 代码块起止行号（1-based，含两端） |
| `symbol` | `range` 强烈建议 | 该代码块对应的符号名（函数 / 类 / 方法等）。行号漂移时可用 **按 symbol 重算行号** 更新起止行 |
| `contentHash` | 否（插件写入） | 内容哈希，软提醒源码可能已变；`directory` 不做内容哈希 |

补充：

- **不修改**被绑定的源码文件；真相源在文档头
- 同一源文件可有多个 `range`，至多一个 `file`；光标行优先匹配**最窄** `range`，否则回退 `file`；文件未单独绑定时可回退到所属目录的 `directory` 文档
- 无 `cbd:` 头的 Markdown 不算绑定（例如本仓库的 `REQUIREMENTS.md`）
- 新建 / 改绑代码块时，插件会按选区自动预填 `symbol`（含 Java 方法）；留空则只按行号绑定，不再二次确认

---

## 要求

- 基于 IntelliJ 平台 2024.1+ 的 JetBrains IDE（IntelliJ IDEA、Android Studio、PyCharm、WebStorm、GoLand、CLion、PhpStorm、Rider、DataGrip 等）
- 打开**项目文件夹**（非单文件）
- 即时渲染需要 JCEF；2026.2 请启用 **Web Browser (JCEF)** 捆绑插件
- 构建插件需要 **JDK 21**

---

## 文档与开发

| 文档 | 内容 |
|------|------|
| [使用说明](docs/USER_GUIDE.md) | 安装、绑定、漂移、设置、命令 |
| [产品需求](docs/REQUIREMENTS.md) | 定位、范围、数据模型 |
| [开发调试](docs/DEVELOPMENT.md) | Gradle、`runIde`、JCEF |
| [VS Code 扩展](https://github.com/elmagnificogi/CodeBindDocs) | 同一套绑定格式的 VS Code / Cursor 版 |

```bat
gradlew.bat test
gradlew.bat buildPlugin
gradlew.bat runIde
```

生成的 zip 在 `build/distributions/`。

---

## 许可

MIT
