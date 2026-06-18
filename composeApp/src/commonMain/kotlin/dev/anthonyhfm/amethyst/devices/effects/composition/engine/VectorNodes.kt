package dev.anthonyhfm.amethyst.devices.effects.composition.engine

import kotlin.math.*
import kotlinx.serialization.Serializable

import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionDeviceContract.SerializableNode
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionDeviceContract.SerializableConnection

class CompositionGraphEvaluator(
    val nodes: List<SerializableNode>,
    val connections: List<SerializableConnection>,
    val frame: Int,
    val totalFrames: Int
) {
    private val memo = mutableMapOf<String, List<VectorStroke>>()
    private val visiting = mutableSetOf<String>()

    fun evaluateNode(nodeId: String): List<VectorStroke> {
        if (nodeId in memo) return memo[nodeId]!!
        if (nodeId in visiting) {
            // Cycle detected! Return empty list to prevent stack overflow
            return emptyList()
        }
        visiting.add(nodeId)

        val node = nodes.find { it.id == nodeId }
        val result = if (node == null) {
            emptyList()
        } else {
            val t = frame.toFloat()
            
            // Helper to get input strokes connected to this node
            fun getInputStrokes(pinId: String = "input"): List<VectorStroke> {
                val conn = connections.find { it.toNodeId == nodeId && it.toPinId == pinId }
                return if (conn != null) {
                    evaluateNode(conn.fromNodeId)
                } else {
                    emptyList()
                }
            }

            when (node.type.lowercase()) {
                "waterdrop" -> {
                    val cx = node.properties["centerX"]?.toFloatOrNull() ?: 0.5f
                    val cy = node.properties["centerY"]?.toFloatOrNull() ?: 0.5f
                    val speed = node.properties["speed"]?.toFloatOrNull() ?: 0.02f
                    val freq = node.properties["frequency"]?.toFloatOrNull() ?: 10.0f
                    val amp = node.properties["amplitude"]?.toFloatOrNull() ?: 0.05f
                    val thick = node.properties["thickness"]?.toFloatOrNull() ?: 0.05f
                    val r = node.properties["r"]?.toFloatOrNull() ?: 1.0f
                    val g = node.properties["g"]?.toFloatOrNull() ?: 1.0f
                    val b = node.properties["b"]?.toFloatOrNull() ?: 1.0f
                    val a = node.properties["a"]?.toFloatOrNull() ?: 1.0f

                    // Generate circular waterdrop ripples
                    val radius = ((speed * t) % 1.0f + 1.0f) % 1.0f
                    val numPoints = 64
                    val points = List(numPoints) { i ->
                        val theta = (i * 2 * PI / numPoints).toFloat()
                        // Ripple oscillation over theta and time
                        val rad = radius + amp * sin(freq * theta - t * 0.2f)
                        val px = cx + rad * cos(theta)
                        val py = cy + rad * sin(theta)
                        VectorPoint(px, py, pressure = 1f)
                    }
                    listOf(VectorStroke(points, r, g, b, a, thick))
                }
                "spiral" -> {
                    val cx = node.properties["centerX"]?.toFloatOrNull() ?: 0.5f
                    val cy = node.properties["centerY"]?.toFloatOrNull() ?: 0.5f
                    val turns = node.properties["turns"]?.toFloatOrNull() ?: 3.0f
                    val tightness = node.properties["tightness"]?.toFloatOrNull() ?: 1.0f
                    val speed = node.properties["speed"]?.toFloatOrNull() ?: 0.05f
                    val amp = node.properties["amplitude"]?.toFloatOrNull() ?: 0.4f
                    val thick = node.properties["thickness"]?.toFloatOrNull() ?: 0.05f
                    val r = node.properties["r"]?.toFloatOrNull() ?: 1.0f
                    val g = node.properties["g"]?.toFloatOrNull() ?: 1.0f
                    val b = node.properties["b"]?.toFloatOrNull() ?: 1.0f
                    val a = node.properties["a"]?.toFloatOrNull() ?: 1.0f

                    val numPoints = 128
                    val points = List(numPoints) { i ->
                        val frac = i.toFloat() / (numPoints - 1)
                        val theta = frac * turns * 2 * PI.toFloat()
                        val rad = amp * (frac.pow(tightness))
                        val phi = theta + speed * t
                        val px = cx + rad * cos(phi)
                        val py = cy + rad * sin(phi)
                        VectorPoint(px, py, pressure = 1f)
                    }
                    listOf(VectorStroke(points, r, g, b, a, thick))
                }
                "scanner" -> {
                    val direction = node.properties["direction"]?.toFloatOrNull() ?: 0.0f
                    val speed = node.properties["speed"]?.toFloatOrNull() ?: 0.02f
                    val width = node.properties["width"]?.toFloatOrNull() ?: 1.0f
                    val thick = node.properties["thickness"]?.toFloatOrNull() ?: 0.05f
                    val r = node.properties["r"]?.toFloatOrNull() ?: 1.0f
                    val g = node.properties["g"]?.toFloatOrNull() ?: 1.0f
                    val b = node.properties["b"]?.toFloatOrNull() ?: 1.0f
                    val a = node.properties["a"]?.toFloatOrNull() ?: 1.0f

                    val p = ((speed * t) % 1.0f + 1.0f) % 1.0f
                    val cx = 0.5f + (p - 0.5f) * cos(direction)
                    val cy = 0.5f + (p - 0.5f) * sin(direction)

                    val vx = -sin(direction)
                    val vy = cos(direction)

                    val numPoints = 10
                    val points = List(numPoints) { i ->
                        val frac = i.toFloat() / (numPoints - 1) - 0.5f
                        val px = cx + frac * width * vx
                        val py = cy + frac * width * vy
                        VectorPoint(px, py, pressure = 1f)
                    }
                    listOf(VectorStroke(points, r, g, b, a, thick))
                }
                "rotate" -> {
                    val angle = node.properties["angle"]?.toFloatOrNull() ?: 0.0f
                    val speed = node.properties["speed"]?.toFloatOrNull() ?: 0.05f
                    val px = node.properties["px"]?.toFloatOrNull() ?: 0.5f
                    val py = node.properties["py"]?.toFloatOrNull() ?: 0.5f

                    val currentAngle = angle + speed * t
                    val strokes = getInputStrokes()
                    strokes.map { it.rotate(currentAngle, px, py) }
                }
                "scale" -> {
                    val sx = node.properties["sx"]?.toFloatOrNull() ?: 1.0f
                    val sy = node.properties["sy"]?.toFloatOrNull() ?: 1.0f
                    val speedX = node.properties["speedX"]?.toFloatOrNull() ?: 0.0f
                    val speedY = node.properties["speedY"]?.toFloatOrNull() ?: 0.0f
                    val px = node.properties["px"]?.toFloatOrNull() ?: 0.5f
                    val py = node.properties["py"]?.toFloatOrNull() ?: 0.5f

                    val currentSx = sx + speedX * t
                    val currentSy = sy + speedY * t
                    val strokes = getInputStrokes()
                    strokes.map { it.scale(currentSx, currentSy, px, py) }
                }
                "translate" -> {
                    val tx = node.properties["tx"]?.toFloatOrNull() ?: 0.0f
                    val ty = node.properties["ty"]?.toFloatOrNull() ?: 0.0f
                    val speedX = node.properties["speedX"]?.toFloatOrNull() ?: 0.0f
                    val speedY = node.properties["speedY"]?.toFloatOrNull() ?: 0.0f

                    val currentTx = tx + speedX * t
                    val currentTy = ty + speedY * t
                    val strokes = getInputStrokes()
                    strokes.map { it.translate(currentTx, currentTy) }
                }
                else -> emptyList()
            }
        }

        visiting.remove(nodeId)
        memo[nodeId] = result
        return result
    }

    fun evaluateAll(): List<VectorStroke> {
        val sourceNodeIds = connections.map { it.fromNodeId }.toSet()
        // Terminal nodes are those whose output is not consumed
        val terminalNodes = nodes.filter { it.id !in sourceNodeIds }
        val nodesToEvaluate = if (terminalNodes.isNotEmpty()) terminalNodes else nodes
        return nodesToEvaluate.flatMap { evaluateNode(it.id) }
    }
}
