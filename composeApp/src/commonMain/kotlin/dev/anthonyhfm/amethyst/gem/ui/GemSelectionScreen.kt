package dev.anthonyhfm.amethyst.gem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.ui.editor.GemEditorWorkspaceMode
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogAction
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Badge
import dev.anthonyhfm.amethyst.ui.components.primitives.BadgeVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Card
import dev.anthonyhfm.amethyst.ui.components.primitives.CardContent
import dev.anthonyhfm.amethyst.ui.components.primitives.CardDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.CardHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.CardTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Input
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyH2
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyMuted
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon

data class GemSelectionWorkspaceMode(
    val initialDomainFilter: GemSignalDomain? = null,
    override val displayName: String = "Gems",
    override val selectable: Boolean = false
) : WorkspaceContract.WorkspaceMode {
    @Composable
    fun ModeContent(paddingValues: PaddingValues) {
        val assets by WorkspaceRepository.gemAssets.collectAsState()

        GemSelectionScreen(
            assets = assets,
            initialDomainFilter = initialDomainFilter,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            onBack = { WorkspaceRepository.switchToPreviousMode() },
            onCreateGem = {
                val asset = WorkspaceRepository.createGemAsset(GemSignalDomain.LED)
                WorkspaceRepository.switchMode(
                    GemEditorWorkspaceMode(
                        initialAssetId = asset.metadata.id,
                        entryContext = GemEditorWorkspaceMode.EntryContext.Workspace(
                            sourceLabel = "Gems",
                            preferredHostDomain = GemSignalDomain.LED
                        )
                    )
                )
            },
            onEditGem = { asset ->
                WorkspaceRepository.switchMode(
                    GemEditorWorkspaceMode(
                        initialAssetId = asset.metadata.id,
                        entryContext = GemEditorWorkspaceMode.EntryContext.Workspace(
                            sourceLabel = "Gems",
                            preferredHostDomain = asset.definition.host.supportedDomains.singleOrNull()
                        )
                    )
                )
            },
            onDeleteGem = { asset ->
                WorkspaceRepository.removeGemAsset(asset.metadata.id)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GemSelectionScreen(
    assets: List<GemAsset>,
    onBack: () -> Unit,
    onCreateGem: () -> Unit,
    onEditGem: (GemAsset) -> Unit,
    onDeleteGem: (GemAsset) -> Unit,
    modifier: Modifier = Modifier,
    initialDomainFilter: GemSignalDomain? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedDomain by remember(initialDomainFilter) { mutableStateOf(initialDomainFilter) }
    var assetPendingDeletion by remember { mutableStateOf<GemAsset?>(null) }

    val availableDomains = remember(assets) {
        buildList {
            if (assets.isEmpty() || assets.any { GemSignalDomain.LED in it.definition.host.supportedDomains }) {
                add(GemSignalDomain.LED)
            }
            if (assets.any { GemSignalDomain.MIDI in it.definition.host.supportedDomains }) {
                add(GemSignalDomain.MIDI)
            }
        }
    }

    LaunchedEffect(availableDomains) {
        if (selectedDomain?.let { it !in availableDomains } == true) {
            selectedDomain = null
        }
    }

    val filteredAssets = remember(assets, searchQuery, selectedDomain) {
        assets
            .filter { asset ->
                val matchesSearch = searchQuery.isBlank() ||
                    asset.metadata.name.contains(searchQuery, ignoreCase = true)
                val matchesDomain = selectedDomain?.let { domain ->
                    domain in asset.definition.host.supportedDomains
                } ?: true
                matchesSearch && matchesDomain
            }
            .sortedWith(compareBy({ it.metadata.name.ifBlank { "Untitled Gem" }.lowercase() }, { it.metadata.id.lowercase() }))
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                variant = ButtonVariant.Outline,
                size = ButtonSize.Small
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBackIosNew,
                    contentDescription = null
                )
                Text("Back")
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TypographyH2("Gems")
                TypographyMuted("Manage saved Gems for this workspace.")
            }

            Button(
                onClick = onCreateGem,
                size = ButtonSize.Small
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Text("New Gem")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            CardHeader {
                CardTitle("Browse Gems")
                CardDescription("Search and filter workspace Gems by name and supported host domain.")
            }
            CardContent(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Input(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search by name…",
                        modifier = Modifier.fillMaxWidth()
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DomainFilterChip(
                            label = "All",
                            selected = selectedDomain == null,
                            onClick = { selectedDomain = null }
                        )
                        availableDomains.forEach { domain ->
                            DomainFilterChip(
                                label = domain.label(),
                                selected = selectedDomain == domain,
                                onClick = { selectedDomain = domain }
                            )
                        }
                    }
                }
            }
        }

        when {
            assets.isEmpty() -> {
                EmptyStateCard(
                    title = "No gems yet",
                    description = "Create one to start building reusable LED signal graphs for this workspace."
                )
            }

            filteredAssets.isEmpty() -> {
                EmptyStateCard(
                    title = "No gems found",
                    description = "Try a different name search or host-domain filter."
                )
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    filteredAssets.forEach { asset ->
                        GemSelectionAssetCard(
                            asset = asset,
                            onEdit = { onEditGem(asset) },
                            onDelete = { assetPendingDeletion = asset }
                        )
                    }
                }
            }
        }
    }

    assetPendingDeletion?.let { asset ->
        val dialogState = rememberDialogState(initiallyVisible = true)
        AlertDialog(
            state = dialogState,
            onDismiss = { assetPendingDeletion = null }
        ) {
            AlertDialogHeader {
                AlertDialogTitle("Delete Gem?")
                AlertDialogDescription(
                    "\"${asset.metadata.name.ifBlank { "Untitled Gem" }}\" will be removed from the workspace. Devices using it will become unresolved."
                )
            }
            AlertDialogFooter {
                AlertDialogCancel(onClick = { assetPendingDeletion = null }) {
                    Text("Cancel")
                }
                AlertDialogAction(
                    onClick = {
                        onDeleteGem(asset)
                        assetPendingDeletion = null
                    },
                    variant = ButtonVariant.Destructive
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun DomainFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        variant = if (selected) ButtonVariant.Secondary else ButtonVariant.Outline,
        size = ButtonSize.Small
    ) {
        Text(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GemSelectionAssetCard(
    asset: GemAsset,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        CardHeader {
            CardTitle(asset.metadata.name.ifBlank { "Untitled Gem" })
            CardDescription(asset.metadata.id)
        }
        CardContent(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val description = asset.metadata.description.ifBlank {
                    "No description yet."
                }
                TypographyMuted(description)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (asset.definition.host.supportedDomains.isEmpty()) {
                        Badge(variant = BadgeVariant.Outline) {
                            Text("Unscoped")
                        }
                    } else {
                        asset.definition.host.supportedDomains.forEach { domain ->
                            Badge(variant = BadgeVariant.Secondary) {
                                Text(domain.label())
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onEdit,
                        size = ButtonSize.Small
                    ) {
                        Text("Edit")
                    }
                    Button(
                        onClick = onDelete,
                        variant = ButtonVariant.Destructive,
                        size = ButtonSize.Small
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    description: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        CardHeader {
            CardTitle(title)
            CardDescription(description)
        }
    }
}

private fun GemSignalDomain.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
