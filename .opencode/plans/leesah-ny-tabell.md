# Leesah: bygg ny forskutteringsprojeksjon før leseskifte

## Status i denne checkouten (9. november 2026)

- Implementert: `V61` oppretter tom
  `forskuttering_esyfo` og et varig checkpoint per topic/partisjon. Ny Kafka-bane
  parser bare nødvendige domenefelter, skriver bare ny tabell og låser checkpoint
  i samme DB-transaksjon. Tombstone sletter raden; ack skjer etter commit.
  Gammel bane bruker igjen `findByNarmesteLederId` og `save` som `origin/main`,
  uten timestamp-filter. Gammel tabell og nullable tombstones beholdes.
- Ny consumer bruker egen gruppe, `earliest` og én synkron listenertråd per pod.
  `FORSKUTTERING_ESYFO_CONSUMER_AKTIV` er `false` som standard. Begge lesere
  bruker én fasade med `FORSKUTTERING_LESEKILDE=GAMMEL` som standard. ESYFO-lesing
  med avslått consumer avvises ved oppstart. Ingen miljømanifest aktiverer dette.
- IntelliJ `sykepengesoknad-backend [ktlintFormat]` og
  `sykepengesoknad-backend [build]` fullførte med Java 25: 1163 beståtte tester,
  ingen testfeil. `sykepengesoknad-backend [test]` returnerte exit 0 uten
  ny output etter forrige build. Testcontainerne verifiserer Kafka-bootstrap fra historikk,
  egen gruppe, restart og checkpoint/ack, samt rollback ved DB-feil mellom
  domeneoppdatering og checkpoint. En egen test verifiserer at checkpoint-gaugen
  leser varig DB-offset etter restart, duplikat og rollback, og registrerer en
  ny partisjon ved vellykket behandling. DB-fri fake-profil bruker gammel lesekilde;
  valg av ESYFO uten repository avvises eksplisitt. Hovedagenten rettet lokale,
  ignorerte `.idea/misc.xml` og `.idea/gradle.xml` til registrert `corretto-25`
  for testkjøring. Disse skal ikke committes; byggfiler og avhengigheter er urørt.
- Før deploy må teamet gi read-ACL i dev/prod, verifisere topic/partisjoner,
  tilgjengelig kompaktert historikk, tombstone-retention og speilerens lag.
  Avklar ny, ubrukt gruppe mot tom projeksjon, dokumenter start- og fanget
  end-offset per partisjon, valider DB-state og driftsovervåking samt go/no-go
  for midlertidig eldre state på ny offset. Koordinert leseskifte og rollback
  er manuelle beslutninger. Fanget head/lag-barriere må overvåkes operativt;
  implementasjonen publiserer utfallstellere, valgt lesekilde og en PII-fri
  gauge per partisjon som leser varig DB-checkpoint. Kafka-lag, fanget head
  og speilerstatus krever fortsatt operative kontroller; ingen automatisk
  readiness eller go/no-go er lagt til.

## Mål og rammer

- Denne planen gjelder bare `sykepengesoknad-backend`. Opprett en egen TOM-tabell
  `forskuttering_esyfo` fra `team-esyfo.syfo-narmesteleder-leesah`.
  Behold `forskuttering` som produksjonskilde inntil et eksplisitt leseskifte.
- Gammelt topic `teamsykmelding.syfo-narmesteleder-leesah` skriver bare gammel tabell.
  Nytt topic skriver bare ny tabell. Ikke kopier mellom tabeller, dual-write,
  les med fallback eller bruk UNION.
- Ny consumer bygger projeksjonen uten forretningssideeffekter: ingen søknadsendring,
  publisering eller aktivering. Ingen backend-avhengighet til de to andre migrerende
  repoene; de kan planlegges og deployes separat.
- Frontend er allerede gjennomgått uten blokkerende funn og kan merges først.
  Dagens flyt er direkte eSyfo-API, DELETE → `NlAvbrutt` → gammelt Leesah-topic
  → speiling til nytt topic. Denne backend-planen endrer ikke flyten.
- Kontrakten er [esyfo-narmesteleder sin Leesah-kontrakt](https://github.com/navikt/esyfo-narmesteleder/blob/main/docs/kafka/syfo-narmesteleder-leesah.md).
  Siste record per key er gjeldende state; det betyr ikke at all historikk er beholdt.

## Utgangspunkt ved planlegging

- Lokal branch `split/narmesteleder-migrering` er på
  `08707aacd23590159dc6971b09db3249cee92ed6` (kode-only, oppå nyere `main`).
  Ved undersøkelsen var bare `.codegraph/` og `.cplt.toml` untracked;
  de skal forbli urørt.
- Oppgitt status uten operasjon mot remote: funksjons-PR #1659 på remote branch
  `narmesteleder-migrering` har commit `e8a87ba3` med AGENTS-endring;
  docs-PR #1660 skiller den ut. cplt blokkerte force-push.
  Avklar PR-opprydding separat, ikke bruk force-push i dette arbeidet.
- Diffen mot `main` legger til `lagreHvisNyere` med `ON CONFLICT` og strengt
  `forskuttering.timestamp < EXCLUDED.timestamp`, og kobler begge topics til
  samme `behandle` i `NarmestelederListener.kt`. Tombstones logges og kvitteres
  uten sletting. Nye tester for delt tabell og tidsstempel tester feil målarkitektur.
- `V7__forskuttering_tabell.sql` opprettet `forskuttering` med unik
  `narmeste_leder_id`, `bruker_fnr`-indeks, nullable `arbeidsgiver_forskutterer`
  og `aktiv_tom`. `Forskuttering.kt` mapper tabellen.
- `ForskutteringRepository.kt` har `findByNarmesteLederId` og
  `finnForskuttering(brukerFnr, orgnummer)`, som sorterer
  `aktiv_tom DESC NULLS FIRST LIMIT 1`. Ingen SQL-join mot tabellen ble funnet
  i produksjonskoden.
- Faktiske produksjonslesere av `finnForskuttering` er
  `MottakerAvSoknadService.arbeidsgiverForskutterer` (valg av søknadsmottaker,
  ukjent/null teller som forskuttering) og
  `ProsesserJulesoknadkandidat.arbeidsgiverForskuttererIkke` (null/ukjent
  teller som *ikke* forskuttering).
- Sistnevnte nås fra `JulesoknadCronJob`, men `@Scheduled` er kommentert ut nå;
  behold leseren i skifteplanen også om jobben fortsatt er av.
- `AivenKafkaConfig.kt` bruker felles String-consumer-fabrikk med gruppe
  `syfosoknad-consumer`, `MAX_POLL_RECORDS=1`, manuell umiddelbar ack og
  `ENABLE_AUTO_COMMIT=false`. Begge lyttere har `idIsGroup=false` og
  `auto.offset.reset=earliest`; det overstyrer ikke committede gruppeoffsets.
- `application.yaml` har `aiven-kafka.auto-offset-reset: none` som grunninnstilling;
  `nais/app/dev.yaml` overstyrer med `latest`. `nais/app/prod.yaml` har 3–5
  replikaer, dev 1–2. `nais/app/naiserator.yaml` definerer Kafka-pool og
  miljøverdier. Velg eksplisitt omstart/deploy fremfor usynkronisert toggle.
- Siste migreringsfil som ble funnet er
  `V60__drop_column_inntektsopplysningerNyKvittering.sql`. Sjekk samtidige
  migreringer før implementering og reserver neste ledige Flyway-nummer da;
  endre aldri en allerede anvendt migrering.

## Endringer i denne PR-en ved implementering

1. Behold topic-konstantene, håndtering av nullable record-verdi, manuell ack etter vellykket behandling, toleranse for ukjente JSON-properties/status og relevante basis-/integrasjonstester. Gjenbruk domenefeltene som faktisk brukes, ikke kontaktfeltene i databasen.
2. Splitt dagens felles `behandle`: gammel listener skal fremdeles bare oppdatere `forskuttering`, ny listener skal bare oppdatere `forskuttering_esyfo` gjennom egen service/repository. Ikke legg inn nye sideeffekter i rebuild-løpet.
3. Fjern delt `lagreHvisNyere` som migrasjonsløsning og testene som krever timestamp-vinner på tvers av topics eller at tombstone ikke sletter ny rad. Ikke la `OppdateringAvForskuttering` for ny strøm bruke `forskuttering`-repository.
4. Behold gammel skrivebane stabil gjennom rollback-vinduet. Branchens opprinnelige `lagreHvisNyere` og strenge timestamp-filter er fjernet; gammel bane bruker samme `findByNarmesteLederId`/`save` som `origin/main`. Ikke fjern gammel consumer eller tabell i denne endringen.
5. Oppdater `OppdaterForskutteringTest.kt`, `ForskutteringRepositoryFake` i `OppsamlingAvRepositoryFakes.kt`, `MottakerAvSoknadServiceTest.kt` og relevante julesøknadstester når lesegrensesnittet skiftes. Behold øvrige kode-/formatendringer fra PR-en bare hvis de fortsatt trengs; rør ikke uvedkommende filer i migreringen.

## Skjema og isolert oppbygging

1. Lag en ny Flyway-migrering med `forskuttering_esyfo`: samme nødvendige domenekolonner som `forskuttering` (`narmeste_leder_id`, `bruker_fnr`, `orgnummer`, `aktiv_fom`, `aktiv_tom`, `arbeidsgiver_forskutterer`, `timestamp`, `oppdatert`), unik key og indeks for `bruker_fnr`/`orgnummer`-oppslaget. Behold også teknisk `id` og ID-generering dersom modellen fortsatt følger eksisterende Spring Data-mapping. Bevar null i `arbeidsgiver_forskutterer` og `aktiv_tom`; ikke tolke null som tombstone.
2. Lag en liten teknisk checkpoint-tabell med primærnøkkel `(topic, partition)` og `last_processed_offset`. Ingen persondata i checkpoint. Bind raden til nytt topic; ikke del checkpoint med gammel consumer.
3. Nytt repository skriver bare `forskuttering_esyfo` med upsert på `narmeste_leder_id` uten timestamp-betingelse. `DELETE` ved Kafka-verdi null bruker record-key. Bruk samme domeneoppslag og sortering som i gammel `finnForskuttering` på den nye tabellen.
4. Bekreft Kafka-key er gyldig UUID og stemmer med `narmesteLederId` i ikke-null verdi; uventede avvik skal feile kontrollert og ikke ackes før avklart. Ikke logg key, payload, ident, organisasjonsnummer eller kontaktdata.
5. Les bare `team-esyfo.syfo-narmesteleder-leesah` med en egen, navngitt bootstrap-consumergruppe. Konfigurer den konkret på ny listener/fabrikk, uten å endre gruppe-ID for andre Kafka-lyttere. Første start fra tidligste tilgjengelige offset, med tom ny tabell og tom checkpoint. `earliest` alene omgår ikke committede offsets.
6. Har gruppen allerede offsets, bruk kontrollert ny gruppenavn for en *ny, tom* projeksjon og avklar restart-strategi. Ikke blind-reset en aktiv gruppe eller replay inn i gammel, ikke-tom projeksjon. Dokumenter faktisk startoffset per partisjon.

## Rekkefølge, checkpoint og tombstones

- 🔴 Rød sone: behandlingsrekkefølge, atomisk checkpoint og sletting påvirker
  hvem som får søknaden. Utvikler må forklare transaksjonsgrensen og godkjenne
  testene før produksjonslesing.
- Per ny-topic-record: start DB-transaksjon, lås eller serialiser checkpoint-raden
  for `(topic, partition)` på tvers av pod-er, også ved første samtidige insert.
  Hvis offset `<= last_processed_offset`, gjør ingenting; ellers bruk key/verdi,
  og skriv checkpoint til denne offseten i samme transaksjon.
  Ack først når commit er bekreftet.
- Sikre første checkpoint-rad med unik nøkkel og konflikttrygg opprettelse/låsing.
  Radlåsen beskytter transaksjoner, men gjenoppretter ikke tapt behandlingsrekkefølge.
  Behold én sekvensiell behandlingsbane per tildelt partisjon, også ved rebalance;
  test at en pågående gammel behandling ikke kan overskrive ny committed tilstand.
  Ikke hopp over en tidligere upåført record fordi en senere record ble behandlet
  parallelt. Kompaktering gir lovlige offset-hull; krev ikke fortløpende heltall.
- Null Kafka-verdi sletter domenekey fra *ny* projeksjon og flytter checkpoint
  selv om ingen rad finnes. En duplikat/eldre offset etter restart eller rebalance
  kan dermed ikke gjenopprette slettet rad. En senere record på **ny** offset
  kan opprette raden igjen.
- Ved feil må DB-transaksjonen rulles tilbake og recorden ikke kvitteres;
  overvåk gjentatte feil uten payload/ident i logger. Dette er at-least-once
  med DB-idempotens, ikke en distribuert Kafka/DB-transaksjon.
- Produsentens retry kan gi eldre state på **senere** offset og midlertidig
  eldre leseresultat. Checkpoint løser ikke dette. Avklar eksplisitt go/no-go
  for live lesing dersom mottakerlogikken ikke tåler intervallet; ikke anta at
  `timestamp` er unik eller filtrer bort senere loggrecords med likt/eldre timestamp.

## Kontrakt og leseskifte

- Kontrakten oppgir 3 partisjoner, stabil partisjon per UUID-key, at-least-once,
  compact med ubegrenset retention og min/maks compaction lag ca. 31/35 dager.
  Tombstones kan utløpe. Bekreft faktisk `delete.retention.ms` med topic-eier og
  at bootstrap fra tilgjengelig start rekker head før tombstones forsvinner;
  stans skiftet hvis dekningen ikke kan bevises.
- Speilingen publiserer opprinnelig key/verdi uendret for validerte records; ugyldige records filtreres av eSyfo, og offseten committes. Ukjente properties og statusverdier må tåles; `orgnummer` er alltid 9 siffer på nye records. Ikke bygg kontrollen på feilaktig antakelse om alle historiske records eller like offsets på tvers av topics.
- Innfør eksplisitt lesekilde, for eksempel `FORSKUTTERING_LESEKILDE=GAMMEL|ESYFO`, med `GAMMEL` som default. Én delt lesefasade skal velge nøyaktig ett repository for `finnForskuttering` og bevare `ORDER BY aktiv_tom DESC NULLS FIRST LIMIT 1`, uten fallback eller UNION. Ingen direkte leser skal omgå fasaden.
- Flytt begge faktiske lesere: `MottakerAvSoknadService` og `ProsesserJulesoknadkandidat`. Test at ukjent/null fortsatt gir `true` for `MottakerAvSoknadService.arbeidsgiverForskutterer`, men `true` for `ProsesserJulesoknadkandidat.arbeidsgiverForskuttererIkke`; `false` og `true` må fortsatt ha samme domenebetydning i begge.
- Ikke slå på ny lesekilde mens gammel og ny binærversjon blander lesekilder på rullerende pod-er. Deploy først kode som kan lese begge med default gammel, bygg ny tabell og verifiser. Bytt deretter via eksplisitt konfigurasjon med koordinert restart/deploy; sjekk at alle pod-er har samme verdi før trafikk. Ved krav om absolutt ensartet svar under rullering: pause/diriger trafikk eller bruk styrt utrulling, og avklar driftsvindu.
- Hold begge consumers i drift gjennom rollback-vinduet. Sjekk også at gammelt topic fortsatt mottar nødvendige state-endringer; en consumer med null lag oppdaterer ikke gammel tabell hvis produsenten har sluttet å publisere dit. Ikke slett tabellen i leseskifte-PR-en.

## Testmatrise og verifikasjon

- Integrasjon: gammelt topic oppdaterer bare `forskuttering`; nytt topic oppdaterer bare `forskuttering_esyfo`; forskjellig verdi på samme key på de to topics gir fortsatt isolerte tabeller.
- Ny strøm: insert, oppdater, eksakt duplikat/eldre offset, likt og eldre `timestamp` på høyere offset, nullable `aktivTom`/`arbeidsgiverForskutterer`, ukjent property/status, 9-sifret `orgnummer` og key/verdi-samsvar.
- Livsløp: insert → tombstone → ny record, tombstone → replay av gammel offset → restart/rebalance → ny record; verifiser sletting, checkpoint og at den nye recorden godtas. Test også tombstone uten eksisterende rad.
- Transaksjon: tving feil mellom domeneskriving og checkpoint begge veier; DB-rollback, manglende ack og trygg retry. Konkurrerende første insert og samtidige pod-er på samme partisjon skal ikke omordne offsets. Ikke krev total rekkefølge på tvers av keys/partisjoner.
- Lesing: begge repository-spørringer velger samme rad ved flere relasjoner per fnr+orgnummer, ingen sammenslåing; lesekilde default gammel og eksplisitt ny; begge produksjonslesere og deres ulike nullkontrakter, også når julesøknadsscheduleren er av.
- Kontrakt: bruk representative syntetiske JSON-records og tombstones fra lenket schema. Test at bare validerte speilede records forventes, og at ukjente felter ikke bryter parsing. Oppdater testdata som nå bruker 6-sifret `orgnummer` når de skal representere nytt topic.
- Kjør ved implementering IntelliJ MCP `get_run_configurations` og eksisterende `sykepengesoknad-backend [ktlintFormat]`, `[test]`, `[build]` via `execute_run_configuration` i den rekkefølgen. Kjør aldri `./gradlew` i shell; denne planoppgaven kjører ingen scripts.

## Driftsporter, rollback og opprydding

1. Før bootstrap: be team-esyfo legge `sykepengesoknad-backend` til med read-ACL i topic-manifestene for **både dev og prod**; kontraktens tilgangstabell lister ikke appen. Bekreft topic, partisjoner, tombstone-retention og speilejobbens fremdrift i hvert miljø. ACL-endring eies av produsentens repo, ikke av denne planen.
2. Med gammel lesing: deploy skjema og ny consumer; hent start og fang end-offset per partisjon på *nytt* topic på et bestemt tidspunkt. End-offset er eksklusiv. Bekreft at consumer-posisjonen har passert alle tilgjengelige records før denne grensen, og at alle disse er ferdigbehandlet og DB-committet uten utestående behandling. DB-checkpoint viser siste faktisk behandlede record, ikke nødvendigvis `endOffset - 1`: tomme partisjoner og kompakterte hull må håndteres. Ikke skriv barriereverdien direkte til checkpoint. Kontroller lav/stabil lag til nytt head. Sjekk separat at speileren er ajour med gammelt topic og at ugyldige records ikke har skjult tap. Sammenlign aldri offsettall mellom gamle og nye topics.
3. Kontroller bootstrap-dekning mot produsentens forventede gjeldende state og slettinger; ubegrenset compact-retention garanterer ikke alle hendelser. Bruk aggregerte SQL-tellinger (total, antall aktive/inaktive, antall nullable forskutteringsverdier) og begrensede avvikstellinger, ikke personlister eller uttrekk av rå records.
4. Eksponer PII-frie metrikker: consumer-lag per partisjon, checkpoint vs. fanget head, behandlede oppdateringer/slettinger/duplikater/feil og valgt lesekilde per pod. Logg bare tekniske kategorier og partisjon/offset, ikke UUID-key eller payload. Gå ikke videre ved uavklart forskjell, manglende tombstones eller ustabil speiling.
5. Etter godkjent review av domene, database og drift: aktiver ny lesekilde i dev, verifiser mottaker/julesøknad og pod-samsvar; gjør tilsvarende go/no-go og koordinert deploy i prod. Behold gammel consumer oppe slik at gammel tabell er oppdatert ved rollback.
6. Rollback i vinduet: avklar produsentovertakelsen sammen med rollback-vinduet før skiftet. Etter eSyfos overtakelse kan gammelt topic bli stille og gammel tabell foreldet selv om gammel consumer kjører med null lag. Verifiser at nødvendige state-endringer fortsatt når gammelt topic før dere setter `FORSKUTTERING_LESEKILDE=GAMMEL` via koordinert deploy/restart; bekreft at alle pod-er leser gammel tabell. Ny consumer kan fortsette å bygge ny tabell. Ingen offset-reset eller kopiering. Avklar hvor lenge begge consumers beholdes, og observer begge strømmer i perioden.
7. Egen, senere irreversibel endring: først etter utløpt rollback-vindu og ny beslutning kan gammel consumer stoppes. Da blir gammel tabell foreldet; sletting av tabell, gammel repository/konfigurasjon og ACL-opprydding krever egen migrering/review og egen gjenopprettingsplan. Ikke bland dette med selve leseskiftet.
