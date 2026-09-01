package com.codebinddocs.intellij.ui

import com.codebinddocs.core.BindingKind
import com.codebinddocs.core.collapseDocIncludes
import com.codebinddocs.core.expandDocIncludes
import com.codebinddocs.core.findByDocPath
import com.codebinddocs.core.findDirectoryBindingForRel
import com.codebinddocs.core.joinMarkdown
import com.codebinddocs.core.normalizeRelPath
import com.codebinddocs.core.protectHrInFences
import com.codebinddocs.core.relativeToDoc
import com.codebinddocs.core.rewriteImagesForDisk
import com.codebinddocs.core.rewriteImagesForWebview
import com.codebinddocs.core.safeAssetFileName
import com.codebinddocs.core.splitMarkdown
import com.codebinddocs.core.unprotectHrInFences
import com.codebinddocs.intellij.CbdProjectService
import com.codebinddocs.intellij.drift.DriftKind
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.Alarm
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.nio.file.Files
import java.util.Base64
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class CbdMarkdownPane(private val svc: CbdProjectService) : Disposable {
    val component = JPanel(BorderLayout())
    var currentDocRel: String? = null
        private set
    var isHome: Boolean = false
        private set
    var isCoverage: Boolean = false
        private set

    private var browser: JBCefBrowser? = null
    private var jsQuery: JBCefJSQuery? = null
    private var ready = false
    private var header: String? = null
    private var imageReverse = mutableMapOf<String, String>()
    private val gson = Gson()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val history = mutableListOf<NavEntry>()
    private var historyIndex = -1
    private var navigatingHistory = false
    private var unboundSource: String? = null
    private var pendingPayload: Map<String, Any?>? = null
    private var fallbackPane: JEditorPane? = null
    private var lastBridgePayload: String = ""
    private var lastBridgeAt: Long = 0

    fun attach() {
        if (component.componentCount > 0) return
        if (JcefSupport.isAvailable()) {
            try {
                attachJcef()
                return
            } catch (_: Throwable) {
                component.removeAll()
                browser = null
                jsQuery = null
                ready = false
            }
        }
        attachFallback()
    }

    private fun attachJcef() {
        val b = object : JBCefBrowser() {
            init {
                jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
                    override fun onConsoleMessage(
                        browser: CefBrowser?,
                        level: CefSettings.LogSeverity?,
                        message: String?,
                        source: String?,
                        line: Int,
                    ): Boolean {
                        val raw = message ?: return false
                        val marker = "__CBD_MSG__"
                        val idx = raw.indexOf(marker)
                        if (idx < 0) return false
                        enqueueHostMessage(raw.substring(idx + marker.length))
                        return true
                    }
                }, cefBrowser)
                jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                    override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                        if (frame?.isMain != true) return
                        injectBridge()
                        ready = true
                        pendingPayload?.let { post(it) }
                        pendingPayload = null
                    }
                }, cefBrowser)
            }
        }
        browser = b
        jsQuery = JBCefJSQuery.create(b as JBCefBrowserBase)
        jsQuery!!.addHandler { payload ->
            enqueueHostMessage(payload)
            null
        }
        b.createImmediately()
        component.add(b.component, BorderLayout.CENTER)
        val html = WebviewAssets.extract().resolve("pane.html")
        b.loadURL(html.toUri().toString())
    }

    private fun attachFallback() {
        val pane = JEditorPane("text/html", fallbackHtml("正在加载…"))
        pane.isEditable = false
        pane.isOpaque = false
        pane.addHyperlinkListener { e ->
            if (e.eventType != HyperlinkEvent.EventType.ACTIVATED) return@addHyperlinkListener
            val desc = e.description ?: return@addHyperlinkListener
            if (desc.startsWith("doc:")) svc.commands.openDoc(desc.removePrefix("doc:"))
        }
        fallbackPane = pane
        component.add(JBScrollPane(pane), BorderLayout.CENTER)
    }

    private fun useJcef(): Boolean = browser != null

    private fun setFallback(html: String) {
        fallbackPane?.text = html
        fallbackPane?.caretPosition = 0
    }

    fun warmIr() {
        post(mapOf("type" to "warmIr"))
    }

    fun showDoc(docRel: String, forceFocus: Boolean) {
        attach()
        if (!useJcef()) {
            val store = svc.storeOrNull()
            val path = store?.workspacePath(normalizeRelPath(docRel))
            val body = if (path != null && path.exists()) path.readText(Charsets.UTF_8) else ""
            setFallback(
                fallbackHtml(
                    "<p>文档 <b>${esc(docRel)}</b></p><p><a href='doc:${esc(docRel)}'>在编辑器中打开</a></p>" +
                        "<pre>${esc(body)}</pre>",
                ),
            )
            currentDocRel = normalizeRelPath(docRel)
            isHome = false
            isCoverage = false
            return
        }
        val store = svc.storeOrNull() ?: return
        currentDocRel = normalizeRelPath(docRel)
        isHome = false
        isCoverage = false
        unboundSource = null
        if (!navigatingHistory) push(NavEntry.Doc(currentDocRel!!))
        val path = store.workspacePath(currentDocRel!!)
        val raw = if (path.exists()) path.readText(Charsets.UTF_8) else ""
        val split = splitMarkdown(raw)
        header = split.header
        var body = protectHrInFences(split.body)
        body = expandDocIncludes(body, currentDocRel!!, store.docsPath) { rel ->
            val p = store.workspacePath(rel)
            if (p.exists()) p.readText(Charsets.UTF_8) else null
        }
        val rewritten = rewriteImagesForWebview(body, currentDocRel!!) { rel ->
            store.workspacePath(rel).toUri().toString()
        }
        imageReverse = rewritten.second
        val binding = findByDocPath(store.read(), currentDocRel!!)
        val jump = binding?.let {
            mapOf(
                "path" to it.target.path,
                "kind" to it.target.kind.yaml,
                "startLine" to it.target.startLine,
                "endLine" to it.target.endLine,
            )
        }
        val dirDoc = binding?.let { findDirectoryBindingForRel(store.read(), it.target.path) }?.let {
            mapOf("doc" to it.doc, "dirPath" to it.target.path)
        }
        post(
            mapOf(
                "type" to "load",
                "title" to (currentDocRel!!.substringAfterLast('/')),
                "markdown" to rewritten.first,
                "mode" to svc.settings.stored.docPaneMode,
                "docRel" to currentDocRel,
                "deletable" to (currentDocRel != store.indexDocPath),
                "sourceJump" to jump,
                "directoryDoc" to dirDoc,
            ) + navFlags(),
        )
        @Suppress("UNUSED_PARAMETER")
        val unused = forceFocus
    }

    fun showHome(forceFocus: Boolean) {
        attach()
        if (!useJcef()) {
            currentDocRel = null
            isHome = true
            isCoverage = false
            val store = svc.storeOrNull()
            val index = if (store != null && store.exists()) store.read() else com.codebinddocs.core.emptyIndex()
            val items = buildString {
                append("<p><a href='doc:${esc(store?.indexDocPath ?: "docs/cbd/cbd-index.md")}'>打开汇总页 cbd-index.md</a></p><ul>")
                for (b in index.bindings) {
                    append("<li><a href='doc:${esc(b.doc)}'>${esc(b.doc.substringAfterLast('/'))}</a> → ${esc(b.target.path)}</li>")
                }
                append("</ul>")
            }
            setFallback(fallbackHtml("<p>文档主页</p>$items"))
            return
        }
        currentDocRel = null
        isHome = true
        isCoverage = false
        unboundSource = null
        if (!navigatingHistory) push(NavEntry.Home)
        val store = svc.storeOrNull() ?: return
        val index = if (store.exists()) store.read() else com.codebinddocs.core.emptyIndex()
        val docs = mutableListOf<Map<String, Any?>>()
        docs += mapOf("doc" to store.indexDocPath, "target" to "(汇总)", "title" to "cbd-index.md", "kind" to "index")
        for (b in index.bindings) {
            docs += mapOf(
                "doc" to b.doc,
                "target" to b.target.path,
                "title" to b.doc.substringAfterLast('/'),
                "kind" to b.target.kind.yaml,
                "startLine" to b.target.startLine,
                "endLine" to b.target.endLine,
                "symbol" to b.anchors.firstOrNull()?.symbol,
            )
        }
        val missing = mutableListOf<Map<String, Any?>>()
        val hints = mutableListOf<Map<String, Any?>>()
        for (issue in svc.drift.issues) {
            val item = mapOf(
                "kind" to issue.kind.name.lowercase().replace('_', '-').let {
                    if (it == "missing-target") "missing-target" else it
                }.let { k ->
                    when (issue.kind) {
                        DriftKind.MISSING_TARGET -> "missing-target"
                        DriftKind.MISSING_DOC -> "missing-doc"
                        DriftKind.HASH -> "hash"
                        DriftKind.RANGE -> "range"
                        DriftKind.SYMBOL -> "symbol"
                        DriftKind.OVERLAP -> "overlap"
                        DriftKind.RENAMED -> "renamed"
                    }
                },
                "target" to issue.targetPath,
                "doc" to issue.doc,
                "message" to issue.message,
            )
            if (issue.kind == DriftKind.HASH) hints += item else missing += item
        }
        val coverage = try {
            val report = store.scanCoverage(index)
            mapOf("boundCount" to report.boundCount, "total" to report.total, "unboundCount" to report.unbound.size)
        } catch (_: Exception) {
            null
        }
        post(
            mapOf(
                "type" to "home",
                "docsPath" to store.docsPath,
                "docs" to docs,
                "missing" to missing,
                "hints" to hints,
                "coverage" to coverage,
            ) + navFlags(),
        )
        @Suppress("UNUSED_PARAMETER")
        val unused = forceFocus
    }

    fun showCoverage(forceFocus: Boolean) {
        attach()
        if (!useJcef()) {
            currentDocRel = null
            isHome = false
            isCoverage = true
            val store = svc.storeOrNull() ?: return
            val report = store.scanCoverage()
            setFallback(
                fallbackHtml(
                    "<p>覆盖率：已绑定 ${report.boundCount} / ${report.total}</p><ul>" +
                        report.unbound.joinToString("") { "<li>${esc(it)}</li>" } +
                        "</ul>",
                ),
            )
            return
        }
        currentDocRel = null
        isHome = false
        isCoverage = true
        unboundSource = null
        if (!navigatingHistory) push(NavEntry.Coverage)
        val store = svc.storeOrNull() ?: return
        val report = store.scanCoverage()
        post(
            mapOf(
                "type" to "coverage",
                "boundCount" to report.boundCount,
                "total" to report.total,
                "unbound" to report.unbound,
            ) + navFlags(),
        )
        @Suppress("UNUSED_PARAMETER")
        val unused = forceFocus
    }

    fun showUnbound(sourceRel: String, canCreate: Boolean, dirDoc: Map<String, String>?, forceFocus: Boolean) {
        attach()
        if (!useJcef()) {
            currentDocRel = null
            isHome = false
            isCoverage = false
            unboundSource = sourceRel
            val extra = dirDoc?.let { "<p>目录文档：${esc(it["doc"].orEmpty())}</p>" } ?: ""
            setFallback(fallbackHtml("<p>尚未绑定：<b>${esc(sourceRel)}</b></p>$extra<p>请用 Tools → CodeBind Docs → Bind Doc to Current File。</p>"))
            return
        }
        currentDocRel = null
        isHome = false
        isCoverage = false
        unboundSource = sourceRel
        if (!navigatingHistory) push(NavEntry.Unbound(sourceRel))
        post(
            mapOf(
                "type" to "unbound",
                "sourceRel" to sourceRel,
                "canCreate" to canCreate,
                "dirDoc" to dirDoc,
            ) + navFlags(),
        )
        @Suppress("UNUSED_PARAMETER")
        val unused = forceFocus
    }

    fun refreshCatalogIfOpen(): Boolean {
        if (isHome) {
            showHome(false)
            return true
        }
        if (isCoverage) {
            showCoverage(false)
            return true
        }
        return false
    }

    fun releaseDoc(docRel: String) {
        if (currentDocRel == normalizeRelPath(docRel)) {
            currentDocRel = null
            header = null
        }
    }

    private fun handleMessage(payload: String) {
        val jsonText = payload.trim().let {
            val start = it.indexOf('{')
            if (start >= 0) it.substring(start) else it
        }
        val now = System.currentTimeMillis()
        if (jsonText == lastBridgePayload && now - lastBridgeAt < 400) return
        lastBridgePayload = jsonText
        lastBridgeAt = now
        val obj = try {
            gson.fromJson(jsonText, JsonObject::class.java)
        } catch (_: Exception) {
            return
        }
        when (obj.get("type")?.asString) {
            "ready" -> {
                ready = true
                pendingPayload?.let { post(it) }
                pendingPayload = null
            }
            "markdownChanged" -> scheduleSave(obj.get("markdown")?.asString.orEmpty())
            "switchMode" -> {
                val mode = obj.get("mode")?.asString ?: "ir"
                svc.settings.stored.docPaneMode = if (mode == "source") "source" else "ir"
                obj.get("markdown")?.asString?.let { scheduleSave(it) }
            }
            "createBind" -> svc.commands.bindCurrentFile(obj.get("sourceRel")?.asString)
            "navHome" -> showHome(true)
            "navCoverage" -> showCoverage(true)
            "navBack" -> if (historyIndex > 0) goHistory(-1) else if (!isHome) showHome(true)
            "navForward" -> goHistory(1)
            "openDoc" -> svc.commands.openDoc(obj.get("docRel")?.asString)
            "deleteDoc" -> svc.commands.deleteDoc(obj.get("docRel")?.asString)
            "openTarget" -> svc.commands.revealSourceRange(
                obj.get("sourceRel")?.asString,
                obj.get("startLine")?.asInt,
                obj.get("endLine")?.asInt,
                when (obj.get("kind")?.asString) {
                    "range" -> BindingKind.RANGE
                    "directory" -> BindingKind.DIRECTORY
                    else -> BindingKind.FILE
                },
            )
            "rebindDoc" -> svc.commands.rebindDoc(obj.get("docRel")?.asString)
            "retightenRange" -> svc.commands.retightenRange(obj.get("docRel")?.asString)
            "refreshHash" -> svc.commands.refreshDocHash(obj.get("docRel")?.asString)
            "refreshAllHashes" -> svc.commands.refreshAllDocHashes()
            "saveAsset" -> saveAsset(obj)
        }
    }

    private fun saveAsset(obj: JsonObject) {
        val store = svc.storeOrNull() ?: return
        val requestId = obj.get("requestId")?.asString ?: return
        val fileName = safeAssetFileName(obj.get("fileName")?.asString ?: "paste.png")
        val base64 = obj.get("base64")?.asString ?: return
        try {
            store.ensureLayout()
            var dest = store.workspacePath("${store.assetsPath}/$fileName")
            if (dest.exists()) dest = store.workspacePath("${store.assetsPath}/${System.currentTimeMillis()}-$fileName")
            Files.createDirectories(dest.parent)
            Files.write(dest, Base64.getDecoder().decode(base64))
            val rel = store.toWorkspaceRelative(dest) ?: return
            val mdPath = currentDocRel?.let { relativeToDoc(it, rel) } ?: rel
            post(
                mapOf(
                    "type" to "assetSaved",
                    "requestId" to requestId,
                    "ok" to true,
                    "mdPath" to mdPath,
                    "previewSrc" to dest.toUri().toString(),
                ),
            )
        } catch (err: Exception) {
            post(mapOf("type" to "assetSaved", "requestId" to requestId, "ok" to false, "error" to (err.message ?: "保存失败")))
        }
    }

    private fun scheduleSave(markdown: String) {
        val docRel = currentDocRel ?: return
        alarm.cancelAllRequests()
        alarm.addRequest({ persist(docRel, markdown) }, 400)
    }

    private fun persist(docRel: String, markdown: String) {
        val store = svc.storeOrNull() ?: return
        var body = rewriteImagesForDisk(markdown, imageReverse)
        body = unprotectHrInFences(body)
        body = collapseDocIncludes(body)
        val content = joinMarkdown(header, if (body.endsWith("\n")) body else "$body\n")
        store.workspacePath(docRel).writeText(content, Charsets.UTF_8)
    }

    private fun goHistory(delta: Int) {
        val next = historyIndex + delta
        if (next !in history.indices) return
        navigatingHistory = true
        historyIndex = next
        try {
            when (val e = history[next]) {
                NavEntry.Home -> showHome(false)
                NavEntry.Coverage -> showCoverage(false)
                is NavEntry.Doc -> showDoc(e.docRel, false)
                is NavEntry.Unbound -> showUnbound(e.sourceRel, true, null, false)
            }
        } finally {
            navigatingHistory = false
        }
    }

    private fun push(entry: NavEntry) {
        val cur = history.getOrNull(historyIndex)
        if (cur == entry) return
        while (history.size > historyIndex + 1) history.removeAt(history.lastIndex)
        history += entry
        historyIndex = history.lastIndex
    }

    private fun navFlags(): Map<String, Any?> = mapOf(
        "canBack" to (historyIndex > 0 || !isHome),
        "canForward" to (historyIndex >= 0 && historyIndex < history.lastIndex),
    )

    private fun enqueueHostMessage(payload: String) {
        ApplicationManager.getApplication().invokeLater(
            { handleMessage(payload) },
            ModalityState.nonModal(),
        )
    }

    private fun injectBridge() {
        val q = jsQuery ?: return
        val inject = q.inject("payload")
        val js = """
            window.cbdHost = {
              postMessage: function(msg) {
                var payload = (typeof msg === 'string') ? msg : JSON.stringify(msg);
                try { __CBD_INJECT__; } catch (e) { console.log('__CBD_MSG__' + payload); }
              },
              getState: function() { return window.__cbdState || {}; },
              setState: function(s) { window.__cbdState = s; }
            };
            window.cbdReceive = function(msg) {
              window.dispatchEvent(new MessageEvent('message', { data: msg }));
            };
            window.cbdOutlineEnable = ${svc.settings.stored.docPaneOutline};
        """.trimIndent().replace("__CBD_INJECT__", inject)
        browser?.cefBrowser?.executeJavaScript(js, browser?.cefBrowser?.url, 0)
    }

    private fun post(payload: Map<String, Any?>) {
        if (!useJcef()) return
        if (!ready) {
            pendingPayload = payload
            return
        }
        val json = gson.toJson(payload)
        browser?.cefBrowser?.executeJavaScript("window.cbdReceive && window.cbdReceive($json);", browser?.cefBrowser?.url, 0)
    }

    private fun fallbackHtml(body: String): String = """
        <html><body style="font-family:sans-serif;padding:12px;line-height:1.5">
        <p><b>CodeBind Docs</b> 需要 IDE 内嵌浏览器（JCEF）才能即时渲染。</p>
        <p>请到 <b>Settings → Plugins</b> 启用 <b>Web Browser (JCEF)</b>，然后重启 IDE。</p>
        <hr/>
        $body
        </body></html>
    """.trimIndent()

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    override fun dispose() {
        jsQuery = null
        browser?.dispose()
        browser = null
    }

    private sealed class NavEntry {
        data object Home : NavEntry()
        data object Coverage : NavEntry()
        data class Doc(val docRel: String) : NavEntry()
        data class Unbound(val sourceRel: String) : NavEntry()
    }
}

internal object JcefSupport {
    fun isAvailable(): Boolean {
        return try {
            Class.forName("com.intellij.ui.jcef.JBCefApp", true, javaClass.classLoader)
            JBCefApp.isSupported()
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: LinkageError) {
            false
        } catch (_: Exception) {
            false
        }
    }
}

class CbdDocToolWindowFactory : com.intellij.openapi.wm.ToolWindowFactory {
    override fun createToolWindowContent(project: com.intellij.openapi.project.Project, toolWindow: com.intellij.openapi.wm.ToolWindow) {
        val svc = CbdProjectService.getInstance(project)
        svc.markdownPane.attach()
        val content = com.intellij.ui.content.ContentFactory.getInstance().createContent(svc.markdownPane.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
