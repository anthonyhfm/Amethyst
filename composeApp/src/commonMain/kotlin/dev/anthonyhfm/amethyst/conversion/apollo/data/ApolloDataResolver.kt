package dev.anthonyhfm.amethyst.conversion.apollo.data

import dev.anthonyhfm.amethyst.conversion.apollo.ApolloConverter

class ApolloDataResolver {
    fun readNextType(reader: ApolloBinaryReader): ApolloModel {
        return when (val type = ApolloTypes.entries[reader.readByte().toInt()]) {
            ApolloTypes.Project -> {
                val bpm = reader.readInt32().also { ApolloConverter.bpm = it }
                val macros = if (ApolloConverter.version >= 25) {
                    List(4) { reader.readInt32() }
                } else {
                    listOf(reader.readInt32(), 1, 1, 1)
                }
                val trackCount = reader.readInt32()
                val tracks = List(trackCount) { readNextType(reader) as ApolloModel.Track }
                val author = if (ApolloConverter.version >= 17) reader.readString() else ""
                val timeSpentSeconds = if (ApolloConverter.version >= 17) reader.readInt64() else 0L
                val startedUnixSeconds = if (ApolloConverter.version >= 17) reader.readInt64() else 0L
                val undoManager = if (ApolloConverter.version >= 30) {
                    readNextType(reader) as ApolloModel.UndoManager
                } else {
                    ApolloModel.UndoManager(version = 0, size = 0)
                }
                ApolloModel.Project(
                    bpm = bpm,
                    macros = macros,
                    tracks = tracks,
                    author = author,
                    timeSpentSeconds = timeSpentSeconds,
                    startedUnixSeconds = startedUnixSeconds,
                    undoManager = undoManager
                )
            }

            ApolloTypes.UndoManager -> {
                val undoVersion = reader.readInt32()
                val size = reader.readInt32()
                reader.skip(size)
                ApolloModel.UndoManager(version = undoVersion, size = size)
            }

            ApolloTypes.Track -> ApolloModel.Track(
                chain = readNextType(reader) as ApolloModel.Chain,
                launchpad = readNextType(reader) as ApolloModel.Launchpad,
                name = reader.readString(),
                enabled = if (ApolloConverter.version >= 8) reader.readBoolean() else true
            )

            ApolloTypes.Launchpad -> {
                val name = reader.readString()
                if (name.isEmpty()) {
                    ApolloModel.Launchpad(name = "", format = 0, rotation = 0)
                } else {
                    val format = if (ApolloConverter.version >= 2) reader.readInt32() else 0
                    val rotation = if (ApolloConverter.version >= 9) reader.readInt32() else 0
                    ApolloModel.Launchpad(name = name, format = format, rotation = rotation)
                }
            }

            ApolloTypes.Chain -> ApolloModel.Chain(
                devices = List(reader.readInt32()) {
                    readNextType(reader) as ApolloModel.DeviceWrapper
                },
                name = reader.readString(),
                enabled = if (ApolloConverter.version >= 6) reader.readBoolean() else true,
                secretMultiFilter = if (ApolloConverter.version >= 29) {
                    List(101) { reader.readBoolean() }
                } else {
                    List(101) { false }
                }
            )

            ApolloTypes.Device -> ApolloModel.DeviceWrapper(
                collapsed = if (ApolloConverter.version >= 5) reader.readBoolean() else false,
                enabled = if (ApolloConverter.version >= 5) reader.readBoolean() else true,
                device = readNextType(reader) as ApolloModel.Device
            )

            ApolloTypes.Color -> {
                val r = reader.readByte()
                val g = reader.readByte()
                val b = reader.readByte()
                ApolloModel.Color(r = r, g = g, b = b)
            }

            ApolloTypes.Length -> ApolloModel.Length(step = reader.readInt32())

            ApolloTypes.Time -> {
                if (ApolloConverter.version <= 2) {
                    val free = reader.readBoolean()
                    val step = reader.readInt32()
                    val divisor = reader.readInt32()
                    ApolloModel.Time(
                        free = free,
                        length = ApolloModel.Length(step = step),
                        divisor = divisor
                    )
                } else {
                    ApolloModel.Time(
                        free = reader.readBoolean(),
                        length = readNextType(reader) as ApolloModel.Length,
                        divisor = reader.readInt32()
                    )
                }
            }

            ApolloTypes.Group -> {
                var expanded = false
                ApolloModel.Device.Group(
                    chains = List(reader.readInt32()) {
                        readNextType(reader) as ApolloModel.Chain
                    },
                    expanded = reader.readBoolean().also { expanded = it },
                    expandedIndex = if (expanded) reader.readInt32() else 0
                )
            }

            ApolloTypes.Choke -> ApolloModel.Device.Choke(
                target = reader.readInt32(),
                chain = readNextType(reader) as ApolloModel.Chain
            )

            ApolloTypes.Offset -> ApolloModel.Offset(
                x = reader.readInt32(),
                y = reader.readInt32(),
                isAbsolute = if (ApolloConverter.version >= 25) reader.readBoolean() else false,
                absoluteX = if (ApolloConverter.version >= 25) reader.readInt32() else 0,
                absoluteY = if (ApolloConverter.version >= 25) reader.readInt32() else 0
            )

            ApolloTypes.Copy -> {
                val time = readNextType(reader) as ApolloModel.Time
                val gate = if (ApolloConverter.version <= 13) reader.readDecimalAsDouble() else reader.readDouble()
                val pinch = if (ApolloConverter.version >= 26) reader.readDouble() else 0.0
                val bilateral = if (ApolloConverter.version >= 28) reader.readBoolean() else false
                val reverse = if (ApolloConverter.version >= 26) reader.readBoolean() else false
                val infinite = if (ApolloConverter.version >= 27) reader.readBoolean() else false
                val mode = reader.readInt32()
                val gridMode = reader.readInt32()
                val wrap = reader.readBoolean()
                val offsetCount = reader.readInt32()
                val offsets = List(offsetCount) { readNextType(reader) as ApolloModel.Offset }
                val angles = List(offsetCount) { if (ApolloConverter.version >= 25) reader.readInt32() else 0 }
                ApolloModel.Device.Copy(
                    time = time, gate = gate, pinch = pinch, bilateral = bilateral,
                    reverse = reverse, infinite = infinite, mode = mode,
                    gridMode = gridMode, wrap = wrap, offsets = offsets, angles = angles
                )
            }

            ApolloTypes.KeyFilter -> {
                val count = if (ApolloConverter.version <= 18) 100 else 101
                val filters = List(count) { reader.readBoolean() }.toMutableList()
                if (ApolloConverter.version <= 18) filters.add(99, false)
                ApolloModel.Device.KeyFilter(filters = filters)
            }

            ApolloTypes.MacroFilter -> ApolloModel.Device.MacroFilter(
                macro = if (ApolloConverter.version >= 25) reader.readInt32() else 1,
                filter = List(100) { reader.readBoolean() }
            )

            ApolloTypes.Delay -> ApolloModel.Device.Delay(
                time = readNextType(reader) as ApolloModel.Time,
                gate = if (ApolloConverter.version <= 13) reader.readDecimalAsDouble() else reader.readDouble()
            )

            ApolloTypes.Hold -> {
                val time = readNextType(reader) as ApolloModel.Time
                val gate = if (ApolloConverter.version <= 13) reader.readDecimalAsDouble() else reader.readDouble()
                val mode = if (ApolloConverter.version <= 31) {
                    if (reader.readBoolean()) 2 else 0
                } else {
                    reader.readInt32()
                }
                val release = reader.readBoolean()
                ApolloModel.Device.Hold(time = time, gate = gate, mode = mode, release = release)
            }

            ApolloTypes.Loop -> ApolloModel.Device.Loop(
                time = readNextType(reader) as ApolloModel.Time,
                gate = reader.readDouble(),
                repeats = reader.readInt32(),
                hold = reader.readBoolean()
            )

            ApolloTypes.Paint -> ApolloModel.Device.Paint(
                color = readNextType(reader) as ApolloModel.Color
            )

            ApolloTypes.Fade -> {
                val time = readNextType(reader) as ApolloModel.Time
                val gate = if (ApolloConverter.version <= 13) reader.readDecimalAsDouble() else reader.readDouble()
                val playMode = reader.readInt32()
                val count = reader.readInt32()
                val colors = List(count) { readNextType(reader) as ApolloModel.Color }
                val positions = List(count) {
                    if (ApolloConverter.version <= 13) reader.readDecimalAsDouble() else reader.readDouble()
                }
                val fadeTypes = if (ApolloConverter.version <= 24) {
                    List(maxOf(0, count - 1)) { 0 }
                } else {
                    List(maxOf(0, count - 1)) { reader.readInt32() }
                }
                var expanded = false
                val hasExpanded = if (ApolloConverter.version >= 23) reader.readBoolean().also { expanded = it } else false
                val expandedIndex = if (expanded) reader.readInt32() else 0
                ApolloModel.Device.Fade(
                    time = time, gate = gate, playMode = playMode, count = count,
                    colors = colors, positions = positions, fadeTypes = fadeTypes,
                    hasExpanded = hasExpanded, expandedIndex = expandedIndex
                )
            }

            ApolloTypes.Preview -> ApolloModel.Device.Preview

            ApolloTypes.Layer -> {
                val target = reader.readInt32()
                var mode = reader.readInt32()
                if (ApolloConverter.version == 5) {
                    mode = when (mode) { 0 -> 0; 1 -> 3; else -> 0 }
                }
                val range = if (ApolloConverter.version >= 21) reader.readInt32() else 200
                ApolloModel.Device.Layer(target = target, mode = mode, range = range)
            }

            ApolloTypes.Pattern -> {
                val repeats = if (ApolloConverter.version >= 11) reader.readInt32() else 1
                val gate = if (ApolloConverter.version <= 13) reader.readDecimalAsDouble() else reader.readDouble()
                val pinch = if (ApolloConverter.version >= 24) reader.readDouble() else 0.0
                val bilateral = if (ApolloConverter.version >= 28) reader.readBoolean() else false
                val frameCount = reader.readInt32()
                val frames = List(frameCount) { readNextType(reader) as ApolloModel.Frame }
                val playbackMode = reader.readInt32()
                if (ApolloConverter.version <= 10) {
                    val chokeEnabled = reader.readBoolean()
                    if (ApolloConverter.version <= 0) {
                        if (chokeEnabled) reader.readInt32()
                    } else {
                        reader.readInt32()
                    }
                }
                val infinite = if (ApolloConverter.version >= 4) reader.readBoolean() else false
                var hasRootKey = false
                val rootKey = if (ApolloConverter.version >= 12) {
                    reader.readBoolean().also { hasRootKey = it }.let {
                        if (hasRootKey) reader.readInt32() else null
                    }
                } else null
                val wrap = if (ApolloConverter.version >= 13) reader.readBoolean() else false
                val expandedIndex = reader.readInt32()
                ApolloModel.Device.Pattern(
                    repeats = repeats, gate = gate, pinch = pinch, bilateral = bilateral,
                    frames = frames, playbackMode = playbackMode, infinite = infinite,
                    rootKey = rootKey, wrap = wrap, expandedIndex = expandedIndex
                )
            }

            ApolloTypes.Frame -> {
                val time = readNextType(reader) as ApolloModel.Time
                val count = if (ApolloConverter.version <= 19) 100 else 101
                val colors = List(count) { readNextType(reader) as ApolloModel.Color }.toMutableList()
                if (ApolloConverter.version <= 19) colors.add(99, ApolloModel.Color(0, 0, 0))
                ApolloModel.Frame(time = time, colors = colors)
            }

            ApolloTypes.Switch -> {
                val target = if (ApolloConverter.version >= 25) reader.readInt32() else 1
                val value = reader.readInt32()
                if (ApolloConverter.version in 18..21 && reader.readBoolean()) {
                    // Apollo auto-creates a Group{Chain{Switch+Clear}} programmatically — no extra bytes in stream
                    val emptyFilter = List(101) { false }
                    return ApolloModel.Device.Group(
                        chains = listOf(
                            ApolloModel.Chain(
                                devices = listOf(
                                    ApolloModel.DeviceWrapper(collapsed = false, enabled = true, device = ApolloModel.Device.Switch(target = 1, value = value)),
                                    ApolloModel.DeviceWrapper(collapsed = false, enabled = true, device = ApolloModel.Device.Clear(mode = 1))
                                ),
                                name = "Switch Reset",
                                enabled = true,
                                secretMultiFilter = emptyFilter
                            )
                        ),
                        expanded = false,
                        expandedIndex = 0
                    )
                }
                ApolloModel.Device.Switch(target = target, value = value)
            }

            ApolloTypes.Flip -> ApolloModel.Device.Flip(
                mode = reader.readInt32(),
                bypass = reader.readBoolean()
            )

            ApolloTypes.Rotate -> ApolloModel.Device.Rotate(
                mode = reader.readInt32(),
                bypass = reader.readBoolean()
            )

            ApolloTypes.LayerFilter -> ApolloModel.Device.LayerFilter(
                target = reader.readInt32(),
                range = reader.readInt32()
            )

            ApolloTypes.ColorFilter -> ApolloModel.Device.ColorFilter(
                hue = reader.readDouble(),
                saturation = reader.readDouble(),
                value = reader.readDouble(),
                hueTolerance = reader.readDouble(),
                saturationTolerance = reader.readDouble(),
                valueTolerance = reader.readDouble()
            )

            ApolloTypes.Move -> ApolloModel.Device.Move(
                offset = readNextType(reader) as ApolloModel.Offset,
                gridMode = reader.readInt32(),
                wrap = reader.readBoolean()
            )

            ApolloTypes.Multi -> {
                val preprocess = readNextType(reader) as ApolloModel.Chain
                val chainCount = reader.readInt32()
                val chains = List(chainCount) { readNextType(reader) as ApolloModel.Chain }
                if (ApolloConverter.version == 28) {
                    repeat(chainCount) { repeat(101) { reader.readBoolean() } }
                }
                var expanded = false
                val hasExpanded = reader.readBoolean().also { expanded = it }
                val expandedIndex = if (expanded) reader.readInt32() else 0
                val multiMode = reader.readInt32()
                ApolloModel.Device.Multi(
                    preprocess = preprocess,
                    chains = chains,
                    multiMode = multiMode,
                    hasExpanded = hasExpanded,
                    expandedIndex = expandedIndex
                )
            }

            ApolloTypes.Output -> ApolloModel.Device.Output(target = reader.readInt32())

            ApolloTypes.Tone -> ApolloModel.Device.Tone(
                hue = reader.readDouble(),
                satHigh = reader.readDouble(),
                satLow = reader.readDouble(),
                valueHigh = reader.readDouble(),
                valueLow = reader.readDouble()
            )

            ApolloTypes.Clear -> ApolloModel.Device.Clear(mode = reader.readInt32())

            ApolloTypes.Refresh -> ApolloModel.Device.Refresh(
                macros = List(4) { reader.readBoolean() }
            )

            else -> error("Unknown or unhandled Apollo type: $type")
        }
    }
}