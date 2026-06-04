package dev.anthonyhfm.amethyst.conversion.unipad.data

import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData

object UnipadAutoPlay {
    fun getAutoPlayData(autoPlayString: String): AutoPlayData {
        val instructions = autoPlayString
            .replace("\r\n", "\n").replace("\r", "\n")
            .split("\n")
        val actions = mutableMapOf<Double, List<AutoPlayData.Action>>()

        var currentTime: Double = 0.0
        val touchedKeys = mutableListOf<Pair<Int, Int>>()

        instructions.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val data = line.split(" ")
            val cmd = data[0].lowercase()

            when {
                // chain / c — switch to a different chain (emitted as a side-button press)
                cmd == "chain" || cmd == "c" -> {
                    val chain = data.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    val current = actions.getOrPut(currentTime) { emptyList() }
                    actions[currentTime] = current + AutoPlayData.Action(x = 9, y = chain, down = true)
                }

                // delay / d — advance timeline
                cmd == "delay" || cmd == "d" -> {
                    val ms = data.getOrNull(1)?.toDoubleOrNull() ?: return@forEach
                    touchedKeys.forEach { (x, y) ->
                        val current = actions.getOrPut(currentTime + ms) { emptyList() }
                        actions[currentTime + ms] = current + AutoPlayData.Action(x = x, y = y, down = false)
                    }
                    touchedKeys.clear()
                    currentTime += ms
                }

                // on / o — button press (down)
                cmd == "on" || cmd == "o" -> {
                    val rawX = data.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    val rawY = data.getOrNull(2)?.toIntOrNull() ?: return@forEach
                    val x = rawY
                    val y = rawX
                    val current = actions.getOrPut(currentTime) { emptyList() }
                    actions[currentTime] = current + AutoPlayData.Action(x = x, y = y, down = true)
                }

                // off / f — button release (up)
                cmd == "off" || cmd == "f" -> {
                    val rawX = data.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    val rawY = data.getOrNull(2)?.toIntOrNull() ?: return@forEach
                    val x = rawY
                    val y = rawX
                    val current = actions.getOrPut(currentTime) { emptyList() }
                    actions[currentTime] = current + AutoPlayData.Action(x = x, y = y, down = false)
                }

                // touch / t — press + immediate release
                cmd == "touch" || cmd == "t" -> {
                    // Docs: touch x y -> data[1]=x, data[2]=y
                    val rawX = data.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    val rawY = data.getOrNull(2)?.toIntOrNull() ?: return@forEach
                    val x = rawY
                    val y = rawX
                    val current = actions.getOrPut(currentTime) { emptyList() }
                    actions[currentTime] = current + AutoPlayData.Action(x = x, y = y, down = true)
                    touchedKeys.add(x to y)
                }

                else -> println("UnipadAutoPlay: unrecognised command '${data[0]}' in line: $line")
            }
        }

        touchedKeys.forEach { (x, y) ->
            val current = actions.getOrPut(currentTime) { emptyList() }
            actions[currentTime] = current + AutoPlayData.Action(x = x, y = y, down = false)
        }

        return AutoPlayData(actions = actions)
    }
}
