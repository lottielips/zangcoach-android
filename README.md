# Zangcoach (Android)

De app zelf staat in `app/src/main/assets/index.html` (dezelfde code als in de browser).
De Kotlin-code in `app/src/main/java/nl/zangcoach/studio/` is de schil eromheen:

- `MainActivity.kt` – laadt de pagina (met het vaste adres `https://zangcoach.app/`, zodat bewaarde nummers blijven),
  microfoon, bestanden kiezen, terug-knop stuurt naar de achtergrond.
- `Bridge.kt` – `window.ZangcoachApp`: `saveAudio` (naar Download/Zangcoach), `share`, `playing`, `features`.
- `PlaybackService.kt` – houdt de app wakker zolang er muziek speelt.

## Bouwen
Elke push naar `main` bouwt automatisch een ondertekende APK (GitHub Actions).
Download: tabblad **Releases** (of **Actions → laatste run → Artifacts**).

De ondertekensleutel staat **niet** in dit project maar in de geheimen van GitHub
(Settings → Secrets and variables → Actions): `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
De bouw controleert dat het certificaat hetzelfde is als dat van de oude app (SHA-256 CB:1B:46:38:…:36:8F),
anders kan de APK niet over de oude app geïnstalleerd worden.

versionCode moet altijd hoger zijn dan de vorige (oude app eindigde op 17).

## Versies
- **3.1** – Sampler (tabblad Beat): eigen geluiden uit een bestand, de microfoon, een zangopname of een mix-opname;
  in plakjes hakken (op de slagen of gelijk), omgekeerd, begin/einde. Plakjes spelen op een drumrij
  (`P.kit[rij]='u:<id>'`, plakje per vakje in `P.dslc`, standaard in `P.dsl`). Geluiden in IndexedDB
  `zangcoach-samples` (stores `meta` en `pcm`, één bibliotheek voor alle nummers), mee in de back-up (`samples`).
- **3.0.1** – Bluetooth: geen "bellen-modus" als de microfoon aangaat.
- **3.0** – Overstap naar Kotlin + GitHub Actions.
