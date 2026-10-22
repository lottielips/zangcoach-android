# Zangcoach (Android)

De app zelf staat in `app/src/main/assets/index.html` (dezelfde code als in de browser).
De Kotlin-code in `app/src/main/java/nl/zangcoach/studio/` is de schil eromheen:

- `MainActivity.kt` – laadt de pagina (met het vaste adres `https://zangcoach.app/`, zodat bewaarde nummers blijven),
  microfoon, bestanden kiezen, terug-knop stuurt naar de achtergrond. Crasht de pagina of sluit Android ze af
  (`onRenderProcessGone`), dan wordt ze opnieuw opgebouwd i.p.v. dat de app crasht. `onPause` laat de pagina
  alles wegschrijven (`zcFlush`).
- `Bridge.kt` – `window.ZangcoachApp`: `saveAudio` en `share` (in één keer, oud), `fileOpen`/`fileWrite`/`fileClose`
  (in stukjes van 768 kB, voor grote back-ups), `playing`, `features`.
- `FileSink.kt` – schrijft die stukjes naar Download/Zangcoach of naar een tijdelijk bestand om te delen.
- `AudioGuard.kt` – audiofocus zolang er muziek speelt: bij een oproep/andere muziek of als de koptelefoon
  losgaat, roept hij `zcAudioInterrupt('focus'|'noisy')` in de pagina aan (die stopt de muziek).
- `PlaybackService.kt` – houdt de app wakker zolang er muziek speelt.
- `NativeMic.kt` – eigen microfoon (AudioRecord, ingebouwde mic, gewone modus): `micStart`, `micRead`, `micStop`.
  Zo zet Chromium een Bluetooth-koptelefoon niet in bellen-modus (SCO) tijdens het opnemen.

## Bouwen
Elke push naar `main` bouwt automatisch een ondertekende APK (GitHub Actions).
Download: tabblad **Releases** (of **Actions → laatste run → Artifacts**).

De ondertekensleutel staat **niet** in dit project maar in de geheimen van GitHub
(Settings → Secrets and variables → Actions): `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
De bouw controleert dat het certificaat hetzelfde is als dat van de oude app (SHA-256 CB:1B:46:38:…:36:8F),
anders kan de APK niet over de oude app geïnstalleerd worden.

versionCode moet altijd hoger zijn dan de vorige (oude app eindigde op 17).

## Opslag (in de pagina)
- Het nummer: `localStorage` (`zangcoach-studio`, + `-at` = tijd van de laatste wijziging) én een reservekopie in
  IndexedDB `zangcoach-veilig` (`cur`, 2 s na een wijziging en meteen bij naar de achtergrond gaan). Bij het opstarten
  wint de nieuwste (`restoreCheck`). Onleesbaar nummer: de ruwe tekst gaat naar `zangcoach-studio-redding`.
  Laat een nummer de app niet opstarten, dan begint ze leeg en staat het nummer in de redding.
- Zangopnames: IndexedDB `zangcoach-zang`. Samples: `zangcoach-samples`. Mix-opnames: `zangcoach-opnames` (hoogstens 20).
- Fouten: de laatste 30 in `localStorage` `zangcoach-fouten` (`zcErr`); ze gaan mee in de back-up.
  De planner beveiligt elk onderdeel apart (`guard`): een fout in één spoor legt de rest niet stil.

## Geluidsmotor (kort)
Bronnen → per spoor: poort (alleen tijdens afspelen) → klankkleur → rack (vervorming, bitcrush, tape-wobble,
helderheid, EQ, compressie) → effect → links/rechts → spoorvolume → master (+ galm en echo als send) →
master-EQ → filter/lo-fi → compressor → tape-stop → luidheid → begrenzer → **eindpoort** (`S.endG`) → uitgang.
Na stoppen of een losse klank: galm en echo klinken uit (`tailMs`, hangt af van ruimte en echo), dan gaat de
eindpoort dicht, worden galm/echo vervangen door verse (`flushTails`) en wordt de AudioContext geschorst
(`ctxSleep`). Elke klank of tik maakt hem wakker (`ctxWake`). De planner (`sched`) tikt via een Worker
(`startTimer`) en plant met het scherm uit 1,5 s vooruit; na een hapering slaat hij gemiste stappen over.
Zuinig: een bewerking op 0 zit niet in de keten (rack: `wire`, master-EQ: `eqWire`, lo-fi: `lofiPath`, verzadiging
van de klankkleur alleen bij Stoffig/Telefoon). De compressor per spoor blijft altijd in de keten (ook neutraal
bepaalt hij mee het vertrouwde volume en de timing). Gemeten: ~35% minder rekenwerk tijdens het spelen, zelfde klank.
Wisselt een deel tijdens het spelen, dan wordt eerst het geluid gepland en pas daarna het scherm bijgewerkt.

## Versies
- **3.11** – Stabieler en zuiniger: geluidsmotor ~35% minder rekenwerk (zelfde klank), reservekopie van je nummer in
  IndexedDB + redding bij een kapot nummer + veilig opstarten, foutenlog, planner per onderdeel beveiligd, mix-opnames
  blijven bewaard (met Verwijder), grote back-ups in stukjes naar Android, de app herstelt zich als de pagina crasht,
  muziek stopt bij een oproep of als de koptelefoon losgaat.
- **3.10** – Eigen akkoordprogressie: tegel ✎ Eigen akkoorden (`P.cprog`, tot 16 akkoorden relatief t.o.v. de grondtoon,
  schuift mee met de toonsoort), kiezen uit toonsoort- en geleende akkoorden, sus/verminderd/overmatig, extra kleur per akkoord,
  intypen als namen (Am F C G7, F#m7b5, Bdim7) of trappen (vi IV I V, bVII, iiø7).
- **3.9.1** – Melodieraster: gewoon tikken op een noot haalt ze weg (overal op de noot); de noot klinkt pas bij slepen.
- **3.9** – Opnemen met een Bluetooth-koptelefoon: de app neemt zelf op via `NativeMic` (ingebouwde microfoon,
  UNPROCESSED of VOICE_RECOGNITION, gewone modus) i.p.v. getUserMedia, dat Chromium naar SCO liet schakelen.
  De pagina haalt de stukjes op (`startNativeMic`, AudioWorklet `zc-native-in`, 0,1 s buffer die `ioLatency`
  meerekent). Valt terug op getUserMedia als het niet lukt. De bellen-modus-truc uit 3.0.1 is weg.
- **3.8** – Melodieraster om te tekenen (`setupMelDraw`, pointer events): tik = noot, tik+schuif = lengte trekken,
  slepen vanaf het begin = verplaatsen, slepen aan het einde = lengte, lang drukken = weg. Vakjes die bij het
  akkoord van dat moment passen, kleuren groen (`paintMel`), akkoordnamen bovenaan. Op een smal scherm één maat
  tegelijk (`melView`, volgt de maat die speelt). Klik na een aanraking wordt genegeerd (`S.mPtrT`).
- **3.7** – Fades per spoor (mixer → ▾ Fade): fade-tijd in tellen/maten (`P.mix[b].fd`, `fadeSecs`), Dempen fadet
  mee, knoppen Fade uit/in; curve met gelijk vermogen (`fadeBus`, setValueCurveAtTime). applyMix laat een lopende
  fade met rust (`S.fadeUntil`).
- **3.6.1** – Extra sporen kunnen ook een bas zijn (`P.mel[t].kind='bas'`: 8 rijen, volgt de akkoorden, basklanken
  eerst; `isBass`, `rowsOf`, `trName`). Tot 3 extra sporen (C, D, E).
- **3.6** – Lagen die tegelijk spelen: tot 3 beats (`P.dlay`/`P.dcur`, sporen `drums`, `drums2`, `drums3`), tot 3
  akkoordlagen (`P.clay`/`P.ccur`, sporen `chords`…`chords3`, zelfde akkoorden, eigen ritme/klank) en extra
  melodiesporen C en D (`P.mel.C/D`). De gekozen laag staat in P; de andere worden per stap ingewisseld
  (`withBeat`/`withChord`). Sporen worden pas gemaakt als je ze nodig hebt (`makeBus`/`ensureBus`); sporen van
  lagen die er niet (meer) zijn, zwijgen.
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
