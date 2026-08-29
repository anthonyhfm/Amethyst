# ADR 0001: Hierarchische Audio-Graph- und Routing-Semantik

Status: Angenommen für Epic 0
Datum: 2026-08-29

## Kontext

Amethyst besitzt eine editierbare `AudioChain`, `AudioChainDevice`-Generatoren und einen Echtzeit-Renderer in Echo. Die aktuelle Runtime reduziert die editierbare Struktur jedoch auf eine flache Liste. Aus verschachtelten Chains werden nur Generatoren übernommen. Dadurch ist nicht eindeutig ausdrückbar, ob ein Effekt einen einzelnen Sample-Zweig oder den gesamten Mix bearbeitet. Parallele Gruppen, Effect Tails, Latenzkompensation und Sidechain-Taps benötigen dieselbe eindeutige Semantik.

Die Entscheidung gilt für alle von Amethyst unterstützten Launchpad-Modelle und Firmwares. Sie liegt oberhalb der modellabhängigen MIDI-/SysEx-Schicht: Hardware-Events werden vor dem Audio-Graph in gemeinsame Pad-Trigger übersetzt. Diese ADR definiert keine gerätespezifischen MIDI-Bytes, Ports oder Layouts.

## Entscheidung

### Editierbarer Graph und Execution Plan

`AudioChain` bleibt die vom Nutzer bearbeitete Source of Truth. Außerhalb des Audio Callbacks wird daraus ein unveränderlicher `AudioExecutionPlan` kompiliert. Der Audio-Thread liest nur den zuletzt atomar veröffentlichten Plan.

- Topologie-, Reihenfolge- und Mute-Änderungen kompilieren einen neuen Plan auf dem Control Thread.
- Ein Render-Aufruf behält für den gesamten Block denselben Plan.
- Entfernte DSP-Instanzen werden erst freigegeben, wenn kein Audio-Block den alten Plan mehr verwenden kann.
- Alle Audiobusse und temporären DSP-Puffer werden beim Prepare oder beim Planbau vorallokiert.
- Der Audio Callback allokiert nicht, blockiert nicht und liest keine Compose-/Flow-Zustände.

### Serielle Chain

Jede Chain besitzt einen lokalen interleaved Stereo-Bus und wird von links nach rechts ausgewertet.

- Ein Source Device, beispielsweise `Sample`, addiert seinen Output auf den lokalen Bus.
- Ein Audio Effect verarbeitet den bis zu seiner Position vorhandenen lokalen Bus in-place.
- Ein Trigger Tool verändert oder routet Trigger-Events, verarbeitet aber keinen Audiobus.
- Ein Modulation Device schreibt zeitgestempelte Parameterwerte, verarbeitet aber keinen Audiobus.
- Ein nicht unterstütztes Device wird beim Kompilieren mit einer diagnostizierbaren Fehlermeldung abgelehnt und nicht still umgedeutet.

Damit bearbeitet `Sample -> Filter` den Output dieses Samples und aller davor im selben Bus liegenden Sources. Soll ein Effekt ausschließlich ein Sample bearbeiten, wird die Sequenz in einen eigenen Group-Zweig gelegt.

### Group

Jeder Group-Zweig besitzt einen eigenen vorallokierten Child-Bus.

1. Der eingehende Trigger wird nach den bestehenden Group-Regeln an die Child-Chains verteilt.
2. Jede Child-Chain rendert unabhängig in ihren Child-Bus.
3. Die Child-Busse werden in stabiler Gruppenreihenfolge auf den Parent-Bus summiert.
4. Audio Effects nach dem Group Device bearbeiten die Summe.

DSP-State wird niemals zwischen Group-Zweigen geteilt, auch wenn die Device-States identisch sind.

### Multi

`MultiGroupChainDevice` verwendet seine bestehende Forward-/Backward-/Random-Auswahl für Trigger-Routing. Nur der durch den Trigger ausgewählte Zweig startet neue Sources. Bereits klingende Voices und Effect Tails anderer Zweige werden nicht implizit beendet. Ein explizites Choke oder Reset darf sie beenden.

Der Preprocess-Chain verändert nur Trigger-/Modulationsdaten vor der Zweigauswahl. Sie besitzt in v1 keinen Audiobus.

### Mute, Bypass, Reset und Tails

- Ein gemutetes Source Device startet keine neuen Voices und rendert bestehende Voices nicht weiter.
- Ein gemuteter Audio Effect ist ein klickfrei geglätteter Bypass; sein Tail-State darf intern weiterlaufen.
- `ResetAudio` beendet Voices und leert alle zeitabhängigen DSP-States deterministisch.
- Ein Source-Ende beendet nicht automatisch nachgelagerte Delay-/Reverb-Tails.
- Ein Choke stoppt standardmäßig Sources. Das Löschen nachgelagerter Tails erfordert eine spätere explizite `Kill tail`-Policy.
- Ein Execution Plan bleibt aktiv, solange Voices oder `tailFrames` seiner Devices noch Output erzeugen können.

### Pegel und Summierung

- Alle internen Busse verwenden interleaved Float32 Stereo.
- Sources und Group-Zweige werden additiv summiert; es gibt kein implizites Auto-Gain.
- Nicht-finite DSP-Werte werden an der verursachenden Device-Grenze diagnostiziert und zu Stille normalisiert.
- Der bestehende Master Limiter bleibt die letzte Schutzstufe, ist aber kein Ersatz für stabilen DSP-Code.

### Parameter und Zeit

- Audio-Parameter werden über unveränderliche Snapshots beziehungsweise lock-freie atomare Werte an den Plan übergeben.
- Kontinuierliche Parameter deklarieren eine Smoothing-Policy.
- Trigger, Automation und Commands verwenden absolute Audio Frames als gemeinsame Laufzeitbasis.
- Beat-basierte Werte werden mit einem dokumentierten BPM-Snapshot in Frames aufgelöst.
- `latencyFrames` und `tailFrames` sind Eigenschaften der kompilierten DSP-Instanz und werden im Plan aggregiert.

### Sidechain

- Sidechain-Ziele adressieren persistente Device-IDs, keine Listenindizes oder UI-Pfade.
- V1 erlaubt trigger-basierte Taps: Ein Source-Trigger veröffentlicht ein kleines vorallokiertes Ereignis.
- Ein späterer Audio-Envelope-Tap veröffentlicht nur Detector-Werte; er kopiert keinen vollständigen Audiobus.
- Der Compiler weist Selbstreferenzen und Zyklen zurück.
- Ein fehlendes Ziel versetzt das empfangende Device in einen sichtbaren, sicheren Bypass-State.

## Identitätsregeln

Die bestehende `selectionUUID` eines `GenericChainDevice` ist zugleich seine persistente Device-ID. Sie wird durch `StateChain` gespeichert.

- Save/Load, Undo/Redo und Collaboration erhalten die ID.
- Copy/Paste, Duplicate und das Duplizieren einer Group erzeugen für das komplette kopierte Subtree neue IDs.
- Importierte alte Projekte ohne gespeicherte IDs erhalten beim Laden neue IDs und speichern sie beim nächsten Save.
- Leere oder innerhalb eines geladenen Graphen doppelte IDs werden repariert.

Macros erhalten ebenfalls stabile IDs. Macro-Werte bleiben wie bisher lokale Performance-Werte; die IDs sind synchronisierte Workspace-Struktur.

## Konsequenzen

### Positiv

- Per-Sample-Effects, Master-Effects und parallele Zweige sind eindeutig.
- Sidechain- und Macro-Ziele können Save/Load und Collaboration überleben.
- Der Audio Callback bleibt von der editierbaren Compose-Struktur entkoppelt.
- Bestehende Chain- und Group-UI bleibt das Nutzerkonzept.

### Kosten

- Der Plancompiler und die Lebensdauer alter Pläne benötigen explizites Ownership-Management.
- Group-Zweige benötigen zusätzliche vorallokierte Audiobuffer.
- Mute/Bypass und Tail-Lebensdauer müssen pro Device getestet werden.
- Clone- und Restore-Pfade dürfen nicht mehr dieselbe Identitätssemantik verwenden.

## Abgelehnte Alternativen

### Eine einzige flache Master-Device-Liste

Sie ist einfach, verliert aber die sichtbare Group-Struktur und kann per-Sample-Routing nicht zuverlässig darstellen.

### Ein eigener Mixer außerhalb der Chain UI

Das würde eine zweite Routing-Metapher, zusätzliche Navigation und doppelte Zustandsverwaltung einführen. Sends/Returns bleiben ein mögliches späteres Feature, sind aber keine Grundlage für v1.

### Audio-DSP direkt in Group-/Compose-Objekten traversieren

Das würde den Audio Callback an mutable UI-Zustände koppeln und sichere Topologieänderungen erschweren.

## Verifikation

Der spätere Plancompiler benötigt mindestens diese Tests:

- `Sample -> Effect` gegenüber `Group(Sample -> Effect) + Group(Sample)`.
- Zwei parallele Gruppen mit unabhängigem DSP-State.
- Multi-Zweigauswahl bei weiterlaufenden Tails.
- Plan-Swap während aktiver Voices.
- Mute/Bypass/Reset für Source und Effect.
- Sidechain-Ziel fehlt, wird gelöscht oder würde einen Zyklus bilden.
- Allocation-/Lock-Audit im Audio Callback.
