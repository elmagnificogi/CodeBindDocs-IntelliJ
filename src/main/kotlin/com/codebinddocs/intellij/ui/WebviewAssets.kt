package com.codebinddocs.intellij.ui

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginId
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

object WebviewAssets {
    fun extract(): Path {
        val dir = Path.of(PathManager.getPluginTempPath(), "codebinddocs-webview")
        Files.createDirectories(dir)
        val marker = dir.resolve(".stamp")
        val plugin = PluginManagerCore.getPlugin(PluginId.getId("com.codebinddocs.plugin"))
        val stamp = plugin?.version ?: "dev"
        copyResource("/webview/pane.html", dir.resolve("pane.html"))
        copyResource("/webview/pane.css", dir.resolve("pane.css"))
        copyResource("/webview/pane.js", dir.resolve("pane.js"))
        if (!(marker.exists() && marker.toFile().readText() == stamp && dir.resolve("vditor").exists())) {
            copyTree("/webview/vditor", dir.resolve("vditor"))
            marker.toFile().writeText(stamp)
        }
        return dir
    }

    private fun copyResource(classpath: String, dest: Path) {
        val stream = javaClass.getResourceAsStream(classpath) ?: return
        stream.use {
            Files.createDirectories(dest.parent)
            Files.copy(it, dest, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun copyTree(classpathDir: String, dest: Path) {
        val url = javaClass.getResource(classpathDir) ?: return
        if (url.protocol == "file") {
            val src = Path.of(url.toURI())
            if (src.exists()) copyDir(src, dest)
            return
        }
        if (url.protocol == "jar") {
            val path = url.toString()
            val bang = path.indexOf("!")
            val jarPath = java.net.URI(path.substring(4, bang)).path
            java.util.jar.JarFile(jarPath).use { jar ->
                val prefix = classpathDir.trimStart('/')
                jar.entries().asSequence().filter { it.name.startsWith(prefix) && !it.isDirectory }.forEach { entry ->
                    val rel = entry.name.removePrefix(prefix).trimStart('/')
                    val out = dest.resolve(rel)
                    jar.getInputStream(entry).use { input ->
                        Files.createDirectories(out.parent)
                        Files.copy(input, out, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
    }

    private fun copyDir(src: Path, dest: Path) {
        Files.walk(src).use { stream ->
            stream.forEach { p ->
                val rel = src.relativize(p)
                val target = dest.resolve(rel.toString())
                if (Files.isDirectory(p)) Files.createDirectories(target)
                else {
                    Files.createDirectories(target.parent)
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}
