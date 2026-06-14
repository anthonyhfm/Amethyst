package dev.anthonyhfm.amethyst.workspace.help

fun mainRegex() {
    val text = "![Keyframes device](res://keyframes.png)"
    val regex = Regex("""!\[.*?\]\((.*?)\)""")
    val match = regex.find(text)
    println(match?.groupValues?.get(1))
}
