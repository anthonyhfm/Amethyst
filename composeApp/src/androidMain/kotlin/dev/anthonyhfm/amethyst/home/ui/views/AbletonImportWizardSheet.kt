package dev.anthonyhfm.amethyst.home.ui.views

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbletonImportWizardSheet(
    path: String,
    navigator: NavHostController,
    onOpenWorkspace: () -> Unit,
) {
    val viewModel = viewModel { AbletonImportWizardSheetViewModel() }
    val customPalettePath by viewModel.customPalettePath.collectAsState()
    val apolloProjPath by viewModel.apolloProjPath.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.home_import_wizard_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = stringResource(Res.string.home_import_wizard_sheet_close_desc),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.home_import_wizard_sheet_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            // Custom palette section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(Res.string.home_import_wizard_sheet_palette_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.home_import_wizard_sheet_palette_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (customPalettePath.isNotEmpty()) {
                    Text(
                        text = customPalettePath.substringAfterLast('/').substringAfterLast('\\'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.onClickImportCustomPalette() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (customPalettePath.isEmpty()) stringResource(Res.string.home_import_wizard_sheet_palette_select) else stringResource(Res.string.home_import_wizard_sheet_palette_change))
                }
            }

            // Apollo project section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(Res.string.home_import_wizard_sheet_apollo_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.home_import_wizard_sheet_apollo_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (apolloProjPath.isNotEmpty()) {
                    Text(
                        text = apolloProjPath.substringAfterLast('/').substringAfterLast('\\'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.onClickImportApolloProjFile() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (apolloProjPath.isEmpty()) stringResource(Res.string.home_import_wizard_sheet_apollo_select) else stringResource(Res.string.home_import_wizard_sheet_apollo_change))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val loadingMsg = stringResource(Res.string.home_import_wizard_sheet_loading_msg)
            val loadingTitle = stringResource(Res.string.home_loading_default_title)
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    coroutineScope.launch {
                        dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.startLoading(
                            initialTitle = loadingTitle,
                            initialStatus = loadingMsg,
                        )
                        navigator.navigate(
                            dev.anthonyhfm.amethyst.home.nav.HomeNavRoute.LoadingScreen(
                                loadingMsg
                            )
                        )
                        try {
                            viewModel.startAbletonImport(path)
                            dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.finishLoading()
                            onOpenWorkspace()
                        } catch (e: Exception) {
                            dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.finishLoading()
                            e.printStackTrace()
                            navigator.popBackStack()
                        }
                    }
                },
            ) {
                Text(stringResource(Res.string.home_import_wizard_sheet_import_button))
            }
        }
    }
}
