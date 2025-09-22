package dev.anthonyhfm.amethyst.conversion.ableton.utils

object PaletteFileParser {
    fun parsePaletteFileContent(content: String): Array<Triple<Int, Int, Int>> {
        val lines = content.lines()
        val colors = mutableListOf<Triple<Int, Int, Int>>()

        for (line in lines) {
            val parts = line.replace(";", "").split(",")
            if (parts.size == 2) {
                val nums = parts[1].trim().split(" ")
                if (nums.size == 3) {
                    val r = nums[0].toIntOrNull() ?: continue
                    val g = nums[1].toIntOrNull() ?: continue
                    val b = nums[2].toIntOrNull() ?: continue
                    colors.add(Triple(r, g, b))
                }
            }
        }

        println("Parsed ${colors.size} colors from palette file.")
        return colors.toTypedArray()
    }
}