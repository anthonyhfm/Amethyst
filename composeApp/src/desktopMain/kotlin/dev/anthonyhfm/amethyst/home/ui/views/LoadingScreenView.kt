package dev.anthonyhfm.amethyst.home.ui.views

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import amethyst.composeapp.generated.resources.*
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyH4
import dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager
import dev.anthonyhfm.amethyst.home.ui.components.AmethystLoadingLogo

@Composable
fun LoadingScreenView(message: String? = null) {
    val progressReportState by ProjectLoadingManager.loadingProgress.collectAsState()
    val report = progressReportState

    val rawProgress = report?.progress ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LoadingProgressAnim"
    )

    val displayPercentage = (animatedProgress * 100).toInt()

    val defaultTitle = stringResource(Res.string.home_loading_default_title)
    val defaultStatus = stringResource(Res.string.home_loading_default_status)

    val displayTitle = report?.title?.ifBlank { null } ?: defaultTitle
    val displayStatus = report?.statusText?.ifBlank { null } ?: message ?: defaultStatus
    val displayDetail = report?.detailText

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme[colors][background]),
        contentAlignment = Alignment.Center
    ) {

        // Compact, Spacious Column Layout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(32.dp)
                .fillMaxHeight()
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Title from About Page (home_about_title), but smaller
            TypographyH4(
                text = stringResource(Res.string.home_about_title)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Compact Amethyst Logo (Width 150dp)
            AmethystLoadingLogo(
                progress = animatedProgress,
                width = 150.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Percentage Readout
            Text(
                text = "$displayPercentage%",
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFAFAFA),
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Ultra-Thin Progress Bar
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF27272A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress.coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF8B5CF6),
                                    Color(0xFFC084FC)
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dynamic Status & Detail Typography with 32.dp Edge Padding & Enforced Single Line Ellipsis
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text(
                    text = displayStatus,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE4E4E7),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!displayDetail.isNullOrBlank()) {
                    Text(
                        text = displayDetail,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF71717A),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
