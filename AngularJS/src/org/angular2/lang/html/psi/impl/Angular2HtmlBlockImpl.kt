// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.angular2.lang.html.psi.impl

import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.formatter.xml.AbstractXmlBlock
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import com.intellij.psi.xml.XmlText
import com.intellij.psi.xml.XmlTokenType
import com.intellij.util.takeWhileInclusive
import org.angular2.codeInsight.blocks.Angular2HtmlBlockDefinition
import org.angular2.codeInsight.blocks.Angular2HtmlBlockUtils.toCanonicalBlockName
import org.angular2.codeInsight.blocks.getAngular2HtmlBlocksConfig
import org.angular2.lang.expr.psi.Angular2BlockParameter
import org.angular2.lang.html.lexer.Angular2HtmlTokenTypes
import org.angular2.lang.html.parser.Angular2HtmlElementTypes
import org.angular2.lang.html.psi.Angular2HtmlBlock
import org.angular2.lang.html.psi.Angular2HtmlBlockContents
import org.angular2.lang.html.psi.Angular2HtmlBlockParameters
import org.angular2.lang.html.psi.Angular2HtmlElementVisitor

class Angular2HtmlBlockImpl(type: Angular2HtmlElementTypes.Angular2ElementType)
  : Angular2HtmlCompositePsiElement(type), Angular2HtmlBlock {

  override fun getName(): String =
    findChildByType(Angular2HtmlTokenTypes.BLOCK_NAME)
      ?.text?.toCanonicalBlockName()!!

  override val parameters: List<Angular2BlockParameter>
    get() = PsiTreeUtil.findChildOfType(this, Angular2HtmlBlockParameters::class.java)
              ?.parameters ?: emptyList()

  override val contents: Angular2HtmlBlockContents?
    get() = PsiTreeUtil.findChildOfType(this, Angular2HtmlBlockContents::class.java)

  override val definition: Angular2HtmlBlockDefinition?
    get() = getAngular2HtmlBlocksConfig(this)[this]

  override val isPrimary: Boolean
    get() = definition?.isPrimary == true

  override val primaryBlockDefinition: Angular2HtmlBlockDefinition?
    get() = getAngular2HtmlBlocksConfig(this).let { config ->
      config[config[this]?.primaryBlock ?: getName().takeIf { config[this]?.isPrimary == true }]
    }

  override val primaryBlock: Angular2HtmlBlock?
    get() =
      blockSiblingsBackward().lastOrNull()?.takeIf { it.isPrimary }

  override fun blockSiblingsForward(): Sequence<Angular2HtmlBlock> {
    val blocksConfig = getAngular2HtmlBlocksConfig(this)
    val primaryBlockName = blocksConfig[this]?.let { if (it.isPrimary) getName() else it.primaryBlock }
    return siblings(true, false)
      .filter { !AbstractXmlBlock.containsWhiteSpacesOnly(it.node) && it.node.getTextLength() > 0 }
      .takeWhile { it is Angular2HtmlBlock && blocksConfig[it]?.primaryBlock == primaryBlockName }
      .filterIsInstance<Angular2HtmlBlock>()
      .takeWhileInclusive { blocksConfig[it]?.last != true }
  }

  override fun blockSiblingsBackward(): Sequence<Angular2HtmlBlock> {
    val blocksConfig = getAngular2HtmlBlocksConfig(this)
    val primaryBlockName = blocksConfig[this]?.let { if (it.isPrimary) return emptySequence() else it.primaryBlock }
    return siblings(false, false)
      .filter { element ->
        element.elementType != XmlTokenType.XML_WHITE_SPACE && element != XmlTokenType.XML_REAL_WHITE_SPACE
        && (element !is XmlText || !element.text.all { it.isWhitespace() })
      }
      .takeWhile {
        it is Angular2HtmlBlock && blocksConfig[it].let { def ->
          def != null && ((def.isPrimary && it.getName() == primaryBlockName) || def.primaryBlock == primaryBlockName)
        }
      }
      .filterIsInstance<Angular2HtmlBlock>()
      .takeWhileInclusive { blocksConfig[it]?.isPrimary != true }
  }

  override fun accept(visitor: PsiElementVisitor) {
    when (visitor) {
      is Angular2HtmlElementVisitor -> {
        visitor.visitBlock(this)
      }
      else -> {
        visitor.visitElement(this)
      }
    }
  }

  override fun toString(): String =
    super.toString() + " (@$name)"
}