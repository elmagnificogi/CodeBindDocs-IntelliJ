# CodeBind Docs for IntelliJ

IntelliJ Platform 插件（IDEA / Android Studio / PyCharm / WebStorm / GoLand / CLion 等）：用旁路 Markdown 绑定源码，打开代码时右侧同步文档。绑定格式与 [VS Code / Cursor 扩展](https://github.com/elmagnificogi/CodeBindDocs) 完全兼容。

详细步骤见 [使用说明](docs/USER_GUIDE.md) · 产品需求见 [REQUIREMENTS](docs/REQUIREMENTS.md) · 开发见 [DEVELOPMENT](docs/DEVELOPMENT.md)

## 功能

- **分栏同步**：打开/切换源文件时，右侧 **CodeBind Docs** 工具窗显示绑定文档；`Ctrl+Alt+D` 手动打开；`Ctrl+Alt+Shift+D` 开关自动同步
- **整文件 / 代码块 / 目录绑定**：YAML `cbd.kind: file | range | directory`
- **即时渲染**：JCEF + Vditor IR（类 Typora），可切纯文本源码
- **侧栏 Bindings**：已绑定 / 待绑定
- **漂移治理**：改名更新路径、哈希软提醒、按 symbol 重算行号
- **Agent 脚手架**：Initialize 写入 `AGENTS.md`、Cursor rules、Junie guidelines

## 构建

需要 **JDK 21**（Gradle Toolchain 会自动下载）。

```bat
gradlew test
gradlew buildPlugin
```

生成的 zip 在 `build/distributions/`。IDEA：**Settings → Plugins → Install Plugin from Disk**。

调试：`gradlew runIde`

## 绑定格式

与 VS Code 扩展相同，写在 Markdown 文件头：

```yaml
cbd:
  target: src/main/kotlin/com/codebinddocs/core/Frontmatter.kt
  kind: file
```

默认文档目录 `docs/cbd/`（设置 `cbd.docsPath`）。
