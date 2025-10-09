package dev.anthonyhfm.amethyst.home.ui.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi

class AbletonImportWizardViewModel: ViewModel() {
    val customPalettePath: MutableStateFlow<String> = MutableStateFlow("")

    // TODO: add things like midiext save0 file path etc.

    @OptIn(ExperimentalSerializationApi::class)
    fun onClickImportCustomPalette() {
        viewModelScope.launch {
            val file = FileKit.openFilePicker(
                type = FileKitType.File(),
                mode = FileKitMode.Single,
            )

            file?.path?.let { path ->
                customPalettePath.value = path
            }
        }
    }

    fun startAbletonImport(path: String) {
        val workspace = AbletonConverter.convertToWorkspace(path, customPalettePath.value.takeIf { it.isNotEmpty() })

        WorkspaceRepository.loadWorkspace(workspace)
    }
}