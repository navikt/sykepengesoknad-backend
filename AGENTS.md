# AGENTS.md - `sykepengesoknad-backend`

Dette er en Spring Boot-backend for sykepengesøknader. Tjenesten tar imot sykmeldinger og hendelser, bygger og oppdaterer søknader, publiserer videre på Kafka og lagrer data i PostgreSQL.

## 1) Kommandoer

Kjør dette når du har gjort kodeendringer:

```sh
./gradlew ktlintFormat
./gradlew test
./gradlew build
```

## 2) Testing

- Prioriter tester som dekker endret domenelogikk.
- Bruk fakes når enhetstester holder.
- Bruk integrasjonstester når endringen krysser database, Kafka eller eksterne klienter.
- Fakes for eksterne avhengigheter ligger i `src/test/kotlin/.../testconfig/fakes/`.
- Testdata og byggere ligger i `src/test/kotlin/.../testdata/`.

## 3) Prosjektstruktur

Typiske hovedområder i repoet:

- `api/` - REST-kontrollere og DTO-er
- `sykmelding/` - mottak, lagring, lesing og splitting av sykmeldinger
- `sykmeldinghendelse/` - brukerhendelser, svar og statushåndtering
- `arbeidsforhold/` - arbeidsforhold og data fra Aareg
- `arbeidsgiverdetaljer/` - sammensatte arbeidsgiverdetaljer
- `narmesteleder/` - nærmeste leder-domene
- `gateways/` - integrasjoner mot eksterne systemer og Kafka
- `config/` - Spring-konfigurasjon
- `utils/` - felles hjelpere

Repoet bruker også testoppsett for fakes og integrasjonstester, samt Flyway-migreringer og Redis-konfigurasjon der det trengs.

## 4) Integrasjoner og infrastruktur

- Nais/GCP
- PostgreSQL med Flyway
- Redis for cache/sesjon der det er konfigurert
- Kafka for inn- og utgående hendelser
- Namespace: `flex`
- Typiske integrasjoner: PDL, Aareg, Ereg, syketilfelle og nærmeste leder-tjenester

## 5) Kodestil

- Følg eksisterende mønstre i koden.
- Skriv nye meldinger, kommentarer og brukerrettet tekst på norsk bokmål.
- Behold etablerte tekniske navn og domenenavn når de allerede finnes i koden.

## 6) Git-workflow

- Jobb på egen branch, aldri direkte på `main`.
- Hold commit-meldinger korte, beskrivende og uten punktum.
- Ingen conventional commit-prefix.

Standard flyt:

```sh
git checkout -b kort-beskrivende-navn
./gradlew ktlintFormat
./gradlew test
./gradlew build
git commit -m "Kort beskrivelse"
git push origin <branch>
gh pr create --fill
```

## 7) Grenser

- Ikke lekke eller logge sensitiv informasjon.
- Ikke hardkode hemmeligheter eller credentials.
- Ikke lever kode med røde format-, test- eller build-sjekker.

## 8) Verktøy

- Bruk tilgjengelige kodeverktøy for søk og redigering når de finnes.
- Bruk shell for git og Gradle-kommandoer.
- Preferer repoets eksisterende verktøy og mønstre fremfor å legge til nye.

## Når du trenger mer kontekst

- `README.md` - prosjektformål og dataflyt
- `build.gradle.kts` - avhengigheter, Java-versjon og testoppsett

## Hurtigsjekk før levering

- [ ] Endringen følger eksisterende mønster i berørte filer
- [ ] Tester er oppdatert der domenelogikk er endret
- [ ] format, build og test er grønn
