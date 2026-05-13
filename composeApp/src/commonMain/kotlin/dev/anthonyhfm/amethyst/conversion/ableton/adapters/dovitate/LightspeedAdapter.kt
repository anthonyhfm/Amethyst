package dev.anthonyhfm.amethyst.conversion.ableton.adapters.dovitate

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceFileDropList
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiFileImporter
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.runBlocking

class LightspeedAdapter(
    val device: MxDevice,
    val offset: IntOffset,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val idToIndex = mapOf(
            185 to 11, 186 to 12, 187 to 13, 188 to 14, 189 to 24, 190 to 23, 191 to 22, 192 to 21,
            193 to 81, 194 to 73, 195 to 72, 196 to 63, 197 to 44, 198 to 33, 199 to 43, 200 to 42,
            201 to 41, 202 to 34, 203 to 32, 204 to 31, 205 to 84, 206 to 83, 207 to 82, 208 to 74,
            209 to 71, 210 to 64, 211 to 62, 212 to 61, 213 to 54, 214 to 53, 215 to 52, 216 to 51,
            217 to 48, 218 to 47, 219 to 46, 220 to 45, 221 to 38, 222 to 37, 223 to 36, 224 to 35,
            225 to 28, 226 to 27, 227 to 26, 228 to 25, 229 to 18, 230 to 17, 231 to 16, 232 to 15,
            233 to 88, 234 to 87, 235 to 86, 236 to 85, 237 to 78, 238 to 77, 239 to 76, 240 to 75,
            241 to 68, 242 to 67, 243 to 66, 244 to 65, 245 to 58, 246 to 57, 247 to 56, 248 to 55,
            268 to 6,  269 to 5,  270 to 4,
            276 to 98, 277 to 97, 278 to 96, 279 to 95, 280 to 94, 281 to 93, 282 to 92, 283 to 91,
            287 to 80, 288 to 40, 289 to 20, 290 to 89, 291 to 70, 292 to 30, 293 to 60, 294 to 50,
            295 to 10, 296 to 79, 297 to 69, 298 to 59, 299 to 49, 300 to 39, 301 to 29, 302 to 19,
            303 to 8,  304 to 7,  305 to 3,  306 to 2,  307 to 1,
        )

        return listOf(
            GroupChainDeviceState(
                groups = device.fileDropList.fileDropList.items
                    .filter {
                        it.ref.fileRef.path?.value != null
                    }
                    .map {
                        val id = Regex("\\d+").find(it.name?.value ?: "0")?.value?.toIntOrNull()
                        val index = idToIndex[id] ?: 0

                        Group(
                            name = "Lightspeed Entry",
                            stateChain = StateChain(
                                devices = listOfNotNull(
                                    CoordinateFilterChainDeviceState(
                                        filters = listOf(
                                            Pair(
                                                first = index % 10,
                                                second = 9 - index / 10
                                            )
                                        )
                                    ),
                                    getKeyframesFromFileDrop(it)
                                )
                            )
                        )
                    }
            ),
        )
    }

    fun getKeyframesFromFileDrop(drop: MxDeviceFileDropList.FileDropList.MxDFullFileDrop): KeyframesChainDeviceContract.KeyframesChainDeviceState? {
        val fileRef = drop.ref.fileRef

        val palette = AbletonConverter.palette
        val filePath: String = fileRef.resolvePath()

        val data = if (AbletonConverter.isZip) {
            AbletonConverter.zipEntries[filePath]?.data ?: return null
        } else {
            try {
                runBlocking { PlatformFile(filePath).readBytes() }
            } catch (e: Exception) {
                return null
            }
        }

        return MidiFileImporter.loadData(
            data = data,
            palette = palette,
            bpm = AbletonConverter.bpm,
            xyOffset = offset
        )
    }
}

