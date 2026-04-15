package dev.anthonyhfm.amethyst.gem.data

import dev.anthonyhfm.amethyst.core.util.Version
import dev.anthonyhfm.amethyst.gem.Gem
import dev.anthonyhfm.amethyst.gem.GemAsset
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path

object GemJsonPersistence {
    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    fun encode(asset: GemAsset): String = json.encodeToString(asset)

    /**
     * Decodes a [GemAsset] from a JSON string, applying optional migrations.
     *
     * Returns a [GemDecodedAsset] that always contains either the decoded/migrated asset or
     * the best-effort partial result. Errors are surfaced via [GemDecodedAsset.loadError]
     * rather than thrown, enabling degraded-but-stable editor behaviour.
     */
    fun decode(
        serialized: String,
        migrationPlan: GemAssetMigrationPlan = GemAssetMigrationPlan.empty,
        targetSchemaVersion: Version? = null
    ): GemDecodedAsset {
        val decoded = try {
            json.decodeFromString<GemAsset>(serialized)
        } catch (e: SerializationException) {
            return GemDecodedAsset(
                asset = GemAsset(),
                originalSchemaVersion = Gem.phase1SchemaVersion,
                loadError = GemLoadError.ParseError(cause = e, message = e.message ?: "JSON parse error")
            )
        } catch (e: IllegalArgumentException) {
            return GemDecodedAsset(
                asset = GemAsset(),
                originalSchemaVersion = Gem.phase1SchemaVersion,
                loadError = GemLoadError.ParseError(cause = e, message = e.message ?: "JSON argument error")
            )
        }

        val migrationResult = targetSchemaVersion?.let { schemaVersion ->
            migrationPlan.migrate(decoded, targetSchemaVersion = schemaVersion)
        }

        return GemDecodedAsset(
            asset = migrationResult?.asset ?: decoded,
            originalSchemaVersion = decoded.schemaVersion,
            appliedMigrations = migrationResult?.appliedMigrations.orEmpty(),
            loadError = migrationResult?.error?.let { GemLoadError.MigrationError(it) }
        )
    }

    /**
     * Saves a [GemAsset] to [path] atomically: writes to a temporary sibling file first,
     * then renames to the final path. This prevents partial writes from corrupting existing
     * saved data when serialization or I/O fails mid-operation.
     *
     * @throws GemSaveException if saving fails; the original file is preserved.
     */
    fun save(
        asset: GemAsset,
        path: Path,
        fileSystem: FileSystem
    ) {
        val encoded = try {
            encode(asset)
        } catch (e: SerializationException) {
            throw GemSaveException("Failed to serialize asset '${asset.metadata.id}': ${e.message}", e)
        }

        path.parent?.let {
            try {
                fileSystem.createDirectories(it)
            } catch (e: IOException) {
                throw GemSaveException("Failed to create directories for '${path}': ${e.message}", e)
            }
        }

        val tempPath = path.parent!! / "${path.name}.tmp"
        try {
            fileSystem.write(tempPath) { writeUtf8(encoded) }
            fileSystem.atomicMove(tempPath, path)
        } catch (e: IOException) {
            runCatching { fileSystem.delete(tempPath) }
            throw GemSaveException("Failed to write asset to '${path}': ${e.message}", e)
        }
    }

    fun load(
        path: Path,
        fileSystem: FileSystem,
        migrationPlan: GemAssetMigrationPlan = GemAssetMigrationPlan.empty,
        targetSchemaVersion: Version? = Gem.phase1SchemaVersion
    ): GemDecodedAsset {
        val serialized = try {
            fileSystem.read(path) { readUtf8() }
        } catch (e: IOException) {
            return GemDecodedAsset(
                asset = GemAsset(),
                originalSchemaVersion = Gem.phase1SchemaVersion,
                loadError = GemLoadError.IoError(cause = e, message = "Could not read '${path}': ${e.message}")
            )
        }
        return decode(
            serialized = serialized,
            migrationPlan = migrationPlan,
            targetSchemaVersion = targetSchemaVersion
        )
    }
}

/**
 * Thrown only by [GemJsonPersistence.save] when the write cannot be completed safely.
 * Load errors are non-throwing — see [GemDecodedAsset.loadError].
 */
class GemSaveException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class GemDecodedAsset(
    val asset: GemAsset,
    val originalSchemaVersion: Version,
    val appliedMigrations: List<GemAssetMigration> = emptyList(),
    val loadError: GemLoadError? = null
) {
    val wasMigrated: Boolean
        get() = appliedMigrations.isNotEmpty()

    val isLoadedWithErrors: Boolean
        get() = loadError != null
}

/**
 * Describes a non-fatal load error surfaced through [GemDecodedAsset] rather than thrown.
 * The editor can use this to show a visible warning instead of crashing.
 */
sealed interface GemLoadError {
    val message: String

    data class ParseError(val cause: Throwable, override val message: String) : GemLoadError
    data class MigrationError(val detail: GemMigrationError) : GemLoadError {
        override val message: String get() = detail.message
    }
    data class IoError(val cause: Throwable, override val message: String) : GemLoadError
}

interface GemAssetMigration {
    val fromSchemaVersion: Version
    val toSchemaVersion: Version

    fun migrate(asset: GemAsset): GemAsset
}

class GemAssetMigrationPlan private constructor(
    migrations: List<GemAssetMigration>
) {
    private val migrationsBySource: Map<Version, GemAssetMigration> = migrations.associateBy { it.fromSchemaVersion }

    init {
        require(migrationsBySource.size == migrations.size) {
            "Gem migrations must use unique source schema versions."
        }
    }

    /**
     * Migrates [asset] toward [targetSchemaVersion], returning a [GemMigrationResult] that
     * contains either the fully-migrated asset or the best-effort partial result plus an
     * [error][GemMigrationResult.error] describing what went wrong. Errors are never thrown —
     * callers can choose to show a degraded state instead of crashing.
     */
    fun migrate(
        asset: GemAsset,
        targetSchemaVersion: Version = Gem.phase1SchemaVersion
    ): GemMigrationResult {
        val applied = mutableListOf<GemAssetMigration>()
        var currentAsset = asset
        val visitedVersions = mutableSetOf<Version>()

        while (currentAsset.schemaVersion != targetSchemaVersion) {
            val currentVersion = currentAsset.schemaVersion

            if (!visitedVersions.add(currentVersion)) {
                return GemMigrationResult(
                    asset = currentAsset,
                    appliedMigrations = applied,
                    error = GemMigrationError.CyclicChain(
                        message = "Detected cyclic gem migration chain at schema version $currentVersion.",
                        atVersion = currentVersion
                    )
                )
            }

            val migration = migrationsBySource[currentVersion]
                ?: return GemMigrationResult(
                    asset = currentAsset,
                    appliedMigrations = applied,
                    error = GemMigrationError.MissingMigration(
                        message = "No gem migration registered from schema version $currentVersion to $targetSchemaVersion.",
                        fromVersion = currentVersion,
                        toVersion = targetSchemaVersion
                    )
                )

            if (migration.toSchemaVersion <= currentVersion) {
                return GemMigrationResult(
                    asset = currentAsset,
                    appliedMigrations = applied,
                    error = GemMigrationError.InvalidStep(
                        message = "Gem migration ${migration.fromSchemaVersion} -> ${migration.toSchemaVersion} must move forward.",
                        step = migration
                    )
                )
            }

            if (migration.toSchemaVersion > targetSchemaVersion) {
                return GemMigrationResult(
                    asset = currentAsset,
                    appliedMigrations = applied,
                    error = GemMigrationError.InvalidStep(
                        message = "Gem migration target ${migration.toSchemaVersion} overshoots requested schema $targetSchemaVersion.",
                        step = migration
                    )
                )
            }

            currentAsset = migration.migrate(currentAsset).copy(schemaVersion = migration.toSchemaVersion)
            applied += migration
        }

        return GemMigrationResult(asset = currentAsset, appliedMigrations = applied)
    }

    companion object {
        val empty: GemAssetMigrationPlan = GemAssetMigrationPlan(emptyList())

        fun of(vararg migrations: GemAssetMigration): GemAssetMigrationPlan = GemAssetMigrationPlan(migrations.toList())
    }
}

data class GemMigrationResult(
    val asset: GemAsset,
    val appliedMigrations: List<GemAssetMigration>,
    val error: GemMigrationError? = null
) {
    val isSuccess: Boolean get() = error == null
}

sealed interface GemMigrationError {
    val message: String

    data class MissingMigration(
        override val message: String,
        val fromVersion: Version,
        val toVersion: Version
    ) : GemMigrationError

    data class CyclicChain(
        override val message: String,
        val atVersion: Version
    ) : GemMigrationError

    data class InvalidStep(
        override val message: String,
        val step: GemAssetMigration
    ) : GemMigrationError
}

private operator fun Version.compareTo(other: Version): Int = when {
    major != other.major -> major.compareTo(other.major)
    minor != other.minor -> minor.compareTo(other.minor)
    else -> hotfix.compareTo(other.hotfix)
}
