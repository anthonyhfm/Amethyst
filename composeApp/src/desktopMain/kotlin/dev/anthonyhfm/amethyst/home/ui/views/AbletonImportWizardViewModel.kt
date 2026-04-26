package dev.anthonyhfm.amethyst.home.ui.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AbletonImportWizardViewModel: ViewModel() {
    val customPalettePath: MutableStateFlow<String> = MutableStateFlow("")
    val apolloProjPath: MutableStateFlow<String> = MutableStateFlow("")

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

    suspend fun startAbletonImport(path: String) {
        HomeRepository.importAbletonProject(
            path = path,
            customPalettePath = customPalettePath.value.takeIf { it.isNotEmpty() },
            apolloProjPath = apolloProjPath.value.takeIf { it.isNotEmpty() },
        )
    }
}
