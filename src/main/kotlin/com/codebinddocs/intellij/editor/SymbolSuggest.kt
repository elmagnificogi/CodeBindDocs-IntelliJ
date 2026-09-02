package com.codebinddocs.intellij.editor

import com.codebinddocs.core.stripSymbolNoise
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace

object SymbolSuggest {
    fun enclosingName(project: Project, file: VirtualFile, startLine: Int, endLine: Int): String? {
        return try {
            ReadAction.nonBlocking<String?> {
                val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@nonBlocking null
                val doc = FileDocumentManager.getInstance().getDocument(file)
                    ?: psiFile.viewProvider.document
                    ?: return@nonBlocking null
                if (doc.lineCount <= 0) return@nonBlocking null
                val from = (startLine - 1).coerceIn(0, doc.lineCount - 1)
                val to = (endLine - 1).coerceIn(from, doc.lineCount - 1)
                var line = from
                var el: PsiElement? = null
                while (line <= to) {
                    val ls = doc.getLineStartOffset(line)
                    val le = doc.getLineEndOffset(line)
                    el = skipWhitespace(psiFile.findElementAt(ls), le)
                    if (el != null && el !is PsiWhiteSpace) break
                    line++
                }
                var current = el
                while (current != null && current !is PsiFile) {
                    if (current is PsiNameIdentifierOwner && isBindableOwner(current)) {
                        val name = stripSymbolNoise(current.name ?: "")
                        if (name.isNotEmpty()) return@nonBlocking name
                    }
                    current = current.parent
                }
                null
            }.executeSynchronously()
        } catch (_: Exception) {
            null
        }
    }

    private fun skipWhitespace(start: PsiElement?, lineEnd: Int): PsiElement? {
        var el = start
        while (el is PsiWhiteSpace && el.textRange.endOffset < lineEnd) {
            el = el.containingFile.findElementAt(el.textRange.endOffset)
        }
        return el
    }

    private fun isBindableOwner(el: PsiElement): Boolean {
        val n = el.javaClass.simpleName
        if (n.contains("Parameter") || n.contains("Variable") || n.contains("Identifier") || n.contains("Field")) {
            return false
        }
        return n.contains("Method") ||
            n.contains("Function") ||
            n.contains("Class") && !n.contains("ClassInitializer") && !n.contains("ClassLiteral") ||
            n.contains("ObjectDeclaration") ||
            n.contains("TypeAlias") ||
            n.contains("Enum") ||
            n.contains("Record") ||
            n.contains("Interface") ||
            n.contains("Trait") ||
            n.contains("Struct")
    }
}
