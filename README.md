# EnigmaPlan

Schlanke Android-App, mit der du am Handy oder Tablet das **Fernsehprogramm deines Enigma2-Receivers** durchblätterst und **Aufnahmen planst, verwaltest und löschst**.

Sie macht bewusst nur das, nicht mehr: kein Streaming und keine Fernbedienung, dafür schnell und übersichtlich.

## Funktionen

- **Programm:** Senderliste aus deinen Bouquets mit laufender und nächster Sendung
- **Senderprogramm über die ganze Woche:** Tagesleiste (Jetzt · 20:15 · Heute · Morgen · …), Wischen wechselt den Sender
- **Aufnehmen mit einem Tipp:** Aufnahme-Knopf direkt in jeder Zeile, Sendungen mit Timer sind rot markiert
- **Suche** im EPG der Box
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
2. Trage unter ⚙ die IP-Adresse der Box ein, z. B. `192.168.178.94`.
3. Benutzer und Passwort trägst du nur ein, wenn OpenWebif mit Passwortschutz läuft.

## Tipp: lückenhaftes EPG

Eine Box sammelt EPG-Daten nur von den Sendern, die sie gerade empfängt. Bei Kabel fehlen deshalb oft ganze Senderfamilien wie ARD oder ZDF. Abhilfe schafft das Box-Plugin **EPGRefresh**: Es schaltet im Standby nachts alle Favoriten durch, danach gibt es für alle Sender mehrere Tage Programm. Alternativ lädt **EPGImport** XMLTV-Daten aus dem Internet.

## Bauen

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Benötigt JDK 17+ und das Android-SDK (compileSdk 36). Die App ist mit Kotlin und Jetpack Compose gebaut und hat keine Abhängigkeiten außer AndroidX. Sie spricht die OpenWebif-JSON-API (`/api/…`) direkt an.
