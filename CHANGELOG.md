# Changelog

## 0.1.13

- 市场校验：插件 ID 改为 `com.codebinddocs.plugin`（ID 不能含 intellij）；简介改为英文开头；JCEF 可选依赖补上 `config-file`
- Gradle 工程名改为 `CodeBindDocs-JetBrains`（安装 zip 文件名随之变化）

## 0.1.12

- 代码块选区只保留编辑器顶部确认条，去掉右下角重复通知

## 0.1.11

- 漂移检测能识别 Java 方法签名上的 symbol，避免刚绑定 `getWarehouseList` 这类接口方法就报「未找到符号」

## 0.1.10

- 代码块绑定会按选区自动预填函数/类名；符号名留空不再弹出二次确认

## 0.1.9

- 选择「代码块」绑定后，在源码编辑器顶部显示确认条并抢回焦点，避免看起来像点完没反应

## 0.1.8

- 文档编辑区改为随工具窗拉满高度，不再被预热时的 700px 锁死，避免比右侧大纲矮一截

## 0.1.7

- 删除 / 重新绑定等弹窗命令同样防重入，避免点一次出两次确认框

## 0.1.6

- 新建关联成功后改为右下角通知，不再弹「确定」框；并忽略重复的 createBind，避免关框后又问一遍绑定粒度

## 0.1.5

- 修复文档预览滚轮无法回到顶部：Vditor 表格/代码块的独立滚动会截胡滚轮，改为交给外层编辑器

## 0.1.4

- 主页 / 后退 / 新建关联等面板按钮改为 console 回传，不依赖 JCEF JSQuery；不在主页时后退可回到主页
- 每次启动覆盖 pane.js，避免临时目录里的旧脚本把点击吞掉

## 0.1.3

- 修复右侧面板按钮无响应：`pane.js` 在 JCEF 注入桥接前捕获了空的 `postMessage`，「新建关联文档」等点了没反应

## 0.1.2

- 启动时在 EDT 上打开工具窗 / Inlay，避免 `Assert: must be called on EDT`

## 0.1.1

- 适配 IntelliJ 2026.2：JCEF 已拆成独立插件，声明 `com.intellij.modules.jcef` 依赖，避免打开文档面板时 `NoClassDefFoundError: JBCefApp`
- 无 JCEF 时工具窗改为简易 HTML 列表，不再把 Initialize 打崩

## 0.1.0

- 首版 IntelliJ Platform 插件：从 VS Code / Cursor 扩展全量迁移核心能力
- 旁路 YAML 绑定（file / range / directory）与 VS Code 扩展兼容
- 右侧 JCEF + Vditor 文档面板、左侧 Bindings 树、状态栏、Inlay
- 漂移检测、按 symbol 重算行号、路径迁移、Agent 脚手架
