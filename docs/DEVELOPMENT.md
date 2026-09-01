# CodeBind Docs for JetBrains IDEs 开发

## 结构

```text
src/main/kotlin/com/codebinddocs/core     纯逻辑（无 IDE API，可单测）
src/main/kotlin/com/codebinddocs/intellij IDE 集成
src/main/resources/webview                Vditor + pane.html/js
src/test/kotlin                           JUnit 5
docs/cbd                                  本仓库 dogfood 绑定文档
```

绑定见 [cbd-index.md](cbd-index.md)（自动惯例：请勿手改汇总表时与源码同步）。

## 构建

需要 **JDK 21**（把 `JAVA_HOME` 指到 JDK 21；本机若只有 JDK 11，Gradle 插件无法加载）。

```bat
set JAVA_HOME=C:\Users\elmag\.jdks\jdk-21.0.12.1+1
gradlew.bat test
gradlew.bat buildPlugin
gradlew.bat runIde
```

生成的 zip 在 `build/distributions/`。CI 说明见文末「CI 与自动发版」。

## 调试

`runIde` 启动带本插件的 IntelliJ IDEA Community（用于调试；安装产物可装到任意基于该平台的 JetBrains IDE）。改代码后重新 runIde 或在沙箱 IDE 里 **Reload**。

2026.2 起文档面板依赖捆绑插件 JCEF（`com.intellij.modules.jcef`，`plugin.xml` 中为 optional 并带 `config-file="cbd-jcef.xml"`，以便无 JCEF 时仍能加载）。

## CI 与自动发版

仓库 workflow：

- [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)：push / PR 跑 `check` + `buildPlugin`
- [`.github/workflows/publish.yml`](../.github/workflows/publish.yml)：打 `v*` tag 后发 GitHub Release

发版步骤：

1. 把 `gradle.properties` 的 `pluginVersion` 升到目标版（如 `0.1.14`），并在 `CHANGELOG.md` 写同名章节。
2. 提交后打同名 tag：

```bash
git tag v0.1.14
git push origin v0.1.14
```

3. tag 必须与 `pluginVersion` 一致（`v` 前缀），否则 workflow 失败。
4. 成功后会在 GitHub **Releases** 创建同名 release，并附上 `CodeBindDocs-JetBrains-*.zip`。说明取自 `CHANGELOG.md` 对应章节。
5. 也可在 Actions 里手动 **Run workflow**（`workflow_dispatch`）；手动跑时不做 tag 校验，也**不**创建 GitHub Release，只上传构建产物。

可选：上 JetBrains Marketplace。在 GitHub → Settings → Secrets and variables → Actions 配置：

- **`PUBLISH_TOKEN`**：https://plugins.jetbrains.com/author/me/tokens
- 若 Marketplace 要求签名，再配 **`CERTIFICATE_CHAIN`** / **`PRIVATE_KEY`** / **`PRIVATE_KEY_PASSWORD`**（见 [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)）

未配置 `PUBLISH_TOKEN` 时仍会发 GitHub Release，只是跳过市场上架。
