package com.intellij.dts.util

import com.intellij.dts.clion.DtsCLionUtil
import com.intellij.dts.lang.DtsTokenSets
import com.intellij.dts.lang.psi.DtsTypes
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import java.util.*

object DtsUtil {
    private val clionUtil: DtsCLionUtil?
        get() = DtsCLionUtil.EP_NAME.extensions.firstOrNull()

    fun isCMakeAvailable(project: Project): Boolean = clionUtil?.isCMakeAvailable(project) ?: false

    /**
     * Splits the name of a node into node and unit address part. If the name
     * does not contain a unit address null will be returned. If the name
     * contains multiple @ the last one will be used as the separator.
     */
    fun splitName(name: String): Pair<String, String?> {
        return if (name.contains("@")) {
            val splitIndex = name.indexOfLast { it == '@' }

            val actualName = name.substring(0, splitIndex)
            val unitAddress = name.substring(splitIndex + 1)

            Pair(actualName, unitAddress)
        } else {
            Pair(name, null)
        }
    }

    /**
     * Iterates over the children of a psi element. If unfiltered is set to true
     * unproductive elements will be skipped (see isProductiveElement).
     */
    fun children(element: PsiElement, forward: Boolean = true, unfiltered: Boolean = false): Sequence<PsiElement> {
        val start = if (forward) element.firstChild else element.lastChild ?: return emptySequence()

        val siblings = start.siblings(forward = forward)
        if (unfiltered) return siblings

        return siblings.filter(::isProductiveElement)
    }

    /**
     * An element is considered productive if it is none of the following:
     * - not null
     * - comment
     * - whit space
     * - any kind of preprocessor statement
     */
    private fun isProductiveElement(element: PsiElement): Boolean {
        val type = element.elementType

        return type != TokenType.WHITE_SPACE &&
               type !in DtsTokenSets.comments &&
               type != DtsTypes.INCLUDE_STATEMENT &&
               type !in DtsTokenSets.ppStatements
    }

    fun <T> singleResult(callback: () -> T?) : List<T> {
        val result = callback()

        return if (result == null) {
            Collections.emptyList<T>()
        } else {
            Collections.singletonList(result)
        }
    }

    private fun nextElement(element: PsiElement, filter: Boolean): PsiElement? {
        val valid = !filter || isProductiveElement(element)
        val notEmpty = element.textLength != 0

        if (valid && notEmpty) {
            val firstChild = element.firstChild
            if (firstChild != null) return firstChild
        }

        val nextSibling = element.nextSibling
        if (nextSibling != null) return nextSibling

        val parent = PsiTreeUtil.findFirstParent(element) { it.nextSibling != null || it is PsiFile }
        if (parent !is PsiFile) return parent?.nextSibling

        return null
    }

    private fun validLeaf(element: PsiElement, filter: Boolean): Boolean {
        val valid = !filter || isProductiveElement(element)
        val notEmpty = element.textLength != 0
        val isLeaf = element.firstChild == null

        return valid && notEmpty && isLeaf
    }

    private tailrec fun nextLeaf(element: PsiElement, filter: Boolean): PsiElement? {
        val next = nextElement(element, filter) ?: return null

        if (validLeaf(next, filter)) {
            return next
        } else {
            return nextLeaf(next, filter)
        }
    }

    fun iterateLeafs(element: PsiElement, filter: Boolean = true, strict: Boolean = true): Sequence<PsiElement> = sequence {
        var current : PsiElement? = if (strict || !validLeaf(element, filter)) {
            nextLeaf(element, filter)
        } else {
            element
        }

        while (current != null) {
            yield(current)
            current = nextLeaf(current, filter)
        }
    }
}