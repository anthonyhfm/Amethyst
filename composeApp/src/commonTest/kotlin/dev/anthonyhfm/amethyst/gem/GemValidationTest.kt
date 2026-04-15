package dev.anthonyhfm.amethyst.gem

import dev.anthonyhfm.amethyst.gem.data.GemJsonPersistence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GemValidationTest {

    @Test
    fun unknownNodeTypeProducesErrorButPreservesNodeIdentityInAsset() {
        val unknownTypeId = "amethyst.test.definitely.does.not.exist"
        val nodeId = "mystery-node"
        val asset = Gem.emptyAsset(
            assetId = "gem://test/unknown-node-type",
            name = "Unknown Node Test"
        ).putNode(
            GemNodeInstance(
                id = nodeId,
                type = GemNodeTypeId(typeId = unknownTypeId)
            )
        )

        val result = GemValidator.validate(asset, GemNodeRegistry.builtIns)

        // Node must still be present in the asset — validation must not strip it.
        assertNotNull(
            asset.graph()?.node(nodeId),
            "Unknown-typed node '$nodeId' must remain in the asset after validation"
        )

        // There must be exactly one UNKNOWN_NODE_TYPE error, referencing the correct node.
        val unknownErrors = result.errors.filter {
            it.code == GemValidationErrorCode.UNKNOWN_NODE_TYPE && it.nodeId == nodeId
        }
        assertTrue(
            unknownErrors.isNotEmpty(),
            "Expected a UNKNOWN_NODE_TYPE validation error for node '$nodeId', " +
                "but found: ${result.errors.map { "${it.code}/${it.nodeId}" }}"
        )
    }

    @Test
    fun unknownNodeTypeSurvivesJsonRoundTrip() {
        val unknownTypeId = "amethyst.future.node.type"
        val nodeId = "future-node"
        val asset = Gem.emptyAsset(
            assetId = "gem://test/unknown-node-roundtrip",
            name = "Unknown Node Round-trip Test"
        ).putNode(
            GemNodeInstance(
                id = nodeId,
                type = GemNodeTypeId(typeId = unknownTypeId)
            )
        )

        val encoded = GemJsonPersistence.encode(asset)
        val decoded = GemJsonPersistence.decode(encoded).asset

        assertNotNull(
            decoded.graph()?.node(nodeId),
            "Unknown-typed node '$nodeId' must survive JSON encode/decode round-trip"
        )
        assertEquals(unknownTypeId, decoded.graph()!!.node(nodeId)!!.type.typeId)

        // Validation on the re-decoded asset should also still flag the error with the correct nodeId.
        val result = GemValidator.validate(decoded, GemNodeRegistry.builtIns)
        assertTrue(
            result.errors.any {
                it.code == GemValidationErrorCode.UNKNOWN_NODE_TYPE && it.nodeId == nodeId
            },
            "UNKNOWN_NODE_TYPE error must still reference '$nodeId' after round-trip"
        )
    }
}
