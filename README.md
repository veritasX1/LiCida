# LiCida

Zeichnen mit Blick auf den Bildschirm: Die Kamera zeigt dein Papier, darüber liegt deine Vorlage –
die Idee von *Camera Lucida* (iOS), neu gebaut für Android und streng nach Apples Human Interface Guidelines.

## Werte

- **Für immer gratis** – keine Käufe, keine Abos, keine Werbung. Niemand schuldet irgendwem etwas.
- **Keine Datensammlung, in keinster Weise** – LiCida hat nicht einmal eine Internet-Berechtigung.
- **Deine Bilder bleiben bei dir** – Vorlagen aus deinem eigenen Speicher (Fotoauswahl oder Dateien),
  kein Cloud-Backup, nichts verlässt das Gerät, außer du sicherst selbst etwas in deine Fotos.
- **Volle Macht dem Benutzer** – nur die Kamera wird erfragt, alles andere entscheidest du.
- **Transparent entwickelt** – jede Änderung mit Begründung nachvollziehbar.

## Lizenz

LiCida ist freie Software unter der **GNU General Public License, Version 3** (Datei `LICENSE`):
Jeder darf es für immer benutzen, untersuchen, verändern und weitergeben – und jede Weiterentwicklung
bleibt ebenso frei. Die Schrift **Inter** (Rasmus Andersson) ist unter der SIL Open Font License 1.1
eingebunden.

Copyright (C) 2026 Olaf Winkler

## Funktionen (Version 0.1b)

- **Einrichten:** Vorlage aus Fotos, Dateien oder Kamera; mit zwei Fingern verschieben, zoomen, drehen;
  Deckkraft, 90°-Drehen, Vorlage in Fotos sichern.
- **Kamera:** Rück-, Ultraweitwinkel-, Front- (mit Spiegelaufsatz) und USB-Kamera; Füllen oder ganzes Bild;
  Korrektur von Hand (Neigung, Höhe, Strecken, Spiegeln, Hilfsraster) und automatisch mit Zielbild.
- **Werkzeugkasten:** 17 stapelbare Filter mit Vorschau (Graustufen, Tontrennung, Lasurwerte, Comic, Kanten,
  Raster …), Verlauf, drei eigene Filterfolgen, Farbpalette mit Farbebenen, Farbeffekte.
- **Zeichnen:** Kamera und Vorlage gekoppelt (bis 30×), Scharfstellen, Belichtung sperren, Taschenlampe,
  geteilte Ansicht, Flimmern, Zeitraffer-Video, Bild der Zeichenfläche sichern oder teilen.
- **Sitzungen:** sichern und später weiterzeichnen – LiCida findet das Blatt per Bildabgleich wieder.
- **Einstellungen:** Hinweise, Kamerabild, Zeitraffer, Tasten für Bluetooth-Tastatur, Controller und
  Selfie-Auslöser, Projektor-Modus.
- **Hilfe:** Rundgang beim ersten Start, deutsche Anleitung in der App und als PDF.

„Beta“, weil LiCida bisher nur auf einem Gerät (moto g84) getestet ist. Die automatische Kamerakorrektur
wartet noch auf den Test mit gedrucktem Zielbild.

## Laden

- Homepage: https://lisoft.goip.de/licida/
- F-Droid-Paketquelle: `https://volkskamera.goip.de/fdroid/repo`
- APK: unter „Releases“ auf GitHub

## Bauen

    cd android && ./gradlew assembleRelease     # Tests: ./gradlew testDebugUnitTest
