package dev.anthonyhfm.amethyst.conversion.unipad.data

import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData

object UnipadAutoPlay {
    fun getAutoPlayData(autoPlayString: String): AutoPlayData {
        val instructions = autoPlayString.split("\n")
        val actions = mutableMapOf<Double, List<AutoPlayData.Action>>()

        var currentTime: Double = 0.0
        instructions.forEach { line ->
            when {
                line.startsWith("c") -> {
                    val data = line.trim().split(" ")
                    val chain = data[1].trim().toInt()
                    val current = actions.getOrPut(currentTime) { emptyList() }

                    actions[currentTime] = current + AutoPlayData.Action(
                        x = 9,
                        y = chain,
                        down = true
                    )
                }

                line.startsWith("d") -> {
                    val data = line.trim().split(" ")

                    currentTime += data[1].trim().toDouble()
                }

                line.startsWith("t") -> {
                    val data = line.trim().split(" ")
                    val current = actions.getOrPut(currentTime) { emptyList() }

                    actions[currentTime] = current + AutoPlayData.Action(
                        x = data[2].trim().toInt(),
                        y = data[1].trim().toInt(),
                        down = true
                    )
                }
            }
        }

        return AutoPlayData(
            actions = actions,
        )
    }
}