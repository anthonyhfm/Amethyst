package dev.anthonyhfm.amethyst.conversion.ableton.utils

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.data.DeviceChain
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiClip
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiTrack
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import org.w3c.dom.NodeList
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbletonArrangementClipTest {
    @Test
    fun splitCopiesDecodeOnlyTheirVisibleNotesAndPageControls() = withTempo {
        val actions = actions(
            clips = listOf(
                decodeClip(start = 10.0, end = 12.0, relative = 0.0),
                decodeClip(start = 12.0, end = 14.0, relative = 2.0),
            ),
        )

        assertEquals(expected = 6, actual = actions.values.flatten().count { it.down })
        assertEquals(
            expected = listOf(0.0, 500.0, 1_000.0, 1_500.0),
            actual = actions.filterValues { events -> events.any { it.down && it.x != 9 } }.keys.sorted(),
        )
        assertEquals(
            expected = listOf(9 to 1),
            actual = actions.getValue(key = 0.0).filter { it.down && it.x == 9 }.map { it.x to it.y },
        )
        assertEquals(
            expected = listOf(9 to 2),
            actual = actions.getValue(key = 1_000.0).filter { it.down && it.x == 9 }.map { it.x to it.y },
        )
        assertEquals(expected = 2_000.0, actual = actions.keys.maxOrNull())
    }

    @Test
    fun loopOffsetRepeatsInsideNonzeroLoopAndClipsFinalRelease() = withTempo {
        val clip = decodeClip(
            start = 10.0,
            end = 14.5,
            relative = 1.0,
            loopStart = 2.0,
            loopEnd = 4.0,
            noteXml = keyTrack(pitch = 36, time = 2.25, duration = 0.75) +
                keyTrack(pitch = 37, time = 3.25, duration = 0.75),
        )
        val actions = actions(clips = listOf(clip))

        assertEquals(
            expected = listOf(125.0, 625.0, 1_125.0, 1_625.0, 2_125.0),
            actual = actions.filterValues { events -> events.any { it.down } }.keys.sorted(),
        )
        assertEquals(expected = 2_250.0, actual = actions.keys.maxOrNull())
        assertTrue(actual = actions.getValue(key = 2_250.0).all { !it.down })
    }

    @Test
    fun adjacentLoopNotesReleaseBeforeTheyRetrigger() = withTempo {
        val clip = decodeClip(
            start = 0.0,
            end = 4.0,
            relative = 0.0,
            loopEnd = 2.0,
            noteXml = keyTrack(pitch = 36, time = 0.0, duration = 3.0),
        )
        val actions = actions(clips = listOf(clip))

        assertEquals(expected = listOf(false, true), actual = actions.getValue(key = 1_000.0).map { it.down })
        assertEquals(expected = listOf(false), actual = actions.getValue(key = 2_000.0).map { it.down })
        assertEquals(expected = 2, actual = actions.values.flatten().count { it.down })
    }

    @Test
    fun nonLoopingClipsUseStartMarkerAndDoNotRepeatOrExportHiddenOnsets() = withTempo {
        val clip = decodeClip(
            start = 8.0,
            end = 10.0,
            relative = 0.0,
            loopStart = 2.0,
            loopEnd = 3.0,
            looping = false,
            noteXml = keyTrack(pitch = 36, time = 1.0) +
                keyTrack(pitch = 37, time = 2.0, duration = 3.0) +
                keyTrack(pitch = 38, time = 4.0),
        )
        val actions = actions(clips = listOf(clip))

        assertEquals(expected = setOf(0.0, 1_000.0), actual = actions.keys)
        assertTrue(actual = actions.getValue(key = 0.0).single().down)
        assertFalse(actual = actions.getValue(key = 1_000.0).single().down)
    }

    @Test
    fun disabledClipsAndNotesAreIgnored() = withTempo {
        val disabledClip = decodeClip(start = 0.0, end = 4.0, relative = 0.0, disabled = true)
        val disabledNote = decodeClip(
            start = 4.0,
            end = 8.0,
            relative = 0.0,
            noteXml = keyTrack(pitch = 36, time = 0.0, enabled = false),
        )

        assertTrue(actual = disabledClip.disabled.value)
        assertFalse(actual = disabledNote.notes.keyTracks.tracks.single().notes.notes.single().isEnabled)
        assertTrue(actual = actions(clips = listOf(disabledClip, disabledNote)).isEmpty())
    }

    @Test
    fun nonLoopingOutMarkerBoundsOnsetsAndReleases() = withTempo {
        val clip = decodeClip(
            start = 0.0,
            end = 4.0,
            relative = 0.0,
            looping = false,
            noteXml = keyTrack(pitch = 36, time = 0.0, duration = 4.0) +
                keyTrack(pitch = 37, time = 2.0),
        )
        val bounded = clip.copy(loop = clip.loop!!.copy(outMarker = MidiClip.CurrentTimeStamp(value = 2.0)))
        val actions = actions(clips = listOf(bounded))

        assertEquals(expected = listOf(0.0, 1_000.0), actual = actions.keys.sorted())
        assertEquals(expected = listOf(false), actual = actions.getValue(key = 1_000.0).map { it.down })
    }

    @Test
    fun preLoopIntroDoesNotRepeatAndOffsetBeyondLoopWraps() {
        val intro = decodeClip(
            start = 10.0,
            end = 16.0,
            relative = -1.0,
            loopStart = 2.0,
            loopEnd = 4.0,
        )
        assertEquals(
            expected = listOf(10.5 to 10.75),
            actual = AbletonTutorialDetector.projectNoteToArrangement(clip = intro, time = 1.5, duration = 0.25),
        )
        assertEquals(
            expected = listOf(11.5 to 11.75, 13.5 to 13.75, 15.5 to 15.75),
            actual = AbletonTutorialDetector.projectNoteToArrangement(clip = intro, time = 2.5, duration = 0.25),
        )
        val wrapped = intro.copy(loop = intro.loop!!.copy(startRelative = MidiClip.CurrentTimeStamp(value = 5.0)))
        assertEquals(
            expected = listOf(10.25 to 10.5, 12.25 to 12.5, 14.25 to 14.5),
            actual = AbletonTutorialDetector.projectNoteToArrangement(clip = wrapped, time = 3.25, duration = 0.25),
        )
    }

    @Test
    fun invalidTimingCannotCreateActions() {
        val clip = decodeClip(start = 0.0, end = 4.0, relative = 0.0)
        for ((time, duration) in listOf(Double.NaN to 1.0, 0.0 to Double.POSITIVE_INFINITY, 0.0 to -1.0)) {
            assertTrue(actual = AbletonTutorialDetector.projectNoteToArrangement(clip = clip, time = time, duration = duration).isEmpty())
        }
        assertTrue(
            actual = AbletonTutorialDetector.projectNoteToArrangement(
                clip = clip.copy(currentEnd = MidiClip.CurrentTimeStamp(value = 0.0)),
                time = 0.0,
                duration = 1.0,
            ).isEmpty(),
        )
    }

    @Test
    fun optionalGoldenArchiveHasOneVisibleRecording() {
        val path = System.getenv("AMETHYST_GOLDEN_ZIP") ?: return
        val document = ZipFile(path).use { archive ->
            val entry = archive.entries().asSequence().first { !it.name.contains("__MACOSX") && it.name.endsWith(".als") }
            val bytes = archive.getInputStream(entry).use { it.readBytes() }
            GZIPInputStream(ByteArrayInputStream(bytes)).use { stream ->
                DocumentBuilderFactory.newInstance().apply {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                }.newDocumentBuilder().parse(stream)
            }
        }
        val clips = XPathFactory.newInstance().newXPath().evaluate(
            "/Ableton/LiveSet/Tracks/MidiTrack[Name/EffectiveName/@Value='Audio']/" +
                "DeviceChain/MainSequencer/ClipTimeable/ArrangerAutomation/Events/MidiClip",
            document,
            XPathConstants.NODESET,
        ) as NodeList
        val transformer = TransformerFactory.newInstance().newTransformer()
        val parsed = (0 until clips.length).map { index ->
            val writer = StringWriter()
            transformer.transform(DOMSource(clips.item(index)), StreamResult(writer))
            AbletonConverter.xml.decodeFromString(deserializer = MidiClip.serializer(), string = writer.toString())
        }
        withTempo(bpm = 128.0) {
            val actions = actions(clips = parsed)
            val page2 = actions.filterKeys { time -> time >= 34.0 * 468.75 && time < 66.0 * 468.75 }
            assertEquals(expected = 11, actual = parsed.size)
            assertEquals(expected = 652, actual = actions.values.flatten().count { it.down })
            assertEquals(expected = 60, actual = page2.values.flatten().count { it.down })
            assertEquals(expected = 422.0 * 468.75, actual = actions.keys.maxOrNull())
            assertFalse(actual = page2.getValue(key = 34.0 * 468.75).any { it.down && it.x == 9 && it.y == 1 })
        }
    }

    private fun actions(clips: List<MidiClip>): Map<Double, List<AutoPlayData.Action>> =
        AbletonTutorialDetector.getTutorialForTrack(
            track = MidiTrack(
                id = 1,
                _name = MidiTrack.Name(effectiveName = MidiTrack.Name.EffectiveName(value = "Tutorial")),
                deviceChain = DeviceChain(
                    deviceChain = DeviceChain.DeviceChain(devices = DeviceChain.DeviceChain.Devices(devices = emptyList())),
                    mainSequencer = DeviceChain.MainSequencer(
                        clipTimeable = DeviceChain.MainSequencer.ClipTimeable(
                            arrangerAutomation = DeviceChain.MainSequencer.ClipTimeable.ArrangerAutomation(
                                events = DeviceChain.MainSequencer.ClipTimeable.ArrangerAutomation.Events(clips = clips),
                            ),
                        ),
                    ),
                ),
            ),
            offset = IntOffset.Zero,
        )

    private fun decodeClip(
        start: Double,
        end: Double,
        relative: Double,
        loopStart: Double = 0.0,
        loopEnd: Double = 4.0,
        looping: Boolean = true,
        disabled: Boolean = false,
        noteXml: String = keyTrack(pitch = 36, time = 0.0) +
            keyTrack(pitch = 37, time = 1.0) +
            keyTrack(pitch = 38, time = 2.0) +
            keyTrack(pitch = 39, time = 3.0) +
            keyTrack(pitch = 100, time = 0.0, duration = 2.0) +
            keyTrack(pitch = 101, time = 2.0, duration = 2.0),
    ): MidiClip = AbletonConverter.xml.decodeFromString(
        deserializer = MidiClip.serializer(),
        string = """
            <MidiClip Time="$start">
                <CurrentStart Value="$start"/>
                <CurrentEnd Value="$end"/>
                <Loop>
                    <LoopStart Value="$loopStart"/>
                    <LoopEnd Value="$loopEnd"/>
                    <StartRelative Value="$relative"/>
                    <LoopOn Value="$looping"/>
                </Loop>
                <Disabled Value="$disabled"/>
                <Notes><KeyTracks>$noteXml</KeyTracks></Notes>
            </MidiClip>
        """.trimIndent(),
    )

    private fun keyTrack(
        pitch: Int,
        time: Double,
        duration: Double = 0.5,
        enabled: Boolean = true,
    ): String = """
        <KeyTrack>
            <MidiKey Value="$pitch"/>
            <Notes><MidiNoteEvent Time="$time" Duration="$duration" Velocity="100" IsEnabled="$enabled"/></Notes>
        </KeyTrack>
    """.trimIndent()

    private fun withTempo(bpm: Double = 120.0, block: () -> Unit) {
        val previous = AbletonConverter.bpm
        try {
            AbletonConverter.bpm = bpm
            block()
        } finally {
            AbletonConverter.bpm = previous
        }
    }
}
