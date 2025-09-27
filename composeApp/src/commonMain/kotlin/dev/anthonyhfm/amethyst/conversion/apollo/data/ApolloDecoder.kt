package dev.anthonyhfm.amethyst.conversion.apollo.data

import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class ApolloDecoder(
    bytes: ByteArray
) {
    private var data: ByteArray = bytes

    fun readInt32(): Int {
        val int = (data[0].toInt() and 0xFF) or
                ((data[1].toInt() and 0xFF) shl 8) or
                ((data[2].toInt() and 0xFF) shl 16) or
                ((data[3].toInt() and 0xFF) shl 24)

        data = data.copyOfRange(4, data.size)

        return int
    }

    fun readBoolean(): Boolean {
        val bool = data[0].toInt() != 0

        data = data.copyOfRange(1, data.size)

        return bool
    }

    fun readByte(): Byte {
        val byte = data[0]

        data = data.copyOfRange(1, data.size)

        return byte
    }

    fun decodeType(byte: Byte): ApolloCommon.ApolloType {
        byte.toUByte().toInt().let {
            return ApolloCommon.ApolloType.entries[it]
        }
    }

    fun decodeHeader(byteArray: ByteArray): Boolean {
        println("Header: ${byteArray.decodeToString()}")
        return byteArray.decodeToString() == "APOL"
    }

    fun decode(): StateChain {
        val version = readInt32()

        if (version > ApolloCommon.APOLLO_VERSION) {
            error("Apollo version $version is not supported. Current max supported version is ${ApolloCommon.APOLLO_VERSION}")
        }

        readByte() // idk but makes it work
        readByte() // same for this, lmao

        if (decodeType(readByte()) != ApolloCommon.ApolloType.Preferences) {
            error("Invalid Apollo file: Missing Preferences")
        }

        return StateChain()
    }
}