package dev.anthonyhfm.amethyst.conversion.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.AmethystConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceInstrumentAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.ApolloConverter
import dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat
import dev.anthonyhfm.amethyst.core.util.determineFormat
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.OriginalSimplerAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.Ableton
import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.GroupTrack
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiTrack
import dev.anthonyhfm.amethyst.conversion.ableton.data.OriginalSimpler
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.Compressor2
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiChainReader
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayout
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayoutDetector
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonTutorialDetector
import dev.anthonyhfm.amethyst.conversion.ableton.utils.Dual2LightLayoutScanner
import dev.anthonyhfm.amethyst.conversion.ableton.utils.OriginalSimplerPrerenderer
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiExtensionMaskRouter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.PaletteFileParser
import dev.anthonyhfm.amethyst.conversion.ableton.utils.toFileHash
import dev.anthonyhfm.amethyst.core.util.FileHelper
import dev.anthonyhfm.amethyst.core.util.Palettes
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.core.util.ProjectArchiveEntry
import dev.anthonyhfm.amethyst.core.util.ProjectArchiveReader
import dev.anthonyhfm.amethyst.core.util.determineProjectArchiveFormat
import dev.anthonyhfm.amethyst.core.util.openProjectArchive
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.chain.data.findMaxMacroIndex
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import dev.anthonyhfm.amethyst.workspace.data.Macro
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceSettings
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.nameWithoutExtension
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.readString
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.serialization.InputKind
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor

object AbletonConverter : AmethystConverter {
    var file: PlatformFile? = null
        internal set

    var bpm: Double = 120.0
        internal set

    var liveVersion: LiveVersion? = null
        private set

    var projectLayout: AbletonLayout? = null
        private set

    internal var launchpadLayout: AbletonLaunchpadLayout? = null
        internal set

    var palette: Array<Triple<Int, Int, Int>> = Palettes.novation
        private set

    var audioMap: Map<OriginalSimplerAdapter.OriginalSimplerData, SampleChainDeviceState> = emptyMap()
        private set

    private var audioSources: List<AudioSource> = emptyList()

    var isZip: Boolean = false
        private set

    val zipEntries: MutableMap<String, ProjectArchiveEntry> = mutableMapOf()

    private var zipArchive: ProjectArchiveReader? = null

    internal fun readZipEntry(path: String): ByteArray? = zipArchive?.readEntry(path)

    var zipStartPath: String = ""
        private set

    @OptIn(ExperimentalXmlUtilApi::class)
    val xml = XML(AbletonDevice.module) {
        defaultPolicy {
            autoPolymorphic = true

            unknownChildHandler = object : UnknownChildHandler {
                override fun handleUnknownChildRecovering(
                    input: XmlReader,
                    inputKind: InputKind,
                    descriptor: XmlDescriptor,
                    name: QName?,
                    candidates: Collection<Any>,
                ): List<XML.ParsedData<*>> {
                    return emptyList()
                }
            }
        }
    }

    private fun loadPalette(palettePath: String?) {
        if (palettePath.isNullOrBlank()) {
            palette = Palettes.novation
        } else {
            try {
                val paletteFile = PlatformFile(palettePath)
                runBlocking {
                    val content = paletteFile.readString()
                    palette = PaletteFileParser.parsePaletteFileContent(content)
                }
            } catch (e: Exception) {
                println("Failed to load palette file ($palettePath): ${e.message}")
                palette = Palettes.novation
            }
        }
    }

    @OptIn(ExperimentalXmlUtilApi::class)
    override fun convertZipToWorkspace(file: PlatformFile, palettePath: String?): SavableWorkspaceData =
        convertZipToWorkspace(file, palettePath, dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.reporter)

    @OptIn(ExperimentalXmlUtilApi::class)
    fun convertZipToWorkspace(
        file: PlatformFile,
        palettePath: String? = null,
        reporter: dev.anthonyhfm.amethyst.core.loading.ProgressReporter? = dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.reporter,
    ): SavableWorkspaceData {
        loadPalette(palettePath)
        isZip = true
        zipEntries.clear()
        try {
            val unzippingMsg = runCatching { runBlocking { getString(Res.string.home_loading_unzipping_archive) } }.getOrDefault("Extracting project archive...")
            reporter?.update(0.05f, statusText = unzippingMsg, detailText = file.name)

            // Clear old conversion files before opening Android's disk-backed archive reader.
            FileHelper.clearCache()
            val archive = checkNotNull(openProjectArchive(file)) {
                "Could not open project archive: ${file.name}"
            }
            zipArchive = archive

            // Only central-directory metadata is loaded here. Entry payloads stay compressed
            // on disk until a converter explicitly requests one.
            val entries = archive.entries.filter {
                !it.path.contains("__MACOSX")
            }

            entries.filter {
                it.path.endsWith(".amxd")
            }.forEach {
                readZipEntry(it.path)?.let { data ->
                    println("Hash (${data.toFileHash()}) - ${it.path.substringAfterLast("/")}")
                }
            }

            val alsEntry = entries
                .filter { it.path.endsWith(".als") }
                .minBy { it.path.length }
            val format = determineProjectArchiveFormat(entries.map(ProjectArchiveEntry::path))
            val approjCount = entries.count { it.path.endsWith(".approj") }
            val approjEntry = entries.firstOrNull {
                if (!it.path.endsWith(".approj")) return@firstOrNull false
                val split = it.path.split("/")
                split.size < 2 || !split[split.lastIndex - 1].lowercase().endsWith("backups")
            }

            zipStartPath = alsEntry.path.substringBeforeLast("/")
            entries.forEach { zipEntries[it.path] = it }

            val projectName = alsEntry.path.substringAfterLast("/").removeSuffix(".als")
            val readingAlsMsg = runCatching { runBlocking { getString(Res.string.home_loading_reading_als) } }.getOrDefault("Reading Ableton Live-Set...")
            reporter?.update(0.12f, statusText = readingAlsMsg, detailText = "$projectName.als")

            val abletonData = decodeAbletonAls(
                checkNotNull(readZipEntry(alsEntry.path)) { "Could not read ${alsEntry.path}" },
            )
            val abletonWorkspace = runLiveConversion(
                name = projectName,
                abletonData = abletonData,
                reporter = reporter
            )

            if (format == ZippedProjectFormat.ABLETON_APOLLO && approjEntry != null) {
                println("Possible Apollo Entries: $approjCount")
                return try {
                    val apolloLightMsg = runCatching { runBlocking { getString(Res.string.home_loading_apollo_light_chains) } }.getOrDefault("Loading Apollo light chains...")
                    reporter?.update(0.92f, statusText = apolloLightMsg, detailText = approjEntry.path.substringAfterLast("/"))
                    val apolloWorkspace = ApolloConverter.convertBytesToWorkspace(
                        checkNotNull(readZipEntry(approjEntry.path)) { "Could not read ${approjEntry.path}" },
                    )
                    val maxMacroIndex = maxOf(
                        apolloWorkspace.lights.findMaxMacroIndex(),
                        abletonWorkspace.sampling.findMaxMacroIndex(),
                        abletonWorkspace.lights.findMaxMacroIndex()
                    )
                    val macroCount = maxOf(maxMacroIndex + 1, apolloWorkspace.macros.size, abletonWorkspace.macros.size, 1)
                    val mergedMacros = List(macroCount) { idx ->
                        apolloWorkspace.macros.getOrNull(idx)
                            ?: abletonWorkspace.macros.getOrNull(idx)
                            ?: Macro(0)
                    }
                    abletonWorkspace.copy(
                        lights = apolloWorkspace.lights,
                        launchpadDevices = apolloWorkspace.launchpadDevices.ifEmpty { abletonWorkspace.launchpadDevices },
                        macros = mergedMacros
                    )
                } catch (e: Exception) {
                    println("Apollo conversion failed, falling back to Ableton lights: ${e.message}")
                    abletonWorkspace
                }
            }

            return abletonWorkspace
        } finally {
            isZip = false
            zipEntries.clear()
            try {
                zipArchive?.close()
            } finally {
                zipArchive = null
            }
        }
    }

    override fun convertToWorkspace(path: String, palettePath: String?): SavableWorkspaceData =
        convertToWorkspace(PlatformFile(path), palettePath)

    fun convertToWorkspace(
        file: PlatformFile,
        palettePath: String?,
        reporter: dev.anthonyhfm.amethyst.core.loading.ProgressReporter? = dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.reporter,
    ): SavableWorkspaceData {
        MxDeviceMidiEffectAdapter.fileHashMap.clear()
        MxDeviceInstrumentAdapter.fileHashMap.clear()
        isZip = false

        this.file = file

        val readingMsg = runCatching { runBlocking { getString(Res.string.home_loading_reading_als) } }.getOrDefault("Reading Ableton Live-Set...")
        reporter?.update(0.08f, statusText = readingMsg, detailText = file.name)

        val abletonData = decodeAbletonAls(
            runBlocking { AbletonConverter.file?.readBytes() ?: ByteArray(0) },
        )

        loadPalette(palettePath)

        return runLiveConversion(
            name = AbletonConverter.file?.nameWithoutExtension ?: "Ableton Live-Set",
            abletonData = abletonData,
            reporter = reporter
        )
    }

    fun runLiveConversion(
        name: String,
        abletonData: Ableton,
        reporter: dev.anthonyhfm.amethyst.core.loading.ProgressReporter? = dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.reporter,
    ): SavableWorkspaceData {
        println("Origin: ${abletonData.creator}")
        val analyzingLayoutMsg = runCatching { runBlocking { getString(Res.string.home_loading_analyzing_layout) } }.getOrDefault("Analyzing project layout...")
        reporter?.update(0.18f, statusText = analyzingLayoutMsg, detailText = name)

        val audioRenderer = OriginalSimplerPrerenderer()

        val layout = AbletonLayoutDetector.detectLayout(
            tracks = abletonData.liveSet.tracks.midiTracks
        )

        bpm = abletonData.liveSet.masterTrack.deviceChain.mixer.tempo.manual.value
        projectLayout = layout
        val launchpadLayout = AbletonLaunchpadLayout.create(
            count = if (layout is AbletonLayout.Single) 1 else 2,
        )
        this.launchpadLayout = launchpadLayout
        val leftLaunchpadOffset = launchpadLayout.target(index = 0).offset

        return try {
            MidiExtensionMaskRouter.prepare(abletonData.liveSet.tracks.midiTracks)
            when (layout) {
                is AbletonLayout.Single -> {
                    MidiExtensionMaskRouter.registerOutputTarget(layout.lightsTrack, leftLaunchpadOffset)
                }
                is AbletonLayout.Dual2Light -> {
                    MidiExtensionMaskRouter.registerOutputTarget(layout.lightsLeft, leftLaunchpadOffset)
                    MidiExtensionMaskRouter.registerOutputTarget(
                        layout.lightsRight,
                        launchpadLayout.target(index = 1).offset,
                    )
                }
                is AbletonLayout.Dual4Light -> {
                    MidiExtensionMaskRouter.registerOutputTarget(layout.lightsLeft, leftLaunchpadOffset)
                    MidiExtensionMaskRouter.registerOutputTarget(layout.lightsRightToLeft, leftLaunchpadOffset)
                    MidiExtensionMaskRouter.registerOutputTarget(
                        layout.lightsLeftToRight,
                        launchpadLayout.target(index = 1).offset,
                    )
                    MidiExtensionMaskRouter.registerOutputTarget(
                        layout.lightsRight,
                        launchpadLayout.target(index = 1).offset,
                    )
                }
            }
            MidiExtensionMaskRouter.materializeMasks()

            liveVersion = when {
                abletonData.minorVersion.startsWith("9") -> LiveVersion.LIVE_9
                abletonData.minorVersion.startsWith("10") -> LiveVersion.LIVE_10
                abletonData.minorVersion.startsWith("11") -> LiveVersion.LIVE_11
                abletonData.minorVersion.startsWith("12") -> LiveVersion.LIVE_12
                else -> null
            }

            val autoPlayData: AutoPlayData = AbletonTutorialDetector.getAutoPlayData(layout, abletonData.liveSet.tracks.midiTracks)

            val audibleAudioTracks = AbletonLayoutDetector.findAudibleAudioTracks(
                layout = layout,
                tracks = abletonData.liveSet.tracks.midiTracks,
            )
            val sampleTracks = abletonData.liveSet.tracks.midiTracks.filter { track ->
                MidiChainReader.getAllDevicesOfType<OriginalSimpler>(track).isNotEmpty()
            }

            val decodingSamplesMsg = runCatching { runBlocking { getString(Res.string.home_loading_decoding_audio_samples) } }.getOrDefault("Decoding audio samples...")
            reporter?.update(0.25f, statusText = decodingSamplesMsg, detailText = null)
            val audioReporter = reporter?.subReporter(0.25f, 0.75f)
            val renderedAudio = audioRenderer.decodeAll(sampleTracks, reporter = audioReporter)
            audioMap = renderedAudio.states
            audioSources = renderedAudio.sources

            val analyzingLightsMsg = runCatching { runBlocking { getString(Res.string.home_loading_analyzing_light_chains) } }.getOrDefault("Analyzing light chains...")
            reporter?.update(0.78f, statusText = analyzingLightsMsg, detailText = null)

            if (layout is AbletonLayout.Dual2Light) {
                layout.lightsLeft?.let { Dual2LightLayoutScanner.scanTrackForMixer(it, leftLaunchpadOffset) }
                layout.lightsRight?.let {
                    Dual2LightLayoutScanner.scanTrackForMixer(it, launchpadLayout.target(index = 1).offset)
                }
            }

            val rawLights = if (layout is AbletonLayout.Dual2Light || layout is AbletonLayout.Dual4Light) {
                StateChain(
                    devices = listOf(
                        GroupChainDeviceState(
                            groups = if (layout is AbletonLayout.Dual2Light) {
                                listOf(
                                    Group(
                                        name = "Left",
                                        stateChain = layout.lightsLeft?.let {
                                            MidiChainReader(offset = leftLaunchpadOffset)
                                                .readMidiChain(it)
                                        } ?: StateChain(emptyList())
                                    ),
                                    Group(
                                        name = "Right",
                                        stateChain = layout.lightsRight?.let {
                                            MidiChainReader(offset = launchpadLayout.target(index = 1).offset)
                                                .readMidiChain(it)
                                        } ?: StateChain(emptyList())
                                    )
                                ) + maskGroups()
                            } else if (layout is AbletonLayout.Dual4Light) {
                                listOf(
                                    Group(
                                        name = "Left",
                                        stateChain = layout.lightsLeft?.let {
                                            MidiChainReader(offset = leftLaunchpadOffset)
                                                .readMidiChain(it)
                                        } ?: StateChain(emptyList())
                                    ),
                                    Group(
                                        name = "Left to Right",
                                        stateChain = layout.lightsLeftToRight?.let {
                                            MidiChainReader(
                                                offset = leftLaunchpadOffset,
                                                outputOffset = launchpadLayout.offsetBetween(fromIndex = 0, toIndex = 1),
                                            )
                                                .readMidiChain(it)
                                        } ?: StateChain(emptyList())
                                    ),
                                    Group(
                                        name = "Right",
                                        stateChain = layout.lightsRight?.let {
                                            MidiChainReader(offset = launchpadLayout.target(index = 1).offset)
                                                .readMidiChain(it)
                                        } ?: StateChain(emptyList())
                                    ),
                                    Group(
                                        name = "Right to Left",
                                        stateChain = layout.lightsRightToLeft?.let {
                                            MidiChainReader(
                                                offset = launchpadLayout.target(index = 1).offset,
                                                outputOffset = launchpadLayout.offsetBetween(fromIndex = 1, toIndex = 0),
                                            )
                                                .readMidiChain(it)
                                        } ?: StateChain(emptyList())
                                    )
                                ) + maskGroups()
                            } else error("This should never happen")
                        )
                    )
                )
            } else {
                val direct = (layout as AbletonLayout.Single).lightsTrack?.let {
                    MidiChainReader(offset = leftLaunchpadOffset).readMidiChain(it)
                } ?: StateChain(emptyList())
                val masks = maskGroups()
                if (masks.isEmpty()) direct else StateChain(
                    devices = listOf(
                        GroupChainDeviceState(
                            groups = listOf(Group(name = "Lights", stateChain = direct)) + masks,
                        )
                    )
                )
            }

            fun buildLegacyRawSamples(): StateChain = if (layout is AbletonLayout.Dual2Light || layout is AbletonLayout.Dual4Light) {
                StateChain(
                    devices = listOf(
                        GroupChainDeviceState(
                            groups = if (layout is AbletonLayout.Dual2Light) {
                                listOf(
                                    Group(
                                        name = "Left",
                                        stateChain = readParallelTracks(
                                            tracks = audibleAudioTracks.left,
                                            offset = leftLaunchpadOffset,
                                        )
                                    ),
                                    Group(
                                        name = "Right",
                                        stateChain = readParallelTracks(
                                            tracks = audibleAudioTracks.right,
                                            offset = launchpadLayout.target(index = 1).offset,
                                        )
                                    )
                                )
                            } else if (layout is AbletonLayout.Dual4Light) {
                                listOf(
                                    Group(
                                        name = "Left",
                                        stateChain = readParallelTracks(
                                            tracks = audibleAudioTracks.left,
                                            offset = leftLaunchpadOffset,
                                        )
                                    ),
                                    Group(
                                        name = "Right",
                                        stateChain = readParallelTracks(
                                            tracks = audibleAudioTracks.right,
                                            offset = launchpadLayout.target(index = 1).offset,
                                        )
                                    )
                                )
                            } else error("This should never happen")
                        )
                    )
                )
            } else {
                readParallelTracks(
                    tracks = audibleAudioTracks.left,
                    offset = leftLaunchpadOffset,
                )
            }

            val rawSamples = buildAbletonAudioGraph(
                sampleTracks = sampleTracks,
                groupTracks = abletonData.liveSet.tracks.groupTracks,
                masterDevices = abletonData.liveSet.masterTrack.deviceChain.devices,
                audibleAudioTracks = audibleAudioTracks,
                sidechainTrackIds = sidechainTrackIds(
                    tracks = abletonData.liveSet.tracks.midiTracks,
                    groupTracks = abletonData.liveSet.tracks.groupTracks,
                    masterDevices = abletonData.liveSet.masterTrack.deviceChain.devices,
                ),
                leftOffset = leftLaunchpadOffset,
                rightOffset = if (launchpadLayout.launchpads.size > 1) {
                    launchpadLayout.target(index = 1).offset
                } else {
                    leftLaunchpadOffset
                },
            ) ?: buildLegacyRawSamples()

            val projectAudioSources = audioSources

            val maxMacroIndex = maxOf(
                rawLights.findMaxMacroIndex(),
                rawSamples.findMaxMacroIndex()
            )
            val macroCount = maxOf(maxMacroIndex + 1, 1)
            val macros = List(macroCount) { Macro(0) }

            SavableWorkspaceData(
                title = name,
                lights = rawLights,
                sampling = rawSamples,
                autoPlay = autoPlayData,
                settings = WorkspaceSettings(
                    bpm = bpm
                ),
                launchpadDevices = launchpadLayout.launchpads,
                macros = macros,
                audioSources = projectAudioSources,
            )
        } finally {
            audioMap = emptyMap()
            audioSources = emptyList()
            MxDeviceMidiEffectAdapter.fileHashMap.clear()
            MxDeviceInstrumentAdapter.fileHashMap.clear()
            projectLayout = null
            this.launchpadLayout = null
            MidiExtensionMaskRouter.clear()
        }
    }

    private fun maskGroups(): List<Group> {
        return MidiExtensionMaskRouter.channels.map { channel ->
            Group(
                name = "MIDIext Mask $channel",
                stateChain = StateChain(devices = listOf(MidiExtensionMaskRouter.createMask(channel))),
            )
        }
    }

    private fun readParallelTracks(
        tracks: List<MidiTrack>,
        offset: IntOffset,
    ): StateChain {
        if (tracks.isEmpty()) return StateChain(emptyList())
        if (tracks.size == 1) return MidiChainReader(offset = offset).readMidiChain(tracks.single())

        return StateChain(
            devices = listOf(
                GroupChainDeviceState(
                    groups = tracks.map { track ->
                        Group(
                            name = track.name,
                            stateChain = MidiChainReader(offset = offset).readMidiChain(track),
                        )
                    },
                )
            )
        )
    }

    private fun buildAbletonAudioGraph(
        sampleTracks: List<MidiTrack>,
        groupTracks: List<GroupTrack>,
        masterDevices: List<AbletonDevice>,
        audibleAudioTracks: AbletonLayoutDetector.AudibleAudioTracks,
        sidechainTrackIds: Set<Int>,
        leftOffset: IntOffset,
        rightOffset: IntOffset,
    ): StateChain? {
        if (sampleTracks.isEmpty() || groupTracks.isEmpty()) return null

        val graphTracks = (
            audibleAudioTracks.left + audibleAudioTracks.right +
                sampleTracks.filter { it.id in sidechainTrackIds }
            )
            .distinctBy(MidiTrack::id)

        val relevantGroupIds = mutableSetOf<Int>()
        val groupsById = groupTracks.associateBy(GroupTrack::id)
        graphTracks.forEach { track ->
            var parentId = track.trackGroupId.value
            val visited = mutableSetOf<Int>()
            while (parentId >= 0 && visited.add(parentId)) {
                relevantGroupIds += parentId
                parentId = groupsById[parentId]?.trackGroupId?.value ?: -1
            }
        }

        val rightTrackIds = audibleAudioTracks.right.mapTo(mutableSetOf(), MidiTrack::id)
        val rightInputTargets = audibleAudioTracks.right
            .map { it.deviceChain.midiInputRouting.target.value }
            .filter(String::isNotBlank)
            .toSet()
        val convertedTracks = graphTracks.associate { track ->
            val isRightTrack = track.id in rightTrackIds ||
                track.deviceChain.midiInputRouting.target.value in rightInputTargets
            val offset = if (isRightTrack) rightOffset else leftOffset
            val mainOutputEnabled = track.deviceChain.mixer.on.manual.value &&
                track.deviceChain.mixer.speaker.manual.value
            val busId = abletonTrackPostFxBusId(track.id)
            track.id to MidiChainReader(offset = offset)
                .readMidiChain(track)
                .routeSampleOutput(
                    audibleOutput = mainOutputEnabled,
                    sidechainBusId = busId,
                )
        }

        fun adaptedEffects(devices: List<AbletonDevice>): List<DeviceState> = devices.flatMap { device ->
            AbletonAdapter.resolveAdapter(device = device)?.toDeviceStates().orEmpty()
        }

        fun buildGroup(group: GroupTrack): StateChain {
            val branches = buildList {
                groupTracks
                    .filter { it.id in relevantGroupIds && it.trackGroupId.value == group.id }
                    .forEach { child ->
                        add(Group(name = child.name, stateChain = buildGroup(child)))
                    }
                graphTracks
                    .filter { it.trackGroupId.value == group.id }
                    .forEach { track ->
                        add(Group(name = track.name, stateChain = convertedTracks.getValue(track.id)))
                    }
            }
            val devices = mutableListOf<DeviceState>()
            if (branches.isNotEmpty()) {
                devices += GroupChainDeviceState(groups = branches)
            }
            devices += adaptedEffects(group.deviceChain.devices)
            if (!group.deviceChain.mixer.on.manual.value || !group.deviceChain.mixer.speaker.manual.value) {
                devices.forEach { it.isMuted = true }
            }
            return StateChain(devices)
        }

        val topBranches = buildList {
            groupTracks
                .filter { it.id in relevantGroupIds && it.trackGroupId.value < 0 }
                .forEach { group ->
                    add(Group(name = group.name, stateChain = buildGroup(group)))
                }
            graphTracks
                .filter { it.trackGroupId.value < 0 }
                .forEach { track ->
                    add(Group(name = track.name, stateChain = convertedTracks.getValue(track.id)))
                }
        }
        if (topBranches.isEmpty()) return null

        val devices = mutableListOf<DeviceState>()
        if (topBranches.size == 1) {
            devices += topBranches.single().stateChain.devices
        } else {
            devices += GroupChainDeviceState(groups = topBranches)
        }
        devices += adaptedEffects(masterDevices)
        return StateChain(devices)
    }

    private fun abletonTrackPostFxBusId(trackId: Int): String =
        "ableton-track-$trackId-postfx"

    private fun sidechainTrackIds(
        tracks: List<MidiTrack>,
        groupTracks: List<GroupTrack>,
        masterDevices: List<AbletonDevice>,
    ): Set<Int> {
        val compressors = tracks.flatMap { track ->
            MidiChainReader.getAllDevicesOfType<Compressor2>(track)
        } +
            groupTracks.flatMap { it.deviceChain.devices.filterIsInstance<Compressor2>() } +
            masterDevices.filterIsInstance<Compressor2>()
        return compressors.mapNotNullTo(mutableSetOf()) { compressor ->
            Regex("^AudioIn/Track\\.(\\d+)/PostFxOut$")
                .matchEntire(compressor.sideChain.routedInput.routable.target.value)
                ?.groupValues?.get(1)?.toIntOrNull()
        }
    }

    private fun StateChain.routeSampleOutput(
        audibleOutput: Boolean,
        sidechainBusId: String,
    ): StateChain = copy(
        devices = devices.map { state ->
            val routed = when (state) {
                is SampleChainDeviceState -> state.copy(
                    audibleOutput = audibleOutput,
                    sidechainBusId = sidechainBusId,
                )
                is GroupChainDeviceState -> state.copy(
                    groups = state.groups.map { group ->
                        group.copy(stateChain = group.stateChain.routeSampleOutput(audibleOutput, sidechainBusId))
                    },
                )
                is MultiGroupChainDeviceState -> state.copy(
                    groups = state.groups.map { group ->
                        group.copy(stateChain = group.stateChain.routeSampleOutput(audibleOutput, sidechainBusId))
                    },
                    preprocessChain = state.preprocessChain.routeSampleOutput(audibleOutput, sidechainBusId),
                )
                else -> state
            }
            routed.also { it.isMuted = state.isMuted }
        },
    )

    internal fun launchpadTarget(offset: IntOffset): AbletonLaunchpadLayout.Target =
        checkNotNull(launchpadLayout) { "Ableton launchpads must be allocated before converting devices" }
            .targetAt(offset)

    internal fun coordinateFilter(
        launchpad: AbletonLaunchpadLayout.Target,
        localCoordinates: List<Pair<Int, Int>>,
    ): CoordinateFilterChainDeviceState =
        CoordinateFilterChainDeviceState(
            filters = localCoordinates.map { (x, y) -> Pair(x + launchpad.offset.x, y + launchpad.offset.y) },
            padFilters = localCoordinates.map { (x, y) -> launchpad.padFilter(x, y) },
        )

    enum class LiveVersion {
        LIVE_12,
        LIVE_11,
        LIVE_10,
        LIVE_9
    }

    private fun sanitizeAlsXml(raw: String): String = buildString(raw.length) {
        var index = 0
        while (index < raw.length) {
            when {
                raw.startsWith("</MainTrack>", index) -> {
                    append("</MasterTrack>")
                    index += "</MainTrack>".length
                }
                raw.startsWith("<MainTrack", index) -> {
                    append("<MasterTrack")
                    index += "<MainTrack".length
                }
                raw[index] == '\n' || raw[index] == '\r' || raw[index] == '\t' -> index++
                raw[index] == ' ' && index > 0 && raw[index - 1] == '>' -> index++
                raw[index] == ' ' && index + 1 < raw.length && raw[index + 1] == '<' -> index++
                else -> append(raw[index++])
            }
        }
    }

    internal fun decodeAbletonAls(compressedBytes: ByteArray): Ableton {
        val decoded = Zip.decode(compressedBytes)
        val sanitized = sanitizeAlsXml(decoded.decodeToString())
        return xml.decodeFromString(sanitized)
    }
}
