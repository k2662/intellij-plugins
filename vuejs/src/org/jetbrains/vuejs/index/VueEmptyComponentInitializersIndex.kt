// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.vuejs.index

import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.javascript.index.JSSymbolUtil
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSElement
import com.intellij.lang.javascript.psi.JSEmbeddedContent
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.impl.JSPropertyImpl
import com.intellij.lang.javascript.psi.util.getStubSafeChildren
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.util.indexing.*
import com.intellij.util.io.BooleanDataDescriptor
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.vuejs.lang.html.VueFileType
import org.jetbrains.vuejs.libraries.componentDecorator.findComponentDecorator
import org.jetbrains.vuejs.model.hasSrcReference
import org.jetbrains.vuejs.model.source.DEFINE_OPTIONS_FUN
import org.jetbrains.vuejs.model.source.VueComponents
import org.jetbrains.vuejs.model.source.getStubSafeDefineCalls
import java.util.*

class VueEmptyComponentInitializersIndex : ScalarIndexExtension<Boolean>() {

  override fun getName(): ID<Boolean, Void> = VUE_NO_INITIALIZER_COMPONENTS_INDEX

  override fun getIndexer(): DataIndexer<Boolean, Void, FileContent> = DataIndexer { inputData ->
    inputData.psiFile.let { file ->
      file is XmlFile
      && hasEmptyScriptSetupInitializer(findScriptTag(file, true))
      && hasEmptyScriptInitializer(findScriptTag(file, false))
    }.let {
      Collections.singletonMap<Boolean, Void>(it, null)
    }
  }

  private fun hasEmptyScriptInitializer(script: XmlTag?): Boolean =
    if (script == null) {
      true
    }
    else if (script.hasSrcReference()) {
      false
    }
    else {
      val module = PsiTreeUtil.getStubChildOfType(script, JSEmbeddedContent::class.java)
      if (module != null) {
        val exportedElement = module.getStubSafeChildren<ES6ExportDefaultAssignment>().firstOrNull()?.stubSafeElement
        if (exportedElement is JSObjectLiteralExpression || exportedElement is JSCallExpression) {
          hasEmptyDescriptor(exportedElement)
        }
        else
          (exportedElement is JSClass
           && findComponentDecorator(exportedElement) == null)
      }
      else true
    }

  private fun hasEmptyScriptSetupInitializer(script: XmlTag?): Boolean =
    script
      ?.let { PsiTreeUtil.getStubChildOfType(it, JSEmbeddedContent::class.java) }
      ?.getStubSafeDefineCalls()
      ?.find { call ->
        call.methodExpression?.let { JSSymbolUtil.isAccurateReferenceExpressionName(it, DEFINE_OPTIONS_FUN) } ?: false
      }
      ?.let { hasEmptyDescriptor(it) }
    ?: true

  private fun hasEmptyDescriptor(exportedElement: JSElement?) =
    VueComponents.getSourceComponentDescriptor(exportedElement)
      ?.initializer
      ?.asSafely<JSObjectLiteralExpression>()
      ?.properties
      ?.count { it is JSPropertyImpl } == 0

  override fun getVersion(): Int = 11

  override fun getInputFilter(): FileBasedIndex.InputFilter = object : DefaultFileTypeSpecificInputFilter(VueFileType.INSTANCE) {
    override fun acceptInput(file: VirtualFile): Boolean {
      return file.fileType == VueFileType.INSTANCE
    }
  }

  companion object {
    val VUE_NO_INITIALIZER_COMPONENTS_INDEX = ID.create<Boolean, Void>("VueNoScriptFilesIndex")
  }

  override fun getKeyDescriptor(): KeyDescriptor<Boolean> = BooleanDataDescriptor.INSTANCE

  override fun dependsOnFileContent(): Boolean = true

}