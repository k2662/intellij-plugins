// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.angular2.lang.html.psi

import com.intellij.psi.PsiElement
import org.angular2.codeInsight.blocks.Angular2HtmlBlockDefinition
import org.angular2.lang.expr.psi.Angular2BlockParameter

interface Angular2HtmlBlock : PsiElement {

  fun getName(): String

  val parameters: List<Angular2BlockParameter>

  val contents: Angular2HtmlBlockContents?

  val definition: Angular2HtmlBlockDefinition?

  val isPrimary: Boolean

  val primaryBlockDefinition: Angular2HtmlBlockDefinition?

  val primaryBlock: Angular2HtmlBlock?

  fun blockSiblingsForward(): Sequence<Angular2HtmlBlock>

  fun blockSiblingsBackward(): Sequence<Angular2HtmlBlock>

}