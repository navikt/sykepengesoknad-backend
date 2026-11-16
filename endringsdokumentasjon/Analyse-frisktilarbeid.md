# Analyse av testene for frisk til arbeid

Testene har nå et tydeligere skille mellom forutsetninger, handling og forventet resultat. **De viktigste gjenværende forbedringene gjelder duplikatmeldinger, delvis feil og kontroll av hva som skal bevares.** Jeg ville prioritert disse foran en ny omfattende omstrukturering.

Jeg har gjennomgått testene under `no.nav.helse.flex.frisktilarbeid`, inkludert `sporsmal` og hjelpeoppsettene, og sammenholdt dem med produksjonslogikken. Dette er en kvalitativ analyse av scenarioene og forventningene, ikke en prosentvis dekningsmåling. Ingen filer ble endret som del av analysen.

## Det som fungerer godt nå

| Område | Vurdering |
|---|---|
| Periodegenerering | Eksakte forventede perioder, relevante 14-dagersgrenser, skuddår og årsskifte |
| Repository | Reell database dekker lagring, eldste først, begrensning og utelukkelse av andre behandlingsstatuser |
| Consumers | Tilstand etter mottak, feilpropagering og acknowledgment er skilt fra interne samarbeidskall |
| STOPP | Flere statuser, datogrenser, andre vedtak og gjentatte meldinger er dekket |
| START | Gjenoppretting, nye søknads-ID-er og blokkering etter innsendt nei-svar er dekket |
| Spørsmål | Rekkefølge, siste periode, innsending og flere muteringsforløp er dekket |
| Teststruktur | Scenarioene er selvstendige, og domenenavngitte hjelpemetoder skjuler mye teknisk oppsett |

De gjenværende verifikasjonene av acknowledgment og aktiveringsbestillinger er relevante eksterne effekter. Jeg ville beholdt dem. Forutsetningskontroller med `check` er også fortsatt riktige der de etablerer tilstanden før handlingen som testes.

## Mangler jeg ville prioritert

### 1. Samme vedtaksmelding mottatt flere ganger

Dette er det viktigste udekkede scenarioet.

Overlappstestene oppretter hovedsakelig to forskjellige meldinger og behandler det første vedtaket før det andre mottas. Det er ikke det samme som å motta **samme melding med samme UUID to ganger før behandling**.

Produksjonskoden oppretter en ny record og ny `vedtakUuid` ved hvert godkjente mottak (`FriskTilArbeidService.kt:37`). Overlappskontrollen tar også med andre ubehandlede vedtak. Ved statisk gjennomgang følger derfor dette forløpet av koden:

1. Samme melding lagres som to `NY`-vedtak.
2. Det første finner det andre som overlappende og blir `OVERLAPP`.
3. Det andre finner det første som overlappende og blir også `OVERLAPP`.
4. Ingen søknader opprettes.

Vi bør dekke gjentatt mottak både **før** og **etter** at første melding er behandlet. Testforventningen må bygge på en avklart kontrakt for duplikater, ikke automatisk gjøre dagens atferd til fasit.

### 2. Feil etter at behandlingen har begynt å skrive

Den nåværende HTTP 500-testen feiler før søknader lagres. Den er nyttig, men sier lite om delvis gjennomført behandling.

`opprettSoknader` lagrer og publiserer hver søknad før vedtaket til slutt markeres som behandlet. Følgende mangler:

- Feil under lagring eller publisering etter at én søknad er håndtert.
- Ny behandling etter feilen.
- Kontroll av duplikate eller manglende søknader og hendelser.
- Feil midt i en batch med flere vedtak.

Batch-behandlingen bruker sekvensiell `.map`. En feil avbryter dermed de resterende vedtakene. En test bør vise tilstanden til allerede behandlede, feilede og ennå ikke behandlede vedtak, og hva som skjer ved neste forsøk.

**Dette krever integrasjonsdekning for transaksjonsegenskapene.** Fakes kan vise domenets beslutninger, men ikke bevise rollback i PostgreSQL eller atomisitet mellom database og Kafka. `@Transactional` alene dokumenterer ikke den samlede kontrakten.

### 3. To av overlappstestene utfører ikke overlappskontrollen

Dette gjelder:

- `FriskTilArbeidServiceOverlapTest.kt:14`: to perioder som ikke overlapper.
- `FriskTilArbeidServiceOverlapTest.kt:23`: overlappende perioder for forskjellige personer.

Begge lagrer vedtak og forventer `NY`. Ingen kaller behandlingen der overlapp faktisk vurderes.

Testene er korrekte som lagringstester, men beskytter ikke reglene som plasseringen i overlappsklassen antyder. De ville fortsatt passert dersom behandlingen feilaktig avviste tilstøtende perioder eller blandet forskjellige personer.

Jeg ville latt begge vedtak bli behandlet og kontrollert vedtaksstatus, søknader og relevante hendelser. Arbeidssøkerforutsetningen må da etableres eksplisitt for begge personene.

### 4. Sletting etter innsendt nei-svar trenger flere bevaringstester

`FremtidigeSoknaderFjernesVedIkkeLengreFriskmeldtTest` dekker et godt positivt forløp: innsending sletter tre senere søknader og avslutter vedtaket.

Det mangler direkte dekning av flere filtre i `FjernFremtidigeFtaSoknaderService.kt:28–65`:

| Scenario | Forventning som bør dekkes |
|---|---|
| Ja-svar eller manglende svar | Ingen sletting eller avslutning |
| Søknaden er ikke `SENDT` | Ingen sletting eller avslutning |
| Annen søknadstype | Ingen sletting |
| Senere søknad med annet vedtaks-ID | Bevares |
| Senere innsendt søknad | Bevares |
| Søknad med samme eller tidligere `fom` | Bevares |
| Gjentatt behandling | Ingen nye slettehendelser, opprinnelig avslutningstidspunkt beholdes |

STOPP-testene dekker allerede flere tilsvarende regler. Det dekker likevel ikke automatisk denne tjenesten, som har egne filtre og en annen datogrense.

Manglende vedtakskobling eller manglende vedtaksrecord er egne kontraktspørsmål. Koden har stille returer og kan slette søknader uten å avslutte en record. Forventet håndtering bør avklares før vi låser den med tester.

### 5. Rebehandling bør kontrollere hele resultatet

I `FriskTilArbeidServiceProcessingTest.kt:92` kontrolleres hovedsakelig den nye søknaden som returneres etter utvidelse.

Testen bør også vise at:

- De opprinnelige søknadene fortsatt finnes med samme ID-er og innhold.
- Den samlede perioden har riktig dekning uten overlapp.
- Bare den nye søknaden publiseres.
- En ny behandling uten endringer ikke oppretter eller publiserer noe.

Begge utvidelsesscenarioene starter dessuten med hele 14-dagersperioder. **Utvidelse av en allerede forkortet siste periode mangler**, for eksempel fra 15 til 16 dager.

Dette er relevant fordi eksisterende søknader filtreres bort ved eksakt likhet på `fom` og `tom` (`FriskTilArbeidSoknadService.kt:101`). Når siste `tom` endres, er ikke perioden lenger lik den gamle. Vi må avklare hvordan den gamle søknaden skal håndteres.

### 6. START/STOPP-listenerens acknowledgment-kontrakt mangler

START/STOPP-testene går gjennom listeneren via Kafka-faken. Vellykket parsing og domenebehandling er dermed indirekte dekket.

Men faken sender inn en tom acknowledgment-funksjon (`StringStringKafkaProducerFake.kt:31`). Testene viser derfor ikke at listeneren:

- Acker etter vellykket behandling.
- Lar være å acke når domenebehandlingen feiler.
- Lar være å acke ved ugyldig payload.

Dette bør dekkes med små listener-tester. Gyldig JSON med manglende obligatoriske felter eller ukjent enum-verdi er også mer verdifullt enn flere varianter av syntaktisk ugyldig JSON.

## Andre scenarioer med verdi

Disse kommer etter punktene over:

| Område | Forslag |
|---|---|
| START | Innsendt ja-svar tillater gjenåpning, mens nei-svar i en ikke-innsendt søknad ikke blokkerer |
| START | Flere ikke-`BEHANDLET`-statuser avvises uten sideeffekter; parameterisering passer her |
| STOPP | En eksplisitt søknad av annen type bevares. Dagens felles søknadsbygger lager alltid FTA |
| Historiske identer | Overlapp og sletting finner riktige data også når personen har tidligere ident |
| Spørsmålsmutering | Svar på fjernede spørsmål håndteres riktig ved gjenoppretting, og svar på beholdte spørsmål bevares |
| Spørsmålsmutering | Begrensende dato midt i perioden og på siste dag |
| Spørsmålsoppsett | Et vedtak med bare én søknad får riktig oppsett for siste periode |
| Meldingsformat | Ett uavhengig JSON-eksempel tester kontrakten uten å serialiseres fra samme DTO som produksjonen leser |

For mutering kontrolleres i dag særlig tags og spørsmålstekst. Det viser at strukturen gjenopprettes, men ikke nødvendigvis at svarene er riktige etterpå.

## Struktur og lesbarhet

**Jeg ville ikke flyttet vesentlig mer logikk ut av alle testene.** De fleste har nå en passende lengde. Neste forbedring er å gjøre enkelte hjelpemetoders ansvar tydeligere.

### Skill mottak av START fra hele gjenopprettingsforløpet

`mottaOgProsesserStartMelding` i `StartMeldingTestOppsett.kt:153` gjør fire ting: mottar START, behandler vedtak, bestiller aktivering og venter på ferdig resultat.

Det er nyttig i tester av hele gjenopprettingsforløpet. I tester av START-regelen skjuler det derimot den umiddelbare overgangen til `NY` med tømt avslutningstidspunkt.

Jeg ville beholdt den samlede hjelpemetoden for forløpstestene, men brukt bare meldingsmottak i tester som undersøker START-regelen. Da blir handlingen mer presis.

### Kontroller nye hendelser, ikke hele historikken

`publisertFremtidigEventFor` i `StartMeldingTestOppsett.kt:160` søker i alle tidligere hendelser og bruker `shouldContainAll`.

Den viser at forventede ID-er finnes, men tillater ekstra og duplikate hendelser. I gjenopprettingstester bør vi ta et øyeblikksbilde før handlingen og kontrollere **akkurat de nye hendelsene** etterpå.

### Gjør asynkrone feil lettere å forstå

Ventingen kontrollerer nå både statuser, spørsmål og publiserte hendelser. Det er riktig.

Enkelte hjelpemetoder bruker likevel et samlet boolsk predikat. Ved timeout blir det vanskeligere å se hvilken betingelse som manglet. `untilAsserted` med konkrete forventninger vil gi bedre feilbeskrivelser.

`.all` over en tom liste bør heller ikke stå alene som en forventning. Jeg har ikke funnet grunnlag for å si at alle slike kall gir falske positive tester nå; flere er beskyttet av eksakte status- og periodekontroller rundt dem.

### Rydd navn der de beskriver feil ansvar

`StartMeldingUgyldigeMeldingerTest` inneholder også STOPP og tester domenemessig ugyldige situasjoner, ikke ugyldig JSON. Navnet bør gjenspeile det, eller STOPP-scenarioet bør flyttes.

`TestvedtakProdusering.kt` bør ligge utenfor `sporsmal`, siden hjelperne brukes på tvers av domenetestene. Jeg ville ellers beholdt hovedpakken og `sporsmal`, uten å innføre en omfattende katalogstruktur etter testnivå.

## Generelle testprinsipper vi bør følge videre

### Test det som skal bevares like tydelig som det som skal endres

Dette gjelder særlig sletting, avkorting og gjenoppretting. Eksakte overlevende ID-er og uendrede hendelser gir sterkere beskyttelse enn bare riktig antall slettinger.

### Test gjentakelser og feilplasseringer

I en Kafka-basert flyt er tidspunktet for gjentatt mottak og feil like relevant som selve payloaden. Et vellykket førstegangsforløp er ikke tilstrekkelig.

### La forventningene være uavhengige av implementasjonen

Eksplisitte perioder fungerer godt. Unngå å beregne forventede resultater med samme produksjonshjelper som testen undersøker. Ett uavhengig JSON-eksempel følger samme prinsipp.

### Bruk riktig testnivå for risikoen

Fakes passer for domeneregler. Reell database trengs for rollback og repository-egenskaper. Kafka-integrasjon trengs for transport og publiseringskontrakter. Vi trenger ikke gjøre alle scenariokombinasjoner til tunge integrasjonstester.

### Isoler hele testtilstanden

Lokal opprydding tømmer hovedsakelig søknader, vedtak og søknadshendelser. Generisk `deleteAll` tømmer bare den aktuelle fake-samlingen, og arbeidssøkerdispatcherens responskø er delt. Relaterte spørsmål/svar og ukonsumerte HTTP-responser bør inngå i et samlet, eksplisitt reset-oppsett, særlig for å hindre følgefeil etter en test som avbrytes tidlig.

### Vurder styrken i forventningene, ikke bare antall tester

Et nyttig spørsmål er: «Ville testen feilet dersom dette filteret ble fjernet, grensen ble endret fra `>` til `>=`, eller hendelsen ble publisert to ganger?» De to lagringsscenarioene i overlappsklassen illustrerer hvorfor dette spørsmålet er viktig.

## Kjente avgrensninger

Cron-testene har fortsatt en begrenset avhengighet til reell klokke. Full kontroll krever en separat produksjonsendring. Den tidligere avtalte utsettelsen av `periodeLengde <= 0` står også ved lag; det er et kjent kontraktspunkt, ikke et nytt funn i denne gjennomgangen.
