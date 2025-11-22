package dev.anthonyhfm.amethyst.home.ui.views

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.theme.AMETHYST_THEME
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun AbletonImportWizard(
    path: String,
    navigator: NavHostController,
    onOpenWorkspace: () -> Unit,
    onCancel: () -> Unit
) {
    val viewModel = viewModel { AbletonImportWizardViewModel() }
    val customPalettePath: String by viewModel.customPalettePath.collectAsState()

    MaterialTheme(
        colorScheme = AMETHYST_THEME
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Ableton Import Wizard",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 6.dp, top = 16.dp)
                )
                Text(
                    "Project: $path",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(10f)
                        .border(1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Custom palette: ${if (customPalettePath.isBlank()) "None" else customPalettePath.substringAfterLast("/").ifEmpty { viewModel.customPalettePath.value }}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.onClickImportCustomPalette()
                            },
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.UploadFile,
                                    contentDescription = "Select file",
                                )
                                Text(
                                    text = "Select",
                                    style = MaterialTheme.typography.titleSmall,
                                    lineHeight = MaterialTheme.typography.titleSmall.fontSize,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = {
                            onCancel()
                            viewModel.customPalettePath.value = ""
                        },
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.titleSmall,
                            lineHeight = MaterialTheme.typography.titleSmall.fontSize
                        )
                    }
                    Button(
                        onClick = {
                            navigator.navigate(HomeNavRoute.LoadingScreen("Translating your Ableton Live-Set"))

                            GlobalScope.launch {
                                viewModel.startAbletonImport(path)

                                onOpenWorkspace()
                                cancel()
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text = "Start Conversion",
                            style = MaterialTheme.typography.titleSmall,
                            lineHeight = MaterialTheme.typography.titleSmall.fontSize
                        )
                    }
                }
            }
        }
    }
}
