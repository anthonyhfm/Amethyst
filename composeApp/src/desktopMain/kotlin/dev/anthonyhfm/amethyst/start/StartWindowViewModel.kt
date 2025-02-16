package dev.anthonyhfm.amethyst.start

import androidx.lifecycle.ViewModel
import java.awt.Desktop
import java.net.URI

class StartWindowViewModel : ViewModel() {
    fun openGitHubWebsite() {
        Desktop.getDesktop().browse(
            URI("https://github.com/anthonyhfm/amethyst")
        )
    }

    fun onClickCreateProject() {

    }

    fun onClickOpenProject() {

    }
}