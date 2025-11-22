package dev.anthonyhfm.amethyst.home.ui.views

import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dev.anthonyhfm.amethyst.conversion.unipad.UnipadConverter
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.core.util.BaseViewModel
import dev.anthonyhfm.amethyst.core.util.FileHelper
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat
import dev.anthonyhfm.amethyst.core.util.determineFormat
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class RecentViewModel(
    private val navigator: NavHostController
) : BaseViewModel<Nothing?, RecentViewContract.Event, RecentViewContract.Effect>(null) {
    @OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
    override fun onEvent(event: RecentViewContract.Event) {
        when (event) {
            is RecentViewContract.Event.OnClickOpenProject -> {
                viewModelScope.launch {
                    val file = FileKit.openFilePicker(
                        type = FileKitType.File(
                            extensions = if (platform is Platform.Desktop) {
                                listOf("amproj", "als", "approj", "zip")
                            } else {
                                listOf("amproj", "zip")
                            }
                        ),
                        title = "Open Project File"
                    )

                    if (file == null) return@launch

                    when (file.extension) {
                        "amproj" -> { // Native Amethyst Projects
                            navigator.navigate(HomeNavRoute.LoadingScreen("Loading Project"))

                            GlobalScope.launch {
                                val workspace = AmethystProtoBuf.decodeFromByteArray<SaveableWorkspaceData>(file.readBytes())

                                WorkspaceRepository.loadWorkspace(workspace)
                                triggerEffect(RecentViewContract.Effect.OpenWorkspace)
                            }
                        }

                        "als" -> { // Ableton Live-Sets
                            navigator.navigate(HomeNavRoute.AbletonImportWizard(file.absolutePath()))
                        }

                        "approj" -> { // Apollo Projects

                        }

                        "zip" -> {
                            val format = Zip.determineFormat(file)

                            if (platform is Platform.Android || platform is Platform.iOS) {
                                FileHelper.indexedFiles[file.path] = file
                            }

                            when (format) {
                                ZippedProjectFormat.ABLETON -> {
                                    navigator.navigate(HomeNavRoute.AbletonImportWizard(file.path))
                                }

                                ZippedProjectFormat.ABLETON_APOLLO -> {
                                    navigator.navigate(HomeNavRoute.AbletonImportWizard(file.path))
                                }

                                ZippedProjectFormat.UNIPAD -> {
                                    FileHelper.clearCache()

                                    navigator.navigate(HomeNavRoute.LoadingScreen("Translating your UniPad Project"))

                                    GlobalScope.launch {
                                        val workspace = UnipadConverter.convertZipToWorkspace(file)

                                        WorkspaceRepository.loadWorkspace(workspace)
                                        triggerEffect(RecentViewContract.Effect.OpenWorkspace)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is RecentViewContract.Event.OnClickNewProject -> {
                WorkspaceRepository.loadWorkspace(
                    SaveableWorkspaceData(
                        launchpadDevices = listOf(
                            SaveableWorkspaceData.SavableViewportLaunchpad(0f, 0f, SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO)
                        )
                    )
                )

                triggerEffect(RecentViewContract.Effect.OpenWorkspace)
            }

            is RecentViewContract.Event.OpenProjectFromHistory -> {
                val file = PlatformFile(event.project.path)

                val workspace = runBlocking {
                    AmethystProtoBuf.decodeFromByteArray<SaveableWorkspaceData>(file.readBytes())
                }

                GlobalSettings.recentWorkspaces += event.project.copy(lastOpened = Clock.System.now().toEpochMilliseconds())
                WorkspaceRepository.loadWorkspace(workspace)

                triggerEffect(RecentViewContract.Effect.OpenWorkspace)
            }
        }
    }
}

sealed interface RecentViewContract {
    sealed interface Event {
        data object OnClickOpenProject : Event
        data object OnClickNewProject : Event

        data class OpenProjectFromHistory(
            val project: RecentWorkspace
        ): Event
    }

    sealed interface Effect {
        data object OpenWorkspace : Effect
    }
}