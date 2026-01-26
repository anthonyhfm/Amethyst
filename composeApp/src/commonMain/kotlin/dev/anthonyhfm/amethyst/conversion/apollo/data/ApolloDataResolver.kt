package dev.anthonyhfm.amethyst.conversion.apollo.data

import dev.anthonyhfm.amethyst.conversion.apollo.ApolloConverter

class ApolloDataResolver {
    fun readNextType(reader: ApolloBinaryReader): ApolloModel {
        return when (val type = ApolloTypes.entries[reader.readByte().toInt()]) {
            ApolloTypes.Project -> ApolloModel.Project(
                bpm = reader.readInt32().apply {
                    ApolloConverter.bpm = this
                },
                macros = List(4) { reader.readInt32() },
                tracks = List(reader.readInt32()) {
                    readNextType(reader) as ApolloModel.Track
                },
                author = reader.readString(),
                timeSpentSeconds = reader.readInt64(),
                startedUnixSeconds = reader.readInt64(),
                undoManager = readNextType(reader) as ApolloModel.UndoManager
            )

            ApolloTypes.UndoManager -> ApolloModel.UndoManager(
                version = reader.readInt32(),
                size = reader.readInt32()
            )

            ApolloTypes.Track -> ApolloModel.Track(
                chain = readNextType(reader) as ApolloModel.Chain,
                launchpad = readNextType(reader) as ApolloModel.Launchpad,
                name = reader.readString(),
                enabled = reader.readBoolean()
            )

            ApolloTypes.Launchpad -> ApolloModel.Launchpad(
                name = reader.readString(),
                format = reader.readInt32(),
                rotation = reader.readInt32()
            )

            ApolloTypes.Chain -> ApolloModel.Chain(
                devices = List(reader.readInt32()) {
                    readNextType(reader) as ApolloModel.DeviceWrapper
                },
                name = reader.readString(),
                enabled = reader.readBoolean(),
                secretMultiFilter = List(101) { reader.readBoolean() }
            )

            ApolloTypes.Device -> ApolloModel.DeviceWrapper(
                collapsed = reader.readBoolean(),
                enabled = reader.readBoolean(),
                device = readNextType(reader) as ApolloModel.Device
            )

            ApolloTypes.Color -> ApolloModel.Color(
                r = reader.readByte(),
                g = reader.readByte(),
                b = reader.readByte()
            )

            ApolloTypes.Length -> ApolloModel.Length(
                step = reader.readInt32()
            )

            ApolloTypes.Time -> ApolloModel.Time(
                free = reader.readBoolean(),
                length = readNextType(reader) as ApolloModel.Length,
                divisor = reader.readInt32()
            )

            ApolloTypes.Group -> {
                var expanded = false

                ApolloModel.Device.Group(
                    chains = List(reader.readInt32()) {
                        readNextType(reader) as ApolloModel.Chain
                    },
                    expanded = reader.readBoolean().also {
                        expanded = it
                    },
                    expandedIndex = if (expanded) reader.readInt32() else 0
                )
            }

            ApolloTypes.Choke -> ApolloModel.Device.Choke(
                target = reader.readInt32(),
                chain = readNextType(reader) as ApolloModel.Chain
            )

            ApolloTypes.KeyFilter -> ApolloModel.Device.KeyFilter(
                filters = List(101) { reader.readBoolean() }
            )

            ApolloTypes.Delay -> ApolloModel.Device.Delay(
                time = readNextType(reader) as ApolloModel.Time,
                gate = reader.readDouble()
            )

            ApolloTypes.Hold -> ApolloModel.Device.Hold(
                time = readNextType(reader) as ApolloModel.Time,
                gate = reader.readDouble(),
                mode = reader.readInt32(),
                release = reader.readBoolean()
            )

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
                var count = 0
                var expanded = false

                ApolloModel.Device.Fade(
                    time = readNextType(reader) as ApolloModel.Time,
                    gate = reader.readDouble(),
                    playMode = reader.readInt32(),
                    count = reader.readInt32().also { count = it },
                    colors = List(count) { readNextType(reader) as ApolloModel.Color },
                    positions = List(count) { reader.readDouble() },
                    fadeTypes = List(count - 1) { reader.readInt32() },
                    hasExpanded = reader.readBoolean().also { expanded = it },
                    expandedIndex = if (expanded) reader.readInt32() else 0
                )
            }

            ApolloTypes.Preview -> ApolloModel.Device.Preview

            else -> {
                error("Unknown type: $type")
            }
        }
    }
}