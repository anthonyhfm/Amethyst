package dev.anthonyhfm.amethyst.workspace.help

import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import amethyst.composeapp.generated.resources.Res
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import androidx.compose.ui.graphics.Color
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHeader
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import androidx.compose.ui.text.TextLinkStyles
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollArea
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.blockquote
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.h1
import dev.anthonyhfm.amethyst.ui.theme.h2
import dev.anthonyhfm.amethyst.ui.theme.h3
import dev.anthonyhfm.amethyst.ui.theme.h4
import dev.anthonyhfm.amethyst.ui.theme.inlineCode
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import org.intellij.markdown.ast.getTextInNode

/**
 * Renders device help markdown using the Amethyst shadcn design-system tokens
 * and primitives (Separator, ScrollArea, Typography styles).
 */
@Composable
internal fun HelpViewer(
    helpRef: String,
    paddingValues: PaddingValues,
) {
    var markdownContent by remember(helpRef) { mutableStateOf<String?>(null) }
    var loadError by remember(helpRef) { mutableStateOf(false) }

    LaunchedEffect(helpRef) {
        try {
            val bytes = Res.readBytes("files/devices/$helpRef.md")
            markdownContent = bytes.decodeToString()
        } catch (e: Exception) {
            loadError = true
            println("HelpViewer: failed to load help for '$helpRef': ${e.message}")
        }
    }

    // shadcn color tokens
    val bg = Theme[colors][background]
    val fg = Theme[colors][foreground]
    val mutedBg = Theme[colors][muted]
    val mutedFg = Theme[colors][mutedForeground]
    val borderColor = Theme[colors][border]
    val primaryColor = Theme[colors][primary]

    // shadcn typography tokens from Theme
    val typoH1 = Theme[typography][h1].copy(color = fg)
    val typoH2 = Theme[typography][h2].copy(color = fg)
    val typoH3 = Theme[typography][h3].copy(color = fg)
    val typoH4 = Theme[typography][h4].copy(color = fg)
    val typoP = Theme[typography][p].copy(color = fg)
    val typoSmall = Theme[typography][small].copy(color = fg)
    val typoBlockquote = Theme[typography][blockquote].copy(color = mutedFg)
    val typoInlineCode = Theme[typography][inlineCode].copy(color = fg)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(paddingValues),
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            loadError -> {
                Text(
                    text = stringResource(Res.string.workspace_help_viewer_failed_to_load),
                    style = typoSmall,
                    color = mutedFg,
                    modifier = Modifier.padding(32.dp),
                )
            }
            markdownContent != null -> {
                ScrollArea(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 48.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        val markdownColors = markdownColor(
                            text = fg,
                            codeBackground = mutedBg,
                            inlineCodeBackground = mutedBg,
                            dividerColor = borderColor,
                            tableBackground = mutedBg.copy(alpha = 0.4f),
                        )

                        val markdownTypo = markdownTypography(
                            h1 = typoH1,
                            h2 = typoH2,
                            h3 = typoH3,
                            h4 = typoH4,
                            h5 = TextStyle(
                                fontFamily = typoP.fontFamily,
                                fontSize = 18.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = fg,
                            ),
                            h6 = TextStyle(
                                fontFamily = typoP.fontFamily,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = fg,
                            ),
                            text = typoP,
                            paragraph = typoP,
                            code = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Normal,
                                color = fg,
                            ),
                            inlineCode = typoInlineCode,
                            quote = typoBlockquote,
                            ordered = typoP,
                            bullet = typoP,
                            list = typoP,
                            table = typoSmall.copy(lineHeight = 22.sp),
                            textLink = TextLinkStyles(
                                style = SpanStyle(
                                    color = primaryColor,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        )

                        val markdownPad = markdownPadding(
                            block = 8.dp,
                            list = 6.dp,
                            listItemTop = 2.dp,
                            listItemBottom = 2.dp,
                            listIndent = 24.dp,
                            codeBlock = PaddingValues(16.dp),
                            blockQuote = PaddingValues(start = 16.dp, end = 0.dp),
                            blockQuoteText = PaddingValues(vertical = 8.dp),
                            blockQuoteBar = PaddingValues.Absolute(
                                left = 0.dp, top = 2.dp, right = 8.dp, bottom = 2.dp,
                            ),
                        )

                        val markdownDim = markdownDimens(
                            dividerThickness = 1.dp,
                            codeBackgroundCornerSize = 6.dp,
                            blockQuoteThickness = 2.dp,
                            tableCellWidth = 180.dp,
                            tableCellPadding = 12.dp,
                            tableCornerSize = 6.dp,
                        )

                        val markdownComp = markdownComponents(
                            heading1 = {
                                Column {
                                    Spacer(Modifier.height(8.dp))
                                    MarkdownHeader(it.content, it.node, style = it.typography.h1)
                                    Spacer(Modifier.height(12.dp))
                                    Separator()
                                }
                            },
                            heading2 = {
                                Column {
                                    Spacer(Modifier.height(20.dp))
                                    MarkdownHeader(it.content, it.node, style = it.typography.h2)
                                    Spacer(Modifier.height(8.dp))
                                    Separator()
                                }
                            },
                            heading3 = {
                                Column {
                                    Spacer(Modifier.height(16.dp))
                                    MarkdownHeader(it.content, it.node, style = it.typography.h3)
                                }
                            },
                            heading4 = {
                                Column {
                                    Spacer(Modifier.height(12.dp))
                                    MarkdownHeader(it.content, it.node, style = it.typography.h4)
                                }
                            },
                            horizontalRule = {
                                Spacer(Modifier.height(16.dp))
                                Separator()
                                Spacer(Modifier.height(16.dp))
                            },
                        )

                        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                            val text = markdownContent!!
                            val regex = Regex("""!\[.*?\]\((res://.*?)\)""")
                            val matches = regex.findAll(text).toList()
                            
                            var lastIndex = 0
                            matches.forEach { match ->
                                val textBefore = text.substring(lastIndex, match.range.first).trim()
                                if (textBefore.isNotEmpty()) {
                                    Markdown(
                                        content = textBefore,
                                        imageTransformer = ResourceImageTransformer,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = markdownColors,
                                        typography = markdownTypo,
                                        padding = markdownPad,
                                        dimens = markdownDim,
                                        components = markdownComp,
                                    )
                                }
                                
                                val link = match.groupValues[1]
                                val imageData = ResourceImageTransformer.transform(link)
                                if (imageData != null) {
                                    Image(
                                        painter = imageData.painter,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(vertical = 12.dp)
                                            .height(300.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.FillHeight
                                    )
                                }
                                
                                lastIndex = match.range.last + 1
                            }
                            
                            val textAfter = text.substring(lastIndex).trim()
                            if (textAfter.isNotEmpty()) {
                                Markdown(
                                    content = textAfter,
                                    imageTransformer = ResourceImageTransformer,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = markdownColors,
                                    typography = markdownTypo,
                                    padding = markdownPad,
                                    dimens = markdownDim,
                                    components = markdownComp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun markdownColor(
    text: Color = Color.Unspecified,
    codeBackground: Color = Color.Unspecified,
    inlineCodeBackground: Color = codeBackground,
    dividerColor: Color = Color.Unspecified,
    tableBackground: Color = Color.Unspecified,
): MarkdownColors = DefaultMarkdownColors(
    text = text,
    codeBackground = codeBackground,
    inlineCodeBackground = inlineCodeBackground,
    dividerColor = dividerColor,
    tableBackground = tableBackground,
)

fun markdownTypography(
    h1: TextStyle = TextStyle.Default,
    h2: TextStyle = TextStyle.Default,
    h3: TextStyle = TextStyle.Default,
    h4: TextStyle = TextStyle.Default,
    h5: TextStyle = TextStyle.Default,
    h6: TextStyle = TextStyle.Default,
    text: TextStyle = TextStyle.Default,
    code: TextStyle = TextStyle.Default,
    inlineCode: TextStyle = code,
    quote: TextStyle = TextStyle.Default,
    paragraph: TextStyle = TextStyle.Default,
    ordered: TextStyle = TextStyle.Default,
    bullet: TextStyle = TextStyle.Default,
    list: TextStyle = TextStyle.Default,
    textLink: TextLinkStyles = TextLinkStyles(style = SpanStyle()),
    table: TextStyle = TextStyle.Default,
): MarkdownTypography = DefaultMarkdownTypography(
    h1 = h1,
    h2 = h2,
    h3 = h3,
    h4 = h4,
    h5 = h5,
    h6 = h6,
    text = text,
    code = code,
    inlineCode = inlineCode,
    quote = quote,
    paragraph = paragraph,
    ordered = ordered,
    bullet = bullet,
    list = list,
    textLink = textLink,
    table = table,
)

