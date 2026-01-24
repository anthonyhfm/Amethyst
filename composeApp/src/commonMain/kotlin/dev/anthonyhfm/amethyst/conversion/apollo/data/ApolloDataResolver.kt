package dev.anthonyhfm.amethyst.conversion.apollo.data

class ApolloDataResolver {
    fun readNextType(reader: ApolloBinaryReader): ApolloModel {
        return when (val type = ApolloTypes.entries[reader.readByte().toInt()]) {
            ApolloTypes.Project -> ApolloModel.Project(
                bpm = reader.readInt32(),
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

            ApolloTypes.KeyFilter -> ApolloModel.Device.KeyFilter(
                filters = List(101) { reader.readBoolean() }
            )

            ApolloTypes.Group -> ApolloModel.Device.Group(
                chains = List(reader.readInt32()) {
                    readNextType(reader) as ApolloModel.Chain
                },
                expanded = reader.readBoolean(),
                expandedIndex = reader.readInt32()
            )

            else -> {
                error("Unknown type: $type")
            }
        }
    }
}