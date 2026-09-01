# CodeBind Docs for IntelliJ 开发

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
gradlew test
gradlew buildPlugin
gradlew runIde
```

## 调试

`runIde` 启动带本插件的 IntelliJ Community。改代码后重新 runIde 或在沙箱 IDE 里 **Reload**。

2026.2 起文档面板依赖捆绑插件 JCEF（`com.intellij.modules.jcef`，`plugin.xml` 中为 optional，以便 2025.3.1 之前仍能加载）。
