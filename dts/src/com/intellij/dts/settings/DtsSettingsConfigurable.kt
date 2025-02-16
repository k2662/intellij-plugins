package com.intellij.dts.settings

import com.intellij.dts.DtsBundle
import com.intellij.dts.util.DtsUtil
import com.intellij.dts.util.Either
import com.intellij.dts.zephyr.DtsZephyrBoard
import com.intellij.dts.zephyr.DtsZephyrRoot
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.CheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import org.jetbrains.annotations.Nls
import java.nio.file.InvalidPathException
import java.nio.file.Path

class DtsSettingsConfigurable(private val project: Project) : BoundSearchableConfigurable(
    displayName = DtsBundle.message("settings.name"),
    helpTopic = "devitree.zephyr.settings",
    _id = "com.intellij.dts.settings.DtsSettingsConfigurable",
) {
    private val state: DtsSettings.State = DtsSettings.of(project).state

    private val enableSync = DtsUtil.isCMakeAvailable(project)

    private fun validateRoot(path: String): Result<String> {
        if (path.isBlank()) {
            val root = DtsZephyrRoot.searchForRoot(project) ?: return Either.Left(DtsBundle.message("settings.zephyr.root.not_found"))
            return Either.Right(root.path)
        } else {
            val root = try {
                VfsUtil.findFile(Path.of(path), true)
            } catch (_: InvalidPathException) {
                null
            } ?: return Either.Left(DtsBundle.message("settings.zephyr.root.not_found"))

            if (!DtsZephyrRoot.isValid(root)) {
                return Either.Left(DtsBundle.message("settings.zephyr.root.invalid"))
            }

            return Either.Right(path)
        }
    }

    private fun validateBoard(path: String): Result<String> {
        return if (DtsZephyrBoard(path).virtualFile == null) {
            Either.Left(DtsBundle.message("settings.zephyr.board.not_found"))
        } else {
            Either.Right(path)
        }
    }

    override fun createPanel(): DialogPanel = panel {
        val syncInput = CheckBox(DtsBundle.message("settings.zephyr.sync_with_cmake"))
        syncInput.isEnabled = enableSync

        val rootInput = RootComboBox(disposable)
        val boardInput = BoardComboBox(disposable, state.zephyrBoard)

        val rootStatus = object : DtsSettingsInputStatus<String, String>(disposable) {
            init {
                isEnabled = false
            }

            override fun readState(): String {
                val state = rootInput.text

                if (state.isBlank()) {
                    rootInput.setEmptyText(DtsBundle.message("settings.zephyr.root.detecting.ongoing"))
                } else {
                    rootInput.setEmptyText("")
                }

                return state
            }

            override fun performCheck(state: String): Result<String> = validateRoot(state)

            override fun evaluate(state: String, result: Result<String>): ValidationInfo? {
                if (state.isBlank()) {
                    result.fold({
                        rootInput.setEmptyText(DtsBundle.message("settings.zephyr.root.detecting.failed"))
                    }, {
                        rootInput.setEmptyText(DtsBundle.message("settings.zephyr.root.detecting.success", it))
                    })
                }

                return super.evaluate(state, result)
            }
        }

        val boardStatus = object : DtsSettingsInputStatus<String, String>(disposable) {
            init {
                isEnabled = false
            }

            override fun readState(): String = boardInput.text

            override fun performCheck(state: String): Result<String> = validateBoard(state)
        }

        val updateBoardList = object : DtsSettingsInputStatus<String, List<String>>(disposable) {
            override fun readState(): String = rootInput.text

            override fun performCheck(state: String): Result<List<String>> = validateRoot(state).mapRight { rootPath ->
                val root = LocalFileSystem.getInstance().findFileByPath(rootPath)
                DtsZephyrRoot.getAllBoardDirs(root).toList()
            }

            override fun evaluate(state: String, result: Result<List<String>>): ValidationInfo? {
                result.fold({
                    boardInput.isEnabled = false
                    boardInput.clearBoards()
                }, { boards ->
                    boardInput.isEnabled = !syncInput.isSelected
                    boardInput.setBoards(boards)
                })

                return super.evaluate(state, result)
            }
        }

        group(DtsBundle.message("settings.zephyr.group")) {
            row {
                cell(syncInput).bind(
                    { input -> input.isSelected },
                    { input, value -> input.isSelected = value && enableSync },
                    state::zephyrCMakeSync.toMutableProperty()
                )
            }
            row(DtsBundle.message("settings.zephyr.root") + ":") {
                cell(rootInput).resizableColumn().bind(
                    { input -> input.text },
                    { input, value -> input.text = value },
                    state::zephyrRoot.toMutableProperty(),
                ).align(AlignX.FILL)
            }
            row(DtsBundle.message("settings.zephyr.board") + ":") {
                cell(boardInput).resizableColumn().bind(
                    { input -> input.text },
                    { input, value -> input.text = value },
                    state::zephyrBoard.toMutableProperty(),
                ).align(AlignX.FILL)
            }
        }

        // add validation listener
        rootStatus.installOn(rootInput)
        rootInput.onTextChanged(rootStatus::check)
        rootInput.onFocusLost(rootStatus::enableAndCheck)

        boardStatus.installOn(boardInput)
        boardInput.onTextChanged(boardStatus::check)
        boardInput.onFocusLost(boardStatus::enableAndCheck)

        rootInput.onTextChanged(updateBoardList::check)

        syncInput.addChangeListener {
            if (syncInput.isSelected) {
                rootInput.isEnabled = false
                boardInput.isEnabled = false
            } else {
                rootInput.isEnabled = !syncInput.isSelected

                // boardInput cannot simply be enabled, need to check for root first
                updateBoardList.check()
            }
        }

        // run initial validation
        rootStatus.check()
        updateBoardList.check()

        if (syncInput.isSelected) {
            rootInput.isEnabled = false
            boardInput.isEnabled = false
        }
    }

    override fun apply() {
        DtsSettings.of(project).update { super.apply() }
    }
}

private class RootComboBox(disposable: Disposable?) : DtsSettingsPathInput<String>(
    disposable,
    DtsBundle.message("settings.zephyr.root.browse"),
) {
    init {
        configure()

        model = CollectionComboBoxModel(listOf(""))
        renderer = SimpleListCellRenderer.create("") {
            DtsBundle.message("settings.zephyr.root.auto_detect", ApplicationInfo.getInstance().versionName)
        }
    }

    fun setEmptyText(text: @Nls String) {
        textField.emptyText.text = text
    }
}

private class BoardComboBox(disposable: Disposable?, initialValue: String) : DtsSettingsPathInput<String>(
    disposable,
    DtsBundle.message("settings.zephyr.board.browse"),
) {
    private val collectionModel = CollectionComboBoxModel<String>(mutableListOf(), initialValue.ifBlank { null })

    init {
        isSwingPopup = false

        configure()

        renderer = SimpleListCellRenderer.create("") { path -> DtsZephyrBoard(path).presentableText }
        model = collectionModel

        // deselect item if text is edited
        onTextChanged {
            selectedItem = null
        }

        textField.emptyText.text = DtsBundle.message("settings.zephyr.board.empty")
    }

    fun clearBoards() = collectionModel.removeAll()

    fun setBoards(boards: List<String>) = collectionModel.replaceAll(boards)
}
