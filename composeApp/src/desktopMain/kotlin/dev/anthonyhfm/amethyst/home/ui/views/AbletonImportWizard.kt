package dev.anthonyhfm.amethyst.home.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Field
import dev.anthonyhfm.amethyst.ui.components.primitives.FieldDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.FieldLabel
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyP
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyMuted
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import kotlinx.coroutines.launch

@Composable
fun AbletonImportWizard(
    path: String,
    navigator: NavHostController,
    onOpenWorkspace: () -> Unit,
    onCancel: () -> Unit
) {
    val viewModel = viewModel { AbletonImportWizardViewModel() }
    val customPalettePath: String by viewModel.customPalettePath.collectAsState()
    val apolloProjPath: String by viewModel.apolloProjPath.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val paletteName = customPalettePath.substringAfterLast("/").ifBlank { customPalettePath }
    val apolloProjName = apolloProjPath.substringAfterLast("/").ifBlank { apolloProjPath }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),

        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Theme[colors][card])
                    .padding(24.dp),

                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WizardHeader(
                    onClose = {
                        viewModel.customPalettePath.value = ""
                        viewModel.apolloProjPath.value = ""
                        onCancel()
                    },
                )

                Separator()

                Field {
                    FieldLabel("Import source")
                    FieldDescription(path)
                }

                Separator()

                PaletteSelectionField(
                    paletteName = paletteName,
                    palettePath = customPalettePath,
                    onSelectPalette = { viewModel.onClickImportCustomPalette() },
                )

                Separator()

                ApolloProjSelectionField(
                    apolloProjName = apolloProjName,
                    apolloProjPath = apolloProjPath,
                    onSelectApolloProj = { viewModel.onClickImportApolloProjFile() },
                )

                Spacer(Modifier.weight(1f))

                WizardActions(
                    onCancel = {
                        viewModel.customPalettePath.value = ""
                        viewModel.apolloProjPath.value = ""
                        onCancel()
                    },
                    onStartConversion = {
                        navigator.navigate(HomeNavRoute.LoadingScreen("Translating your Ableton Live-Set"))

                        coroutineScope.launch {
                            viewModel.startAbletonImport(path)
                            onOpenWorkspace()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WizardHeader(
    onClose: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (platform == Platform.Desktop.MacOS) {
                    Modifier.padding(top = 16.dp)
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DialogTitle("Ableton Import Wizard")

            DialogDescription("Choose an optional custom palette before converting your Live set into an Amethyst workspace.")
        }

        Button(
            onClick = onClose,
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Icon,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Theme[colors][foreground],
            )
        }
    }
}

@Composable
private fun PaletteSelectionField(
    paletteName: String,
    palettePath: String,
    onSelectPalette: () -> Unit,
) {
    Field {
        FieldLabel("Custom palette")

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PaletteSelectionSummary(
                    paletteName = paletteName,
                    palettePath = palettePath,
                )
            }

            Button(
                onClick = onSelectPalette,
                variant = ButtonVariant.Outline,
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = Theme[colors][foreground],
                )
                Text("Select")
            }
        }
    }
}

@Composable
private fun PaletteSelectionSummary(
    paletteName: String,
    palettePath: String,
) {
    if (palettePath.isBlank()) {
        TypographyP("No custom palette selected")
        FieldDescription("Optional. If omitted, the default Novation palette is used.")
    } else {
        TypographyMuted(palettePath)
    }
}

@Composable
private fun ApolloProjSelectionField(
    apolloProjName: String,
    apolloProjPath: String,
    onSelectApolloProj: () -> Unit,
) {
    Field {
        FieldLabel("Apollo lights project (.approj)")

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ApolloProjSelectionSummary(apolloProjName = apolloProjName, apolloProjPath = apolloProjPath)
            }

            Button(
                onClick = onSelectApolloProj,
                variant = ButtonVariant.Outline
            ) {
                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, tint = Theme[colors][foreground])
                Text("Select")
            }
        }
    }
}

@Composable
private fun ApolloProjSelectionSummary(apolloProjName: String, apolloProjPath: String) {
    if (apolloProjPath.isBlank()) {
        TypographyP("No Apollo project selected")
        FieldDescription("Optional. If set, lights chain will be sourced from the Apollo project instead of Ableton MIDI tracks.")
    } else {
        TypographyMuted(apolloProjPath)
    }
}

@Composable
private fun WizardActions(
    onCancel: () -> Unit,
    onStartConversion: () -> Unit,
) {
    DialogFooter {
        Button(
            onClick = onCancel,
            variant = ButtonVariant.Outline,
        ) {
            Text("Cancel")
        }

        Button(
            onClick = onStartConversion,
        ) {
            Text("Start Conversion")
        }
    }
}
