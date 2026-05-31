package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectUser
import dev.anthonyhfm.amethyst.core.network.presence.ActivityToast
import dev.anthonyhfm.amethyst.core.network.presence.RemoteCursor
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.cardForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun CursorOverlay(
    cursors: Map<String, RemoteCursor>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        cursors.values.forEach { cursor ->
            key(cursor.user.id) {
                RemoteCursorIndicator(cursor)
            }
        }
    }
}

@Composable
private fun RemoteCursorIndicator(cursor: RemoteCursor) {
    val animatedX = animateFloatAsState(cursor.x, label = "remote-cursor-x")
    val animatedY = animateFloatAsState(cursor.y, label = "remote-cursor-y")
    val color = Color(cursor.user.color)

    Column(
        modifier = Modifier.offset {
            IntOffset(animatedX.value.roundToInt(), animatedY.value.roundToInt())
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width * 0.85f, size.height * 0.42f)
                lineTo(size.width * 0.42f, size.height * 0.56f)
                lineTo(size.width * 0.24f, size.height)
                close()
            }
            drawPath(path = path, color = color)
        }

        Box(
            modifier = Modifier
                .background(color, DefaultShape)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(
                text = cursor.user.name.ifBlank { "Guest" },
                color = Color.White,
                style = Theme[typography][small],
            )
        }
    }
}

@Composable
fun UserRoster(
    participants: List<ConnectUser>,
    modifier: Modifier = Modifier
) {
    val visibleParticipants = participants.distinctBy { it.id }
    if (visibleParticipants.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleParticipants.take(5).forEach { user ->
            UserAvatar(
                user = user,
                modifier = Modifier.size(28.dp),
            )
        }

        if (visibleParticipants.size > 5) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Theme[colors][background], CircleShape)
                    .border(1.dp, Theme[colors][border], CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+${visibleParticipants.size - 5}",
                    style = Theme[typography][small],
                    color = Theme[colors][mutedForeground],
                )
            }
        }
    }
}

@Composable
private fun UserAvatar(
    user: ConnectUser,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(user.color), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = user.name.trim().take(1).uppercase().ifBlank { "?" },
            color = Color.White,
            style = Theme[typography][small],
        )
    }
}

@Composable
fun ActivityToastOverlay(
    toasts: List<ActivityToast>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        toasts.forEach { toast ->
            key(toast.id) {
                var visible by remember(toast.id) { mutableStateOf(true) }

                LaunchedEffect(toast.id) {
                    delay(3_000)
                    visible = false
                    delay(220)
                    onDismiss(toast.id)
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + slideInHorizontally { it / 2 },
                    exit = fadeOut() + slideOutHorizontally { it / 2 },
                ) {
                    Row(
                        modifier = Modifier
                            .background(Theme[colors][card], DefaultShape)
                            .border(1.dp, Theme[colors][border], DefaultShape)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(toast.userColor), CircleShape),
                        )
                        Text(
                            text = toast.message,
                            color = Theme[colors][cardForeground],
                            style = Theme[typography][small],
                        )
                    }
                }
            }
        }
    }
}
