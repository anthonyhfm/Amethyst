# Amethyst 0.7.2 — Implementierungsbericht

Stand: 25.10.2026. Auftrag: `/Users/anthony/Downloads/AMETHYST_0.7.2_CODEX_SPEC.md`, vollständig gelesen. Änderungen ausschließlich lokal. Nach der Implementierung wurden vier lokale Commits ausdrücklich autorisiert; kein Push.

## Zusammenfassung

- Implementiert und automatisiert geprüft: **19/19**.
- Teilweise umgesetzt: **0/19**.
- Offen/blockiert: **0/19** innerhalb des beschriebenen Funktionsumfangs.
- Baseline: **599 Tests**, keine Fehler. Abschließender Release-Gate-Lauf: **630 Tests**, keine Fehler; Android und iOS-Simulator kompilieren.
- Zusätzliche Prüfung mit den tatsächlich vorhandenen Suima-Dateien: Ableton-Import, Apollo-Konvertierung sowie Page-3-Runtime mit schnellen Triggern und Page-Wechseln.
- Bekannte Restrisiken: physische Geräteabnahme ausstehend; ein bestehender LED-Callback-Test hatte in einem Zwischenlauf einen einzelnen Timeout und bestand isoliert erneut (Details unten).
- Die Nachweise sind Code-, Command-, Serialisierungs-, Runtime- und Compose-Pointertests. Eine manuelle Abnahme auf physischer Launchpad-/Audio-Hardware ist damit nicht behauptet; konkrete Schritte stehen unten.

## Phase 0 — Code-Inventur und Baseline

Das übergeordnete Verzeichnis `amethyst-platform` ist eine Sammlung getrennter Repositories. Bearbeitet wurde ausschließlich die Studio-App in `amethyst`.

Git-Ausgangszustand: Branch `main`, `ahead 1`, HEAD `72eda86cdea9e1beac0f7b0dd5fef8b9146bf39f`. Vorhandene Nutzeränderungen: `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` und das unversionierte Verzeichnis `iosApp/KotlinMultiplatformLinkedPackage/.build/`. Beide blieben unangetastet. Bis zur späteren Commit-Freigabe keine Branch-Wechsel, Commits oder Pushes.

Während der Bearbeitung erschien außerhalb meiner Git-Befehle der parallele Commit `add69c2fccabd16a62f3d2d712bd0ef1394bb9e0` (`fix launchpad swap sheets`); der Branch stand vor dem Verpacken der Release-Änderungen deshalb auf `main`, `ahead 2`. Er betrifft sechs Launchpad-Dialog-/Action-Dateien, nicht die hier implementierten Release-Änderungen. Dieser fremde Stand wurde weder zurückgesetzt noch überschrieben. Bis zur späteren Commit-Freigabe blieben meine Release-Änderungen uncommittet; bis dahin wurden von mir ausschließlich lesende Git-Befehle ausgeführt.

Build/Plattformen: Kotlin Multiplatform mit Compose, JVM-Desktop, Android, iOS Arm64 und iOS Simulator Arm64. Module `composeApp` und `nativeEngine`; Letzteres bindet die Rust-Audio-/MIDI-Laufzeit ein. Desktop verwendet den bestehenden Nucleus-Composite-Build. Tests liegen in `commonTest` und `desktopTest`, einschließlich Headless-Compose-Szenen für reale Pointer-Eingaben.

| Bereich | Vorgefundene Einstiegspunkte / bestehendes Modell |
|---|---|
| Piano Roll | `timeline/PianoRollWorkspaceMode.kt`, `ui/pianoroll/PianoRollTransformPanel.kt`, `PianoRollInspectorSidebar.kt`, `transforms/PianoRollTransforms.kt` |
| Dokument und Commands | `TimelineRepository`, `TimelineCommandSurface`, `TimelineCommandExecutor`, `TimelineEditCommand`; `MidiEntry`, stabile `MidiNote.noteId` |
| Undo und Auswahl | `core/controls/undo/UndoManager.kt`, `UndoableAction.kt`, `selection/SelectionManager.kt`, `Selectable.PianoRollNote` |
| Clipboard und Tastatur | `ClipboardManager`, `ClipboardData.PianoRollNotes`, `ShortcutManager`, `TimelineKeyHandler`; Desktop-Routing in `WorkspaceWindow` |
| Device-/Chain-Drag | `workspace/chain/ui/ChainView.kt`, gemeinsames `ui/dnd`, `GroupEditorFoundation`, `GroupEditorActionLayer` |
| Clip-Drag und Lineal | `TimelineClipDragCoordinator`, `TimelineClipMoveEngine`, `TimelineLaneView`, `AudioClip`, `MidiClip`, `TimelineRuler` |
| Parameter-Controls | `ui/components/Dial.kt`, `primitives/Slider.kt`, `EffectDial`, `AutomatableSlider`, Parameter-Deskriptoren |
| Sidechain | `DuckerChainDevice` wird als Compressor dargestellt; gemeinsamer `Select` |
| Conversion/Dateiformat | Ableton `FlipAdapter`, Apollo-Adapter; `DeviceRegistry`, `StateChain`, `AmethystProtoBuf`, `SavableWorkspaceData` |
| Runtime | `FlipChainDevice`, `ChokeChainDevice`, `KeyframesChainDevice`, `LoopChainDevice`, `Chain`, `AudioExecutionPlan`, Heaven-Scheduler |

Geteilte Komponenten mit besonderem Regressionsrisiko: Select-Popups, gemeinsame Shortcuts/Fokussperren, Device-Kopien, Chain-Routing, Undo und Parameter-Controls. Die gesamte vorhandene Suite wurde deshalb zusätzlich zu den gezielten Tests ausgeführt.

## Architekturentscheidungen und Interpretationen

1. **Aktuelle Notes kommen aus dem Dokument.** Auswahl-Snapshots werden anhand der Note-ID gegen den aktuellen Clip aufgelöst und bei Wiederherstellung aktualisiert. Transforms ordnen Ergebnisse anhand der IDs zu; eine geänderte Listenreihenfolge kann keine falschen Notes ersetzen. Timeline- und eigenständiger Piano-Roll-Pfad bleiben erhalten.
2. **Vorhandene Undo-Pipeline bleibt maßgeblich.** Transform Copy und Paste verwenden die vorhandenen Note-Erstellungscommands. Device-Kopien verwenden gepackte, unabhängig deserialisierte States und neue Identitäten. Clip-Kopien benutzen denselben Move-Command mit einem Copy-Flag. Der bereits vorhandene experimentelle Chain-Effect-Clip ist eingeschlossen; dessen vorhandene Clip-ID wird im internen Drag-Key mitgeführt, ohne sein Projektformat zu ändern.
3. **Copy-Platzierung:** Transform Copy erzeugt zusätzliche Notes an den transformierten Positionen im selben Clip. Note-Paste setzt die früheste kopierte Note an den Edit-Cursor, sonst an den relativen Timeline-Playhead, sonst an `0`; Abstände bleiben erhalten. Device-Paste erfolgt per Shortcut und Kontextmenü vor dem links/erstmals ausgewählten Device, ohne Auswahl am Chain-Ende.
4. **Drag-Modifier:** Option/Alt kopiert beim Drop. Während eines Clip-Moves schaltet Shift das Snapping aus, damit Alt nicht gleichzeitig zwei Bedeutungen erhält. Die bisherigen Alt-Regeln für andere Editor-/Trim-Aktionen bleiben erhalten. Ungültige Drops verändern die Quelle nicht. Eine Clip-Kopie auf belegtem Ziel wird abgewiesen, damit sie ihre Quelle nicht überschreibt.
5. **Mute:** Devices behalten das vorhandene Bypass-Verhalten; Sample-Quellen, gemutete Chains und Clips unterdrücken ihre Ausgabe. Aktive Chokeable-Unterbäume werden beim Muten gestoppt. Device-Mute besitzt beobachtbaren Laufzeitzustand unabhängig von Parameter-State-Kopien. Chain-Mute ist ein ergänzendes Protobuf-Feld mit Default `false`; bestehende Device-Mute-Indizes bleiben erhalten. Audio-/MIDI-Clips bekommen ebenfalls optionale, standardmäßig falsche Mute-Felder. Keine erzwungene Migration alter Dateien.
6. **Repeats:** Im Keyframe-Modus Loop begrenzt Repeats die Zahl vollständiger Durchläufe auch bei gehaltenem Pad. Release beendet früher; ein neuer Press startet neu. Der vorhandene explizite Continuous-Modus bleibt unendlich. Infinity behält seine bestehende Bedeutung: letzte LEDs stehen lassen. Die vorhandene Hilfe wurde entsprechend präzisiert.
7. **Pinch:** Der Wert und Bilateral-State leben im bestehenden Piano-Roll-Modus, nicht in einem Transform-Snapshot. Apply, Undo und Auswahlwechsel setzen sie nicht zurück. Direkte Eingabe akzeptiert Punkt/Komma, klemmt auf `[-2, 2]` und verwirft nicht endliche/ungültige Eingaben sicher. Kein Live-Preview und keine neue Projekt-Persistenz für Tool-Einstellungen.

## Issue-Status

Dateinamen beziehen sich auf `composeApp/src/commonMain/kotlin/dev/anthonyhfm/amethyst`, sofern nicht anders angegeben.

| ID | Status | Änderung / relevante Dateien | Nachweis / Test | Hinweise |
|---|---|---|---|---|
| B01 | Implementiert, geprüft | `primitives/Select.kt`: begrenzte Scroll-Liste; `DuckerChainDevice.kt`: Auswahl per Quell-ID statt mehrdeutigem Label | `Release072ControlsTest.selectScrollReachesAndSelectsLastOfSixtySources`; bestehende Dynamics-/Parameter-Serialisierungstests | Popup kapselt Scrollen; keine Änderung am Sidechain-Datenformat |
| B02 | Implementiert, geprüft | `FlipChainDevice.kt` verarbeitet MIDI und LEDs und erhält Release/Metadaten | `convertedFlipPreservesMidiPressReleaseAndConfigurationAcrossReload`: Ableton-Adapter → Protobuf → AudioChain → Press/Release | Sampling-Palette unverändert; kein natives Hinzufügen von Flip |
| B03 | Implementiert, geprüft | `ChokeChainDevice.kt`: verschachtelte Unterbäume rekursiv stoppen; Choke selbst ist Chokeable | `nestedChokeStopsDescendantsWithoutTouchingOtherGroups`; `realSuimaPageThreeRapidTriggersAndPageChangesLeaveNoScheduledPlayback`; bestehende Choke-/Apollo-Tests | Reproduktion und genaue Grenze siehe unten |
| B04 | Implementiert, geprüft | `KeyframesChainDevice.kt`: Repeat-Limit im gehaltenen Loop; konsistente Duration; Hilfe `devices/Keyframes.md` | `heldKeyframeLoopStopsAtOneAndFourRepeatsAndRetriggers`; bestehende Completion-/Continuous-Tests | Explicit Continuous bleibt endlos; ungültiger Count unter 1 wird wie 1 behandelt |
| B05 | Implementiert, geprüft | `TimelineRuler.kt`: Seek während Drag, Locator-Drag bleibt erhalten | `rulerDragSeeksContinuouslyAtScrolledZoomedCoordinates`: Klick, Drag, Zoom 2, Scroll 300, Bewegung außerhalb, Release | Bestehender synchronisierter Seek-Pfad wird benutzt |
| B06 | Implementiert, geprüft | `PianoRollWorkspaceMode.kt`, Clipboard-/Shortcut-Kontext | `copyPasteKeepsAttributesAndIsOneUndoStep`; `pianoKeyboardClipboardMuteAndTextFocusStayInTheirEditors` | Neue IDs; Timing, Device, Paint und Längen bleiben erhalten |
| B07 | Implementiert, geprüft | Dokumentzustand und ID-Auflösung statt alter Selection-Note-Snapshots | `scrambleUndoLengthUsesRestoredNotesAndSelection`; Transform-Copy-Ketten, leere Auswahl, Redo-Verzweigung | Exakte gemeldete Reihenfolge automatisiert abgedeckt |
| B08 | Funktioniert wie vorgesehen; Bedienproblem korrigiert, geprüft | `PianoRollTransformPanel.kt`: präzise Erklärung; Spread nutzt gemeinsamen aktuellen Transform-Pfad | `gradientSpreadHasVisibleMidpointAndKeepsTiming`; `gradientSpreadUsesCurrentNotesAndSupportsUndoAndCopy` | Drei zeitlich geordnete Notes, rote/blaue Endpunkte → violette Mitte; zwei Endpunkte allein ändern sichtbar nichts |
| B09 | Implementiert, geprüft | `LoopChainDevice.kt`: generationbasierte Scheduler-Cancellation; aktive Off-Signale beim Stop/Mute/Entfernen; getrennte Ursprünge/Layers; positiver Mindestabstand | `finiteLoopPairsPressesAndReleasesAfterReload`, `loopChokeEmitsOffAndCancelsPendingFixedRepeats`, `loopChokeClearsEachLayerAndOriginIndependently`; bestehende `LoopResilienceTest` | Vorhandene Fixed-/Hold-Semantik bleibt erhalten |
| F01 | Implementiert, geprüft | Option/Alt im Transform-Panel; gemeinsame `applySelectedTransform` mit Copy-Variante; auch Spread und Randomize | `allTransformsSupportIndependentCopiesAndUndoRedo`, Spread-Copy-Test | Shift XY, Speed, beide Scrambles, Note Length, Pinch, Rotationen und Spiegelungen geprüft |
| F02 | Implementiert, geprüft | Piano-Clipboard-Routing, kontextuelles Select All, Mute und Track-Anlage; Fokussperre für Popups/Modals | Keyboard-/Text-/Modal-Tests in `Release072WorkflowTest`; bestehende Shortcut-/Editor-Tests | Konkrete Tabelle unten; keine konfigurierbare Shortcut-Engine |
| F03 | Implementiert, geprüft | `ChainView`, `GroupEditorFoundation`, gemeinsames DnD, `TimelineClipMoveEngine`: Copy bei Drop, unabhängige IDs; Chain-Reorder wird einmal am Drop committed | `deviceCopyDropHasIndependentSubtreesAndOneUndoStep`, `clipCopyDropKeepsSourceMakesIndependentNotesAndRejectsIncompatibleTargets`, `existingChainEffectClipsUseTheSameCopyMoveMuteAndReloadRules`, Group-Copy/Workspace-Reload-Test; bestehende Multi-Drag-/Identity-Tests | Modifier wird auch während Pointer-Bewegung/Release aktualisiert; ungültige Kopie überschreibt nichts |
| F04 | Implementiert, geprüft | `TimelineLaneView`, `TimelineClipDragPreview`, Audio-/MIDI-/Chain-Effect-Clip: Clip mit Name und Waveform/Notes in Ziellane statt abstraktem Feld; Quelle bei Move verborgen | Bestehende `TimelineClipMoveEngineTest`, `TimelineClipGestureTest`; Copy-/Kompatibilitäts-/Undo-Tests einschließlich vorhandener Chain-Effect-Clips | Kein automatisches Track-Anlegen im Leerraum |
| F05 | Implementiert, geprüft | `EditorMuteCommands`, `Chain`, `GenericChainDevice`, `AudioExecutionPlan`, Clip-/Chain-Serialisierung und Menüs; `0` | `muteDevicesChainsAndClipsSurvivesSerializationAndUndo`, `zeroTogglesSelectedDevicesAsOneUndoStepAndChainCopiesSurviveProjectReload`, neuer Audio-Graph-Branch-Mute-Test | Mehrfachselektion als eine Undo-Aktion; Device-UI beobachtet Mute direkt |
| F06 | Implementiert, geprüft | `TimelineCommandSurface.addTrack`, `TimelineKeyHandler`, `TimelineViewModel`, `TimelineView` | `commandTrackShortcutsAreTypedUndoableAndIgnoreRepeatsAndTextFocus` | Cmd/Ctrl+T: Audio; Shift: bestehender MIDI/Lights-Typ; Auswahl und Scroll zur neuen Spur |
| F07 | Implementiert, geprüft | `ClipboardManager`: Einfügeindex vor der ersten Auswahl, stabile Reihenfolge; unabhängige Device-States | `devicePasteIsBeforeSelectionAndPreservesOrderAndUndo`; Workspace-/Identity-Tests | Ohne Auswahl: Anhängen, auch bei leerer Chain |
| F08 | Implementiert, geprüft | Vorhandenes Dial-Reset beibehalten; gültige Defaults statt willkürlicher Werte; `EffectDial` verwendet Parameter-Deskriptor; Slider mit nicht störendem Double-Click-Observer | `dialDoubleClickUsesItsExplicitDefault`; `sliderDoubleClickUsesConfiguredRangeEdgeDefaultAndDragStillWorks`; Parameter-/Audio-Tests | Ohne semantisch gültigen Default kein erfundener Reset; vorhandene Automation-Start/Finish-Callbacks werden benutzt |
| F09 | Implementiert, geprüft | Pinch-State aus Panel in `PianoRollWorkspaceMode` gehoben; Reset nach Apply entfernt | `emptySelectionDoesNotTransformAndPinchSettingsSurviveEdits`; Transform-/Undo-Ketten | Lebensdauer des aktuellen Editors, keine neue globale Einstellung |
| F10 | Implementiert, geprüft | Pinch verwendet das bestehende fokustrackende Input-Control; Parsing, Grenzen, Enter-Normalisierung | `pinchAcceptsCommaAndClampsWithoutAcceptingNonFiniteText`; Fokus-/Keyboard-Tests | Numerische Direktbearbeitung wurde ausschließlich für Pinch ergänzt |

## Diagnose der unklaren Bugs

**B03 — Choke:** Ein Choke mit einer Group/Multi/anderen Container-Chain als direktem Child hat bisher nur direkte Chokeable-Devices gestoppt. Ein darunterliegender Sample-/Keyframe-/Loop-Player konnte weiterlaufen. Minimalfall: Choke Target 29 → Group → Chokeable-Player; zweiter Choke auf Target 29 triggert. Der neue Test wiederholt das zwanzigmal und prüft, dass Target 28 unberührt bleibt. Zusätzlich wurden das lokal vorhandene Suima-Apollo-Projekt mit 588 Patterns und die Page-Werte für `3 → 1 → 3` tatsächlich durch den Runtime-Pfad geschickt; nach explizitem Cleanup kommen keine weiteren geplanten Ausgaben. Das behauptet keine automatische Page-Reset-Funktion und identifiziert ohne Felix' genaue Padfolge nicht jede denkbare Variante seiner ursprünglichen Meldung.

**B08 — Gradient Spread:** Der vorhandene Algorithmus verteilt die Farben der zeitlich ersten und letzten Note über die selektierten Notes. Bei zwei Notes bleiben diese Endpunkte unverändert; bei identischen Endfarben ist die Aktion ebenfalls unsichtbar. Gültiger Nachweis: drei Notes bei 50/350/700 ms, erster Endpunkt rot, letzter blau; die Mitte wird `(0.5, 0, 0.5)`. Beschriftung/Erklärung nennen diese Semantik. Undo und Copy werden geprüft; kein Gradient-Redesign.

**B09 — Loop:** Fixed Repeat und Hold Loop waren bereits implementiert. Der belegte Defekt war fehlendes Off beim Choke/Entfernen: Nach einem On konnten untergeordnete Gate-/LED-Ausgaben aktiv bleiben. Außerdem wurden Cancellation auf die vorhandenen synchronen Heaven-Generationen umgestellt und aktive Ausgaben nach Ursprung/Layer getrennt. Die Tests prüfen Fixed-Repeats nach Reload, Press/Release-Paare, Stop vor ausstehenden Wiederholungen, mehrere Layers und den bestehenden Zero-Gate-Hold-/Release-Fall. Keine neue Loop-Engine.

## Shortcuts

Auf macOS gilt Cmd/Option, auf den unterstützten anderen Plattformen Ctrl/Alt.

| Kürzel | Kontext | Wirkung | Neu/geändert |
|---|---|---|---|
| Cmd/Ctrl+C | Piano Roll | Aktuelle ausgewählte Notes kopieren | Explizites Editor-Routing und aktuelle Note-Auflösung |
| Cmd/Ctrl+V | Piano Roll | Notes am Edit-Cursor/relativen Playhead einfügen | Explizites Routing; kein Device-Paste-Fallback |
| Cmd/Ctrl+X | Piano Roll | Aktuelle Notes kopieren und löschen | Explizites Routing |
| Cmd/Ctrl+A | Piano Roll / Devices / Chains | Alle Notes bzw. Devices der aktuellen Chain / Group-Einträge auswählen | Aktuelle Auswahl; verschachtelte Chain statt pauschaler Root-Chain |
| Cmd/Ctrl+Z | Bearbeitungseditoren | Undo | Bestehend; Selection-/Dokumentzustand korrigiert |
| Cmd/Ctrl+Shift+Z, Ctrl+Y | Bearbeitungseditoren | Redo | Bestehend; State-Konsistenz geprüft |
| Delete / Backspace | Piano Roll / Timeline / Devices / Chains | Auswahl löschen | Bestehend; Fokus-/Modal-Guard geprüft |
| Cmd/Ctrl+D | Piano Roll / Timeline / Devices / Chains | Bestehende Duplicate-Aktion | Bestehend; Context- und Copy-Pfade geprüft |
| Cmd/Ctrl+T | Timeline | Audio-Track anlegen | Neu; Key-Repeats werden abgefangen |
| Cmd/Ctrl+Shift+T | Timeline | MIDI/Lights-Track anlegen | Neu; Key-Repeats werden abgefangen |
| `0` | Device-/Chain-Editor | Ausgewählte Devices oder ausgewählte Chain-Einträge muten/entmuten | Neu; eine Undo-Aktion für Mehrfachauswahl |
| `0` | Timeline | Ausgewählte Audio-/MIDI-/vorhandene Chain-Effect-Clips muten/entmuten | Neu |
| Cmd/Ctrl+V | Device-Editor | Vor dem ausgewählten Device einfügen | Geändert; ohne Auswahl am Ende |
| Option/Alt+Click | Piano-Roll-Transforms | Ergebnis als zusätzliche Notes erzeugen | Neu; einschließlich Pinch Apply, Spread, Randomize |
| Option/Alt+Drag | Device-/Chain-/Clip-Editor | Unabhängige Kopie am Drop-Ziel | Neu |
| Shift während Clip-Move | Timeline | Snapping vorübergehend aussetzen | Geändert, um die vorherige Alt-Belegung mit Drag Copy aufzulösen |
| Double Click | Knob/unterstützter Slider | Auf definierten Default zurücksetzen | Slider ergänzt, Dial-/Effect-Defaults korrigiert |
| Enter | Pinch-Zahleneingabe | Sichtbaren gültigen Zahlenwert normalisieren/bestätigen | Neu |

Textfelder und geöffnete Modals/Menüs schützen diese Editorbefehle über den gemeinsamen Fokus-/Lebensdauer-Guard; ihre normale Texteingabe bleibt beim Text-Control.

## Ausgeführte Prüfungen

Lokale Belege: [abschließendes Build-/Testprotokoll](build/reports/release-0.7.2/verification.log), [JUnit-Zusammenfassung](build/reports/release-0.7.2/test-summary.json), [vollständiger Testbericht](composeApp/build/reports/tests/desktopTest/index.html), [isolierter Model-Swap-Lauf](build/reports/release-0.7.2/model-swap-isolated.log).

| Prüfung | Ergebnis | Nachweis |
|---|---|---|
| Baseline `./gradlew :composeApp:desktopTest --console=plain` | PASS | 599 Tests, 0 Fehler vor Änderungen |
| Gezielte Piano-Roll-, Device-, Workflow-, Compose-Control- und Audio-Graph-Tests | PASS | Neue Regressionen plus bestehende Suites |
| `AMETHYST_ABLETON_FIXTURE='/Users/anthony/Downloads/Suima Project/Suima.als' ./gradlew :composeApp:desktopTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 --console=plain` | PASS | 630 Tests, 0 Fehler; beide Plattform-Kompilationen erfolgreich |
| Vorhandener Suima-Apollo-Conversion-Test | PASS | 588 Patterns, Gate-/Root-/Mute-Erhalt gegen reale Fixture |
| Neue Suima-Page-3-Runtime-Prüfung | PASS | Reale Fixture, schnelle Trigger, Page 3/1/3, Scheduler-Cleanup |
| Protobuf-/Workspace-Roundtrips | PASS | Konvertiertes Flip, Loop-Konfiguration, Device-/Chain-/Clip-Mute, duplizierte Unterbäume und vorhandene Chain-Effect-Clips |
| Compose-Pointertests | PASS | Select mit 60 Quellen, Slider-/Knob-Double-Click, Drag/Seek bei Zoom und Scroll |
| `git diff --check` | PASS | Keine Whitespace-Fehler |
| Zwischenlauf `LaunchpadModelSwapPlaybackTest.modelSwapPausesSustainedTimelineNotesAndResumeUsesTheReplacementPad` | Einmal FAIL, isolierte Wiederholung PASS | Timeout an `rendered.await(2, TimeUnit.SECONDS)` (Zeile 197); unveränderter bestehender Test, keine Korrektur durch bloßes Erhöhen des Timeouts |
| Physischer Launchpad-/Audio-Hardware-E2E-Test | NICHT AUSGEFÜHRT | Automatisierte Runtime-/Audio-Graph-Tests ersetzen keine hör-/sichtbare Geräteabnahme |

Der vorhandene Build meldet weiterhin Kotlin-Expect/Actual-/Gradle-/Native-Access-Warnungen. Sie verursachen keine fehlgeschlagenen Prüfungen. Bei der Plattform-Kompilation meldete der Kotlin-Beta-Compiler außerdem einen Fehler seiner inkrementellen Cache-Auswertung; sein automatischer vollständiger Fallback lief erfolgreich durch. Ein zwischenzeitlicher Suite-Fehler wurde auf einen von den neuen Tests nicht zurückgesetzten Workspace-Modus eingegrenzt; die Test-Isolation ist korrigiert und der vollständige Folgelauf ist grün.

## Bekannte offene Punkte und konkrete manuelle Abnahme

Keine reproduzierbaren Code-/Build-Blocker im 0.7.2-Scope. Der einzelne zwischenzeitliche Model-Swap-LED-Timeout bleibt als intermittierendes Test-/Timing-Risiko dokumentiert; seine genaue Ursache ist nicht belegt. Isolierte Wiederholung: `./gradlew :composeApp:desktopTest --tests '*LaunchpadModelSwapPlaybackTest' --console=plain`. Der vorhandene Test wurde nicht abgeschwächt. Falls er erneut auftritt, LED-Snapshot und Callback-Reihenfolge beim Modellwechsel gemeinsam erfassen.

Vor Veröffentlichung empfiehlt sich diese tatsächliche Hardware-/Interaktionsabnahme; sie wurde nicht als erledigt gezählt:

1. Suima laden, Page 3 mit Felix' ursprünglicher Padfolge spielen, Pads schnell retriggern und zwischen Pages wechseln. Prüfen, dass nur die vorgesehenen Choke-Gruppen unterbrochen werden. Die genaue ursprüngliche Padfolge ist nicht bekannt; der automatisierte Test verwendet alle Hauptpads und Page 3/1/3.
2. In Sampling eine konvertierte Flip-Chain spielen, Projekt speichern und erneut öffnen; gleiche Pad-Spiegelung und korrekte Releases hören. Loop Fixed 4 und Hold testen, dann Mute/Entfernen während eines gehaltenen Gate-Samples; keine hängende Voice.
3. In einem Projekt mit vielen ähnlich benannten Samples den Compressor öffnen, zur letzten Sidechain-Quelle scrollen und diese auswählen. Speichern/Öffnen sowie Scrollen über dem Popup prüfen.
4. Device, Group-Chain sowie Audio- und MIDI-Clip mit gedrückter und erst während des Drags gedrückter Option/Alt-Taste kopieren. Quelle und Kopie getrennt verändern; Undo/Redo und Reload. Clip vertikal in kompatible und inkompatible Lane ziehen; bei Wiedergabe und horizontalem Scroll prüfen.
5. Piano Roll: Cmd+A → Scramble → Cmd+Z → Note Length; anschließend Cmd+C/V und Option+Transform. Pinch `1,25` eingeben, zweimal Apply, Auswahl wechseln und Undo. In der Zahleneingabe `0`, Cmd+A/V/T und in einem offenen Modal testen; keine Editorobjekte dürfen verändert werden.
6. Mehrere Devices/Chains/Clips muten, bei verschachtelten Samples die Ausgabe prüfen, mit Undo/Redo und Reload vergleichen. Mehrere Knobs/Slider auf ihre tatsächlichen Defaults zurücksetzen und normale Single-Click-/Drag-Bedienung prüfen.

Windows/Linux sind über die gemeinsame Modifier-Abstraktion und synthetische Ctrl-/Alt-Tests abgedeckt; ein nativer UI-Lauf auf diesen Betriebssystemen wurde nicht durchgeführt. iOS wurde für den Simulator kompiliert, nicht als vollständige signierte App auf Hardware gestartet. Release-Packaging/Signing/Veröffentlichung gehört nicht zum ausgeführten Auftrag.

## Abgrenzung zu 0.8.0

Keine Änderungen an Hub, Library, Homepage oder Server. Keine neue Sampling-Flip-Palette, kein Launchpad-Sidechain-Quick-Select, keine automatische Track-Anlage im Leerraum, keine neue Shortcut-Engine, kein globales Zahleneditor-Feature, kein Pinch-Live-Preview oder Pinch-Redesign, kein automatischer Subpage-/Multisampling-Reset bei Page-Wechsel, keine Page-Namen/Overview, keine neuen Home-/Help-/Tutorial-/Studio-/Performance-/Learning-Konzepte und keine Fenster-Neustrukturierung. Der vorhandene Versionswert war bereits `0.7.2` und wurde nicht künstlich geändert.

## Release-Notes 0.7.2

- Lange Compressor-Sidechain-Listen lassen sich scrollen; gleichnamige Quellen werden zuverlässig ausgewählt.
- Konvertierte Flip-Devices arbeiten auch in Sampling-Chains mit MIDI-Press und Release.
- Choke stoppt verschachtelte Player; Loop räumt aktive Ausgaben und geplante Wiederholungen beim Stop sauber auf.
- Keyframe-Loop berücksichtigt die eingestellte Repeat-Anzahl, auch bei gehaltenem Pad.
- Timeline-Playhead lässt sich im Lineal ziehen; vertikaler Clip-Drag zeigt Clip-Inhalt in der Ziellane.
- Piano-Roll-Copy/Paste und Transforms nach Undo verwenden den aktuellen Note-Zustand. Option/Alt erzeugt Transform-Kopien.
- Option/Alt-Drag dupliziert Devices, Chains und Clips. Device-Paste fügt vor der aktuellen Auswahl ein.
- Devices, Chains und Clips lassen sich muten; `0` toggelt die Auswahl. Cmd/Ctrl+T und Cmd/Ctrl+Shift+T erzeugen Audio- bzw. Lights-Tracks.
- Knobs und unterstützte Slider setzen sich per Doppelklick auf ihre definierten Defaults zurück.
- Pinch behält seinen Wert nach Apply und lässt sich direkt numerisch bearbeiten. Gradient Spread erklärt die erforderliche Auswahl und seine Farblogik.

## Nachbesserung — Waveform beim Clip-Drag

- Vorschau und platzierter Clip verwenden dieselbe Waveform-Farbe aus dem Theme. Die dunkle Selection-Textfarbe wird nicht mehr für die Waveform benutzt.
- Drop-Gültigkeit verändert den Rahmen, nicht die Helligkeit der Waveform. Der tatsächliche Clip-Mute bleibt sichtbar.
- Die Drag-Vorschau bleibt auch beim Wechsel zwischen Ursprungs- und Ziellane bestehen und wird nach Clip-Identität stabil gehalten.
- Breitenbasierte Waveform-Berechnung wird bei reiner Zeitverschiebung wiederverwendet. Beim Nachladen eines anderen sichtbaren Ausschnitts bleibt die letzte Waveform bis zum fertigen Ergebnis stehen.
- Drei neue Regressionstests in `WaveformDragRenderingTest`: gerenderte Pixelfarben von platziertem Clip und gültiger/ungültiger Vorschau in beiden Themes; keine Neuberechnung beim Verschieben; kein leeres Zwischenbild beim Nachladen.
- Gesamtlauf nach dieser Korrektur: 630 Tests bestanden, Android und iOS-Simulator erfolgreich kompiliert. Zu diesem Zeitpunkt waren noch keine Commits oder Pushes ausgeführt.

## Lokale Commits

Nach ausdrücklicher Freigabe wurden die Änderungen in vier lokale Commits aufgeteilt:

1. `fix flip choke loop and keyframe repeats`
2. `fix piano roll editing and transforms`
3. `improve device and timeline editing`
4. `fix sidechain scrolling and parameter controls`

Die einzelnen Zwischenstände wurden in einem separaten temporären Checkout kompiliert und mit den zugehörigen Regressionstests geprüft. Die bestehenden iOS-Scheme- und Swift-Build-Dateien sind ausgeschlossen. Kein Push.
