package dev.anthonyhfm.amethyst.home.ui.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Progress
import com.composeunstyled.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_studio_logo
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogFooter
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.h3
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedText
import dev.anthonyhfm.amethyst.ui.theme.typography
import io.github.kdroidfilter.nucleus.updater.NucleusUpdater
import io.github.kdroidfilter.nucleus.updater.UpdateResult
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import java.io.File

@Composable
fun UpdateView(
    version: String,
    updater: NucleusUpdater,
    updateResult: UpdateResult.Available?,
    onDismiss: () -> Unit
) {
    var progress by remember { mutableStateOf(-1.0) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Theme[colors][background].copy(alpha = 0.8f))) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Theme[colors][background])
                    .border(1.dp, Theme[colors][border], RoundedCornerShape(8.dp))
                    .padding(24.dp),

                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(Res.drawable.amethyst_studio_logo),
                    contentDescription = "Amethyst Logo",
                    modifier = Modifier.size(64.dp)
                )

                Spacer(Modifier.height(16.dp))

                Text("Update available", style = Theme[typography][h3], color = Theme[colors][foreground])

                Spacer(Modifier.height(4.dp))

                Text("Version $version", style = Theme[typography][mutedText], color = Theme[colors][foreground].copy(alpha = 0.7f))

                Spacer(Modifier.height(24.dp))

                if (progress >= 0.0) {
                    Text(
                        text = "Downloading... ${progress.toInt()}%",
                        style = Theme[typography][mutedText], color = Theme[colors][foreground]
                    )

                    Spacer(Modifier.height(8.dp))

                    Progress(value = (progress / 100.0).toFloat(), modifier = Modifier.fillMaxWidth())
                } else if (downloadedFile != null) {
                    DialogFooter {
                        Button(onClick = { updater.installAndRestart(downloadedFile!!) }) {
                            Text("Install & Restart")
                        }
                    }
                } else {
                    DialogFooter {
                        Button(
                            onClick = onDismiss,
                            variant = ButtonVariant.Outline,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Later")
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                updateResult?.info?.let { info ->
                                    scope.launch {
                                        updater.downloadUpdate(info).collect { status ->
                                            progress = status.percent
                                            if (status.file != null) {
                                                downloadedFile = status.file
                                                updater.installAndRestart(status.file!!)
                                            }
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Update Now")
                        }
                    }
                }
            }
        }
    }
}
