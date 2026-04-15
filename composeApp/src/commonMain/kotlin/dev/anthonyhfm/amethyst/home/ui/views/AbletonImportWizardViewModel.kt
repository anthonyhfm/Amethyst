package dev.anthonyhfm.amethyst.home.ui.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.apollo.ApolloConverter
import dev.anthonyhfm.amethyst.core.util.FileHelper
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi

class AbletonImportWizardViewModel: ViewModel() {
    val customPalettePath: MutableStateFlow<String> = MutableStateFlow("")
    val apolloProjPath: MutableStateFlow<String> = MutableStateFlow("")

    @OptIn(ExperimentalSerializationApi::class)
    fun onClickImportCustomPalette() {
        viewModelScope.launch {
            val file = FileKit.openFilePicker(
                type = FileKitType.File(),
                mode = FileKitMode.Single,
            )
            file?.path?.let { customPalettePath.value = it }
        }
    }

    fun onClickImportApolloProjFile() {
        viewModelScope.launch {
            val file = FileKit.openFilePicker(
                type = FileKitType.File(extensions = listOf("approj")),
                mode = FileKitMode.Single,
            )
            file?.path?.let { apolloProjPath.value = it }
        }
    }

    fun startAbletonImport(path: String) {
        val importedFile = if (platform is Platform.Android || platform is Platform.iOS) {
            FileHelper.indexedFiles[path] ?: PlatformFile(path)
        } else {
            PlatformFile(path)
        }

        val apolloPath = apolloProjPath.value.takeIf { it.isNotEmpty() }

        val workspace = when {
            apolloPath != null -> {
                val abletonWorkspace = if (importedFile.extension.equals("zip", ignoreCase = true)) {
                    AbletonConverter.convertZipToWorkspace(importedFile)
                } else {
                    AbletonConverter.convertToWorkspace(
                        importedFile,
                        customPalettePath.value.takeIf { it.isNotEmpty() }
                    )
                }
                val apolloWorkspace = ApolloConverter.convertToWorkspace(apolloPath, palettePath = null)
                abletonWorkspace.copy(lights = apolloWorkspace.lights)
            }
            importedFile.extension.equals("zip", ignoreCase = true) -> {
                AbletonConverter.convertZipToWorkspace(importedFile)
            }
            else -> {
                AbletonConverter.convertToWorkspace(
                    importedFile,
                    customPalettePath.value.takeIf { it.isNotEmpty() }
                )
            }
        }

        WorkspaceRepository.loadWorkspace(workspace)
    }
}
