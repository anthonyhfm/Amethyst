package dev.anthonyhfm.amethyst.workspace.help

import amethyst.composeapp.generated.resources.Res
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
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHeader
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
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
                    text = "Could not load help content.",
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
                        Markdown(
                            content = markdownContent!!,
                            imageTransformer = ResourceImageTransformer,
                            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),

                            colors = markdownColor(
                                text = fg,
                                codeBackground = mutedBg,
                                inlineCodeBackground = mutedBg,
                                dividerColor = borderColor,
                                tableBackground = mutedBg.copy(alpha = 0.4f),
                            ),

                            typography = markdownTypography(
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
                            ),

                            padding = markdownPadding(
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
                            ),

                            dimens = markdownDimens(
                                dividerThickness = 1.dp,
                                codeBackgroundCornerSize = 6.dp,
                                blockQuoteThickness = 2.dp,
                                tableCellWidth = 180.dp,
                                tableCellPadding = 12.dp,
                                tableCornerSize = 6.dp,
                            ),

                            components = markdownComponents(
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
                            ),
                        )
                    }
                }
            }
        }
    }
}
