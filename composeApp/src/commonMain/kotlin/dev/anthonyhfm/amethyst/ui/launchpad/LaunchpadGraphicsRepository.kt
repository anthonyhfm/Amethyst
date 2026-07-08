package dev.anthonyhfm.amethyst.ui.launchpad

import amethyst.composeapp.generated.resources.Res
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import dev.anthonyhfm.amethyst.settings.data.GeneralSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object LaunchpadGraphicsRepository {
    data class DeviceGraphics(
        val buttons: ImageBitmap,
        val device: ImageBitmap,
        val ledspots: ImageBitmap
    )

    data class MidiFighterGraphics(
        val blackDevice: ImageBitmap,
        val whiteDevice: ImageBitmap
    )

    data class GraphicsState(
        val idealised: DeviceGraphics? = null,
        val lp2: DeviceGraphics? = null,
        val lpp: DeviceGraphics? = null,
        val lpp3: DeviceGraphics? = null,
        val lpx: DeviceGraphics? = null,
        val mystrix: DeviceGraphics? = null,
        val midiFighter: MidiFighterGraphics? = null
    )

    private val _graphicsState = MutableStateFlow(GraphicsState())
    val graphicsState: StateFlow<GraphicsState> = _graphicsState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            GeneralSettings.simplifiedGraphics.flow.collect { useSimplified ->
                loadGraphics(useSimplified)
            }
        }
    }

    private suspend fun loadGraphics(useSimplified: Boolean) {
        val suffix = if (useSimplified) "anth" else "ml"

        val idealised = try {
            DeviceGraphics(
                buttons = Res.readBytes("files/launchpad/Idealised/Idealised_Buttons_Layer_$suffix.png").decodeToImageBitmap(),
                device = Res.readBytes("files/launchpad/Idealised/Idealised_Device_Layer_$suffix.png").decodeToImageBitmap(),
                ledspots = Res.readBytes("files/launchpad/Idealised/Idealised_Spots_Layer_$suffix.png").decodeToImageBitmap()
            )
        } catch (ex: Exception) {
            DeviceGraphics(
                buttons = Res.readBytes("files/launchpad/Idealised/Idealised_Buttons_Layer_anth.png").decodeToImageBitmap(),
                device = Res.readBytes("files/launchpad/Idealised/Idealised_Device_Layer_anth.png").decodeToImageBitmap(),
                ledspots = Res.readBytes("files/launchpad/Idealised/Idealised_Spots_Layer_anth.png").decodeToImageBitmap()
            )
        }

        val lp2 = try {
            DeviceGraphics(
                buttons = Res.readBytes("files/launchpad/LP2/LP2_Buttons_Layer_$suffix.png").decodeToImageBitmap(),
                device = Res.readBytes("files/launchpad/LP2/LP2_Device_Layer_$suffix.png").decodeToImageBitmap(),
                ledspots = Res.readBytes("files/launchpad/LP2/LP2_Spots_Layer_$suffix.png").decodeToImageBitmap()
            )
        } catch (ex: Exception) {
            DeviceGraphics(
                buttons = Res.readBytes("files/launchpad/LP2/LP2_Buttons_Layer_anth.png").decodeToImageBitmap(),
                device = Res.readBytes("files/launchpad/LP2/LP2_Device_Layer_anth.png").decodeToImageBitmap(),
                ledspots = Res.readBytes("files/launchpad/LP2/LP2_Spots_Layer_anth.png").decodeToImageBitmap()
            )
        }

        val lpp = try {
            DeviceGraphics(
                buttons = Res.readBytes("files/launchpad/LPP/LPP_Buttons_Layer_$suffix.png").decodeToImageBitmap(),
                device = Res.readBytes("files/launchpad/LPP/LPP_Device_Layer_$suffix.png").decodeToImageBitmap(),
                ledspots = Res.readBytes("files/launchpad/LPP/LPP_LED_Spots_$suffix.png").decodeToImageBitmap()
            )
        } catch (ex: Exception) {
            DeviceGraphics(
                buttons = Res.readBytes("files/launchpad/LPP/LPP_Buttons_Layer_anth.png").decodeToImageBitmap(),
                device = Res.readBytes("files/launchpad/LPP/LPP_Device_Layer_anth.png").decodeToImageBitmap(),
                ledspots = Res.readBytes("files/launchpad/LPP/LPP_LED_Spots_anth.png").decodeToImageBitmap()
            )
        }

        val lpp3 = try {
            DeviceGraphics(
                buttons = Res.readBytes("files/launchpad/LPP3/LPP3_Buttons_Layer_$suffix.png").decodeToImageBitmap(),
                device = Res.readBytes("files/launchpad/LPP3/LPP3_Device_Layer_$suffix.png").decodeToImageBitmap(),
                ledspots = Res.readBytes("files/launchpad/LPP3/LPP3_Spots_Layer_$suffix.png").decodeToImageBitmap()
            )
        } catch (ex: Exception) {
            DeviceGraphics(
                buttons = Res.readBytes("files/launchpad/LPP3/LPP3_Buttons_Layer_anth.png").decodeToImageBitmap(),
                device = Res.readBytes("files/launchpad/LPP3/LPP3_Device_Layer_anth.png").decodeToImageBitmap(),
                ledspots = Res.readBytes("files/launchpad/LPP3/LPP3_Spots_Layer_anth.png").decodeToImageBitmap()
            )
        }

        val lpx = try {
            DeviceGraphics(
                buttons = Res.readBytes("files/launchpad/LPX/LPX_Buttons_Layer_$suffix.png").decodeToImageBitmap(),
                device = Res.readBytes("files/launchpad/LPX/LPX_Device_Layer_$suffix.png").decodeToImageBitmap(),
                ledspots = Res.readBytes("files/launchpad/LPX/LPX_Spots_Layer_$suffix.png").decodeToImageBitmap()
            )
        } catch (ex: Exception) {
            DeviceGraphics(
                buttons = Res.readBytes("files/launchpad/LPX/LPX_Buttons_Layer_anth.png").decodeToImageBitmap(),
                device = Res.readBytes("files/launchpad/LPX/LPX_Device_Layer_anth.png").decodeToImageBitmap(),
                ledspots = Res.readBytes("files/launchpad/LPX/LPX_Spots_Layer_anth.png").decodeToImageBitmap()
            )
        }

        val mystrix = try {
            DeviceGraphics(
                buttons = Res.readBytes("files/launchpad/Mystrix/Mystrix_Buttons_Layer_$suffix.png").decodeToImageBitmap(),
                device = Res.readBytes("files/launchpad/Mystrix/Mystrix_Device_Layer_$suffix.png").decodeToImageBitmap(),
                ledspots = Res.readBytes("files/launchpad/Mystrix/Mystrix_Spots_Layer_$suffix.png").decodeToImageBitmap()
            )
        } catch (ex: Exception) {
            DeviceGraphics(
                buttons = Res.readBytes("files/launchpad/Mystrix/Mystrix_Buttons_Layer_anth.png").decodeToImageBitmap(),
                device = Res.readBytes("files/launchpad/Mystrix/Mystrix_Device_Layer_anth.png").decodeToImageBitmap(),
                ledspots = Res.readBytes("files/launchpad/Mystrix/Mystrix_Spots_Layer_anth.png").decodeToImageBitmap()
            )
        }

        val midiFighter = try {
            MidiFighterGraphics(
                blackDevice = Res.readBytes("files/launchpad/MF64/MIDI_Fighter_64_Black_$suffix.png").decodeToImageBitmap(),
                whiteDevice = Res.readBytes("files/launchpad/MF64/MIDI_Fighter_64_White_$suffix.png").decodeToImageBitmap()
            )
        } catch (ex: Exception) {
            MidiFighterGraphics(
                blackDevice = Res.readBytes("files/launchpad/MF64/MIDI_Fighter_64_Black_anth.png").decodeToImageBitmap(),
                whiteDevice = Res.readBytes("files/launchpad/MF64/MIDI_Fighter_64_White_anth.png").decodeToImageBitmap()
            )
        }

        _graphicsState.value = GraphicsState(
            idealised = idealised,
            lp2 = lp2,
            lpp = lpp,
            lpp3 = lpp3,
            lpx = lpx,
            mystrix = mystrix,
            midiFighter = midiFighter
        )
    }
}
