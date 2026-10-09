# EnigmaPlan

**Schnell mal das Programmheft durchblättern – tap, tap, tap – fertig.**
Oder in Ruhe alle Timer und Aufnahmen verwalten.

EnigmaPlan ist eine schlanke Android-App für deinen **Enigma2-Receiver** (Vu+, GigaBlue, Zgemma, Dreambox & Co.). Du scrollst durch das Wochenprogramm eines Senders und tippst auf den Kreis neben einer Sendung, dann ist die Aufnahme geplant. Mit einem Wisch geht es zum nächsten Sender.

Die App macht bewusst nur das: kein Streaming und keine Fernbedienung, dafür schnell und übersichtlich, am Handy hochkant genauso wie auf dem Tablet.

## Funktionen

- **Programm:** Senderliste aus deinen Bouquets mit laufender und nächster Sendung
- **Senderprogramm über die ganze Woche:** Tagesleiste (Jetzt · 20:15 · Heute · Morgen · …), Wischen wechselt den Sender
- **Aufnehmen mit einem Tipp:** Aufnahme-Knopf direkt in jeder Zeile, Sendungen mit Timer sind rot markiert
- **Senderlogos** (Picons von der Box)
- **Herunterziehen** zum Aktualisieren
- **Suche** im EPG der Box
- **Serien-Timer:** Mo–Fr, wöchentlich, täglich oder eigene Wochentage, direkt aus der Sendung („Als Serie aufnehmen“)
- **Timer:** bearbeiten (Titel, Datum, Zeiten), aktivieren/deaktivieren, löschen, von Hand anlegen, Erledigte aufräumen
- **Aufnahmen:** Liste mit Größe und Beschreibung, löschen, Anzeige des freien Festplattenplatzes
- **Handy und Tablet:** hochkant eine Spalte; auf dem Tablet oder quer stehen die Sender neben dem Programm, Timer und Aufnahmen mehrspaltig
- Material You (Farben passend zum Handy), Dunkelmodus

## Kompatibilität

Funktioniert mit jedem **Enigma2-Receiver mit [OpenWebif](https://github.com/E2OpenPlugins/e2openplugin-OpenWebif)**, zum Beispiel Vu+, GigaBlue, Zgemma, Octagon, Edision, Xtrend, Formuler oder Dreambox (mit OpenWebif), mit Images wie VTi, OpenATV, OpenPLi, OpenViX oder OpenBH.

Getestet mit: Vu+ Duo2, VTi 15.0.04, OpenWebif 1.2.8.

Voraussetzung: Android 8.0 oder neuer, Handy und Box im selben Netz.

## Einrichtung

1. Installiere die App.
2. Trage unter ⋮ → Einstellungen die IP-Adresse der Box ein, z. B. `192.168.178.50`. Sie steht in der Box unter Menü → Einstellungen → System → Netzwerk oder in der Geräteliste deines Routers.
3. Benutzer und Passwort brauchst du nur, wenn OpenWebif mit Passwortschutz läuft (meist Benutzer `root`).
4. Optional: Aufnahme-Puffer (Vor-/Nachlauf) einstellen – Standard 3 / 10 Minuten.

## Tipp: lückenhaftes EPG

Eine Box sammelt EPG-Daten nur von den Sendern, die sie gerade empfängt. Bei Kabel fehlen deshalb oft ganze Senderfamilien wie ARD oder ZDF. Abhilfe schafft das Box-Plugin **EPGRefresh**: Es schaltet im Standby nachts alle Favoriten durch, danach gibt es für alle Sender mehrere Tage Programm. Alternativ lädt **EPGImport** XMLTV-Daten aus dem Internet.

## Bauen

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Benötigt JDK 17+ und das Android-SDK (compileSdk 36). Die App ist mit Kotlin und Jetpack Compose gebaut und hat keine Abhängigkeiten außer AndroidX. Sie spricht die OpenWebif-JSON-API (`/api/…`) direkt an.

## Lizenz

GPL-3.0, siehe [LICENSE](LICENSE). Senderlogos und Sendernamen gehören den jeweiligen Sendern; die App zeigt nur die Picons, die auf deiner Box liegen.

## Autor

© 2026 Thomas Bugge · [thomas@bgg-mail.de](mailto:thomas@bgg-mail.de) · [github.com/thobgg](https://github.com/thobgg)
