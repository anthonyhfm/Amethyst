# Sampling Release Gates

Status: verbindliche Freigabekriterien für Desktop; Mobile bleibt gesperrt.

Diese Gates konkretisieren Epic 8 aus [`roadmap.md`](roadmap.md). Ein grüner Build allein ist keine Produktfreigabe: Automatisierte Prüfungen und die manuelle Plattformmatrix müssen für einen Release-Kandidaten vollständig erfüllt sein.

## Unterstütztes Desktop-Profil

- Betriebssysteme: die jeweils noch unterstützten Windows-, macOS- und Linux-Versionen aus der Release-Matrix.
- Mindesthardware: x86_64 oder arm64 CPU mit vier physischen oder Performance-Kernen, 8 GB RAM und ein Audio-Interface beziehungsweise Systemtreiber mit stabilen 48 kHz bei 256 Frames Buffergröße.
- Referenzlast: 48 kHz, Stereo, 256 Frames, 16 gleichzeitige Voices pro Sample Device und höchstens acht aktive Audio Effects pro serieller Sampling Chain.
- Sichere Defaults: 16 Voices pro Sample Device; ein neuer Effekt startet mit konservativem Dry/Wet beziehungsweise neutralem Pegel. Mehr als acht Effekte sind erlaubt, gelten aber als projekt- und hardwareabhängig und müssen über die Diagnostics-Ansicht beobachtet werden.
- Tempo: Warp und Repitch sind für Ratios von 0.25x bis 4.0x freigegeben; Werte außerhalb werden sicher auf diesen Bereich begrenzt.
- Freigabeschwelle: keine nicht-endlichen Samples, keine reproduzierbaren Render Overruns im Referenzprojekt und Peak DSP Load unter 80 Prozent während eines zehnminütigen Laufs. Voice- oder Queue-Drops müssen bei der Referenzlast null bleiben.

Die acht Effects sind durch den Stress-Test als vier Delay-/Reverb-Paare abgedeckt. Die Release-Pipeline führt die Desktop-Tests vor der Paketierung auf Windows, macOS und Linux aus.

## Automatisierte Gates

- DSP: Impulsantwort, Frequenzgang, Tail, Stereo-Routing, Extremwerte und finite Ausgabe.
- Trigger: Down/Up, Retrigger, Choke, getrennte Origins und Autoplay-Origin.
- Persistenz: Roundtrips aktueller States, Legacy-Fixtures, stabile Device-/Macro-/Mapping-IDs und fehlende Ziele als sichere No-ops.
- Editing und Collaboration: Mapping-Änderungen durchlaufen Undo/Redo; Remote-Sync erzeugt keine lokale Undo-Historie.
- Offline Render: deterministische PCM-Ausgabe mit Toleranz sowie RMS- und Peak-Grenzen.
- Stress: schneller Retrigger, begrenzte Voices/Queues, acht Time Effects und BPM-Wechsel.
- Themes: WCAG-Kontrast der im Sampling UI verwendeten Text-/Flächentokens in Light und Dark.
- Plattformen: `desktopTest` muss auf Windows, macOS und Linux vor dem Packaging erfolgreich sein; der iOS-Simulator muss weiterhin kompilieren.

## Manuelle Desktop-Abnahme pro Release-Kandidat

Die folgende Matrix ist auf Windows, macOS und Linux jeweils mit Maus und Tastatur auszuführen. Auf macOS zusätzlich VoiceOver, auf Windows Narrator und auf Linux der verfügbare AT-SPI-Screenreader.

- Mit Tab/Shift-Tab in visueller Reihenfolge durch Device, Tabs, Selects und Dials navigieren; kein unsichtbarer Fokus und keine Fokusfalle.
- Dials mit Pfeil hoch/runter verändern und per Screenreader mit Name und aktuellem Wert prüfen.
- Selects ausschließlich per Tastatur öffnen, wählen und schließen; interaktive Ziele bleiben mindestens 44 dp hoch.
- Light und Dark Theme prüfen: Text, Fokus, Disabled State, Waveform und Diagnostics bleiben lesbar; Information wird nicht ausschließlich über Farbe vermittelt.
- Reduced Motion aktivieren: Sampling-Interaktionen enthalten keine räumliche Bewegung; Select-Transitions werden ohne Animation angezeigt.
- Referenzprojekt zehn Minuten abspielen, BPM zwischen 60, 90, 120 und 180 wechseln und Diagnostics protokollieren.
- Audioausgang, Kanalzuordnung und vergleichbaren Pegel mit demselben Referenzprojekt auf allen drei Plattformen bestätigen.

## Mobile Gate

Sampling bleibt in `SamplingChainWorkspaceMode` auf iOS und Android mit „Currently not available on mobile“ gesperrt. Die Sperre darf erst entfernt werden, wenn jede Zielplattform separat folgende Nachweise besitzt:

- Audio-I/O: Start/Stop, Route Change, Bluetooth/USB, Sample Rate, Buffergröße, Unterbrechung und Recovery.
- Touch Layout: alle Controls ohne Hover erreichbar, mindestens 44 dp groß, bei unterstützten Gerätegrößen ohne abgeschnittene Kernfunktion.
- Lifecycle: Background/Foreground, Audio Session/Focus, Telefon-/Systemunterbrechung, Speicherwarnung und Projektwechsel ohne hängende Voices oder Jobs.
- Performance: derselbe zehnminütige Referenzlauf erfüllt die Plattformgrenzen; mobile Grenzwerte werden erst nach Messung festgelegt und nicht vom Desktop übernommen.
- Accessibility: Screenreader, Focus Order, Dynamic Type/Schriftvergrößerung, Kontrast und Reduced Motion sind auf realer Hardware geprüft.

Bis alle fünf Punkte pro Plattform belegt sind, ist weder die Mobile UI noch Audio-I/O als freigegeben zu kennzeichnen.
