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

## Stand

Erste Version: Einrichten- und Zeichenmodus, Vorlage aus Fotos/Dateien/Kamera, mit zwei Fingern
verschieben, zoomen, drehen, 90°-Drehen, Deckkraft, Vorlage in Fotos sichern; im Zeichenmodus Kamera und
Vorlage gekoppelt (bis 30×, Doppeltipp 3×), Tippen blendet die Bedienung aus, Scharfstellen,
Belichtung per Zwei-Finger-Tipp sperren, Drehung gesperrt, Bildschirm bleibt an.

## Bauen

    cd android && ./gradlew assembleRelease     # Tests: ./gradlew testDebugUnitTest
