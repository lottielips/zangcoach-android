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

## Geluidsmotor (kort)
Bronnen → per spoor: poort (alleen tijdens afspelen) → klankkleur → rack (vervorming, bitcrush, tape-wobble,
helderheid, EQ, compressie) → effect → links/rechts → spoorvolume → master (+ galm en echo als send) →
master-EQ → filter/lo-fi → compressor → tape-stop → luidheid → begrenzer → **eindpoort** (`S.endG`) → uitgang.
Na stoppen of een losse klank: galm en echo klinken uit (`tailMs`, hangt af van ruimte en echo), dan gaat de
eindpoort dicht, worden galm/echo vervangen door verse (`flushTails`) en wordt de AudioContext geschorst
(`ctxSleep`). Elke klank of tik maakt hem wakker (`ctxWake`). De planner (`sched`) tikt via een Worker
(`startTimer`) en plant met het scherm uit 1,5 s vooruit; na een hapering slaat hij gemiste stappen over.

## Versies
- **3.5** – Slimme EQ (Mix & opname): meet 6 s het spectrum in 29 tertsbanden (`seqMeasure`, vóór de EQ), vergelijkt met
  een doelcurve (`refCurve` = gemiddelde van commerciële mixen + stijlkleur, 10 doelklanken in `SEQ_T`), berekent de
  gewenste correctie (gelijke luidheid, lege banden niet oppompen, grenzen per gebied: `seqWanted`) en fit een EQ van
  10 banden (`AEQ`, via getFrequencyResponse: `seqFit`). Geldt voor het hele nummer (`P.master.aeq`), sterkte instelbaar,
  grafiek met mix/doel/EQ/resultaat. 7 extra vaste voorinstellingen.
- **3.4** – Songmodus (Mix & opname): delen in een volgorde (`P.song`, `P.songLoop`), deel na deel afspelen met fill
  vóór een wissel (`songStep` in de planner), stopt vanzelf aan het einde (`S.songEnd`), en het hele nummer in één keer
  opnemen. Fout uit 3.3 opgelost: nieuw deel maken/wisselen liep vast bij oudere nummers (`clone(undefined)`; standaard
  voor `ext` en `twin`).
- **3.3** – Per vakje toonhoogte/lengte en glijdende roffels (`P.dstn`, `P.dsln`, `P.drgl`), variatie per drumrij (`P.dvar`).
  Akkoordkleur 7/9/11-13 met jazzvoicings (`P.ext`), 808 glijdt bij Legato. Zangeffecten: andere stem (formanten via PSOLA),
  oefenversterker, gitaarversterker, omgekeerde echo, beweging links-rechts. Twee verweven gitaren (`P.twin`).
  Tekst met cut-up (`P.lyrics`) en flow-raster per deel (`P.flow`). Bouw op ubuntu-24.04, checkout/setup-java v5.
- **3.2** – Geen ruis meer na het afspelen: eindpoort + uitklinken + afsluiten, vinylkraak alleen tijdens het
  afspelen, galm/echo leeg na het afsluiten. Planner met eigen klok (Worker), verder vooruit met het scherm uit,
  inhaalregel na een hapering. `play()` kan niet dubbel starten; een snelle start die nog laadt, begint niet
  meer als je intussen stopte. Niveauregeling rust als de motor stilligt.
- **3.1.2** – Geen ruis bij het openen: samples laden zonder de geluidsmotor te starten (AudioBuffer pas bij afspelen, `smpBuf`);
  stilleggen na 6 s zonder klank.
- **3.1.1** – Geen ruis meer na de sampler: de microfoon gaat na een sampler-opname weer uit; de geluidsmotor
  gaat na 10 s zonder klank stil (`ctxSleep`, wakker bij de volgende tik); op de achtergrond zonder muziek: microfoon los en stil.
- **3.1** – Sampler (tabblad Beat): eigen geluiden uit een bestand, de microfoon, een zangopname of een mix-opname;
  in plakjes hakken (op de slagen of gelijk), omgekeerd, begin/einde. Plakjes spelen op een drumrij
  (`P.kit[rij]='u:<id>'`, plakje per vakje in `P.dslc`, standaard in `P.dsl`). Geluiden in IndexedDB
  `zangcoach-samples` (stores `meta` en `pcm`, één bibliotheek voor alle nummers), mee in de back-up (`samples`).
- **3.0.1** – Bluetooth: geen "bellen-modus" als de microfoon aangaat.
- **3.0** – Overstap naar Kotlin + GitHub Actions.
