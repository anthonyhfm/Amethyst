package dev.anthonyhfm.amethyst.workspace.help

fun mainSplit() {
    val text = """
# Header
Some text
![Image title](res://image.png)
More text
    """.trimIndent()
    
    val regex = Regex("""!\[.*?\]\((res://.*?)\)""")
    val matches = regex.findAll(text).toList()
    
    var lastIndex = 0
    matches.forEach { match ->
        val textBefore = text.substring(lastIndex, match.range.first)
        println("TEXT: $textBefore")
        println("IMAGE: ${match.groupValues[1]}")
        lastIndex = match.range.last + 1
    }
    val textAfter = text.substring(lastIndex)
    println("TEXT: $textAfter")
}
