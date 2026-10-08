package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.LaunchpadPadFilter
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesEntry
import dev.anthonyhfm.amethyst.devices.effects.mask.MaskChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class LaunchpadBindingRemapperTest {
    private val position = Offset(x = 20f, y = 30f)

    private fun remapper(
        from: LaunchpadLayout,
        to: LaunchpadLayout,
        rotationDegrees: Float = 0f,
    ) = LaunchpadBindingRemapper(
        launchpadId = "target",
        position = position,
        oldLayout = from,
        newLayout = to,
        rotationDegrees = rotationDegrees,
    )

    private fun pad(x: Int, y: Int, id: String = "target") = LaunchpadPadFilter(
        launchpadId = id,
        localX = x,
        localY = y,
    )

    @Test
    fun rotatedMainGridBindingsFollowTheInputCoordinatesForEveryLayoutPair() {
        fun inputPad(layout: LaunchpadLayout, column: Int, row: Int, rotation: Int): Pair<Int, Int> {
            val x = column + layout.mainOffsetX
            val y = row + layout.rows - 1 - layout.mainGridMaxY
            val max = layout.cols - 1
            val rotated = when (rotation) {
                90 -> y to max - x
                180 -> max - x to max - y
                270 -> max - y to x
                else -> x to y
            }
            return rotated.first to layout.rows - 1 - rotated.second
        }

        LaunchpadLayout.entries.forEach { from ->
            LaunchpadLayout.entries.forEach { to ->
                listOf(0, 90, 180, 270).forEach { rotation ->
                    val sourcePads = (0..7).flatMap { column ->
                        (0..7).map { row ->
                            val (x, y) = inputPad(layout = from, column = column, row = row, rotation = rotation)
                            pad(x = x, y = y)
                        }
                    }
                    val expectedPads = (0..7).flatMap { column ->
                        (0..7).map { row ->
                            val (x, y) = inputPad(layout = to, column = column, row = row, rotation = rotation)
                            pad(x = x, y = y)
                        }
                    }
                    val mapper = remapper(from = from, to = to, rotationDegrees = rotation.toFloat())
                    val filter = CoordinateFilterChainDeviceState(padFilters = sourcePads)
                    val mapped = mapper.remap(state = filter)
                    assertEquals(expected = expectedPads, actual = mapped.padFilters)
                    assertEquals(
                        expected = filter,
                        actual = remapper(from = to, to = from, rotationDegrees = rotation.toFloat()).remap(state = mapped),
                    )
                    val sourcePad = sourcePads.first()
                    val targetPad = expectedPads.first()
                    val root = KeyframesChainDeviceState(
                        rootKey = sourcePad.localX + sourcePad.localY * 10,
                        rootKeyLaunchpadId = "target",
                    )
                    val mappedRoot = mapper.remap(state = root)
                    assertEquals(expected = targetPad.localX, actual = mappedRoot.rootKeyLocalX)
                    assertEquals(expected = targetPad.localY, actual = mappedRoot.rootKeyLocalY)
                    val autoplay = AutoPlayData(
                        actions = mapOf(
                            0.0 to listOf(
                                AutoPlayData.Action(
                                    x = position.x.toInt() + sourcePad.localX,
                                    y = position.y.toInt() + sourcePad.localY,
                                    down = true,
                                    launchpadId = "target",
                                ),
                            ),
                        ),
                    )
                    val action = mapper.remap(data = autoplay).actions.getValue(key = 0.0).single()
                    assertEquals(expected = position.x.toInt() + targetPad.localX, actual = action.x)
                    assertEquals(expected = position.y.toInt() + targetPad.localY, actual = action.y)
                }
            }
        }
    }

    @Test
    fun everyLayoutPairKeepsAll64MainPadsAtTheirRelativeGridPositions() {
        LaunchpadLayout.entries.forEach { from ->
            LaunchpadLayout.entries.forEach { to ->
                val sourcePads = (0..7).flatMap { x ->
                    (0..7).map { y ->
                        pad(x = from.mainOffsetX + x, y = from.mainOffsetY + y)
                    }
                }
                val expectedPads = (0..7).flatMap { x ->
                    (0..7).map { y ->
                        pad(x = to.mainOffsetX + x, y = to.mainOffsetY + y)
                    }
                }
                val state = CoordinateFilterChainDeviceState(padFilters = sourcePads)
                val mapped = remapper(from = from, to = to).remap(state = state)

                assertEquals(expected = expectedPads, actual = mapped.padFilters)
                assertEquals(
                    expected = state,
                    actual = remapper(from = to, to = from).remap(state = mapped),
                )
            }
        }
    }

    @Test
    fun surroundingBindingsRemainDistinctAndRoundTripThroughSmallerLayouts() {
        val source = CoordinateFilterChainDeviceState(
            padFilters = listOf(
                pad(x = 0, y = 4),
                pad(x = 4, y = 9),
                pad(x = 9, y = 4),
                pad(x = 1, y = 8, id = "other"),
            ),
        )
        val small = remapper(
            from = LaunchpadLayout.LAYOUT_10X10,
            to = LaunchpadLayout.LAYOUT_8X8,
        ).remap(state = source)

        assertEquals(expected = -1 to 3, actual = small.padFilters.first().let { it.localX to it.localY })
        assertEquals(expected = source.padFilters.last(), actual = small.padFilters.last())

        val medium = remapper(
            from = LaunchpadLayout.LAYOUT_8X8,
            to = LaunchpadLayout.LAYOUT_9X9,
        ).remap(state = small)
        val restored = remapper(
            from = LaunchpadLayout.LAYOUT_9X9,
            to = LaunchpadLayout.LAYOUT_10X10,
        ).remap(state = medium)

        assertEquals(expected = source, actual = restored)
    }

    @Test
    fun legacyPeripheralBindingsGainStableAnchorsBeforeLeavingTheLayout() {
        val source = CoordinateFilterChainDeviceState(filters = listOf(20 to 34, 24 to 39, 99 to 99))
        val swapped = remapper(
            from = LaunchpadLayout.LAYOUT_10X10,
            to = LaunchpadLayout.LAYOUT_9X9,
        ).remap(state = source)

        assertEquals(expected = listOf(99 to 99), actual = swapped.globalFilters)
        assertEquals(
            expected = listOf(-1 to 4, 3 to 9),
            actual = swapped.padFilters.map { it.localX to it.localY },
        )

        val restored = remapper(
            from = LaunchpadLayout.LAYOUT_9X9,
            to = LaunchpadLayout.LAYOUT_10X10,
        ).remap(state = swapped)

        assertEquals(
            expected = listOf(0 to 4, 4 to 9),
            actual = restored.padFilters.map { it.localX to it.localY },
        )
    }

    @Test
    fun keyframeEdgesRootAndAutoplaySurviveSerializationAndRoundTrip() {
        val anchored = KeyframesEntry(
            x = 20,
            y = 34,
            r = 1f,
            g = 0f,
            b = 0f,
            launchpadId = "target",
            localX = 0,
            localY = 4,
            abletonPitch = 36,
        )
        val legacy = KeyframesEntry(x = 20, y = 34, r = 0f, g = 1f, b = 0f)
        val state = KeyframesChainDeviceState(
            rootKey = 40,
            rootKeyLaunchpadId = "target",
            frames = listOf(
                Frame(
                    timing = Timing.Duration(duration = 100.milliseconds),
                    entries = listOf(anchored, legacy),
                ),
            ),
        )
        val forward = remapper(from = LaunchpadLayout.LAYOUT_10X10, to = LaunchpadLayout.LAYOUT_9X9)
        val backward = remapper(from = LaunchpadLayout.LAYOUT_9X9, to = LaunchpadLayout.LAYOUT_10X10)
        val persisted = Json.decodeFromString<KeyframesChainDeviceState>(
            string = Json.encodeToString(value = forward.remap(state = state)),
        )

        assertEquals(expected = -1, actual = persisted.rootKeyLocalX)
        assertEquals(expected = 4, actual = persisted.rootKeyLocalY)

        val restored = backward.remap(state = persisted)

        assertEquals(expected = 40, actual = restored.rootKey)
        assertEquals(
            expected = 0 to 4,
            actual = restored.frames.single().entries.last().let { it.localX to it.localY },
        )
        assertEquals(expected = anchored, actual = restored.frames.single().entries.first())

        val legacyAction = AutoPlayData.Action(x = 20, y = 34, down = true, beforeNotes = true)
        val otherAction = AutoPlayData.Action(x = 70, y = 80, down = false, launchpadId = "other")
        val autoplay = AutoPlayData(actions = mapOf(0.0 to listOf(legacyAction, otherAction)))
        val restoredActions = backward.remap(data = forward.remap(data = autoplay)).actions.getValue(key = 0.0)

        assertEquals(expected = legacyAction.copy(launchpadId = "target"), actual = restoredActions.first())
        assertEquals(expected = otherAction, actual = restoredActions.last())
    }

    @Test
    fun nestedPersistedChainsIncludePreprocessorsMasksAndChokesWithoutChangingIdentities() {
        val filter = CoordinateFilterChainDeviceState(padFilters = listOf(pad(x = 0, y = 8))).apply {
            isMuted = true
        }
        val leaf = StateChain(
            devices = listOf(filter),
            mutedDeviceIndices = listOf(0),
            deviceIds = listOf("filter-id"),
        )
        val choke = ChokeChainDeviceState(stateChain = leaf)
        val mask = MaskChainDeviceState(
            colorStateChain = leaf,
            shapeStateChain = StateChain(devices = listOf(choke)),
        )
        val innerGroup = GroupChainDeviceState(
            groups = listOf(
                Group(name = "inner", stateChain = StateChain(devices = listOf(mask))),
            ),
        )
        val nested = StateChain(
            devices = listOf(
                MultiGroupChainDeviceState(
                    preprocessChain = leaf,
                    groups = listOf(
                        Group(name = "group", stateChain = StateChain(devices = listOf(innerGroup))),
                    ),
                ),
            ),
            deviceIds = listOf("multi-id"),
        )
        val swapped = remapper(
            from = LaunchpadLayout.LAYOUT_9X9,
            to = LaunchpadLayout.LAYOUT_10X10,
        ).remap(chain = nested)
        val multi = swapped.devices.single() as MultiGroupChainDeviceState
        val swappedFilter = multi.preprocessChain.devices.single() as CoordinateFilterChainDeviceState

        assertEquals(expected = nested.deviceIds, actual = swapped.deviceIds)
        assertEquals(expected = leaf.deviceIds, actual = multi.preprocessChain.deviceIds)
        assertEquals(expected = 1 to 8, actual = swappedFilter.padFilters.single().let { it.localX to it.localY })
        assertTrue(actual = swappedFilter.isMuted)

        val restored = remapper(
            from = LaunchpadLayout.LAYOUT_10X10,
            to = LaunchpadLayout.LAYOUT_9X9,
        ).remap(chain = swapped)

        assertEquals(expected = nested, actual = restored)
    }
}
