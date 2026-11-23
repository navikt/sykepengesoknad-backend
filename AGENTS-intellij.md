# IntelliJ MCP-instruksjoner

Bruk IntelliJ MCP-verktøy (`com-jetbrains-intellij-*`) til kode- og filoperasjoner når tilsvarende verktøy finnes. Kjør formatering, tester og bygging via IntelliJ run configurations med `execute_run_configuration`. Ikke kjør `./gradlew` fra shell: Gradle-workerne får ikke åpne lokale sockets i sandkassen.

## Formatering, tester og bygg

Bruk disse run configurations:

| Oppgave | Run configuration |
|---|---|
| Formatering | `sykepengesoknad-backend [ktlintFormat]` |
| Tester | `sykepengesoknad-backend [test]` |
| Kompilering uten tester | En run configuration for Gradle-oppgaven `classes` |

`build` kjører også tester og kan ta flere minutter. Bruk `classes` for kompilering uten tester.

Hent tilgjengelige navn med `get_run_configurations`. Opprett manglende Gradle-konfigurasjoner som beskrevet under.

Testene ligger i `src/test/kotlin/`. For én testklasse eller test: kall `get_run_configurations` med `filePath` og kjør run point-et med `execute_run_configuration` (`filePath` + `line`).

Hele testpakken tar lang tid. Sett høy `timeout` (for eksempel 1800000 ms) og ikke start en ny testkjøring mens en annen kjører, siden de deler Testcontainers og Kafka.

## Opprette en Gradle-konfigurasjon

1. Finn run configurations med `get_run_configurations`. Gradle-oppgaver kan finnes selv om de ikke er listet som run configurations.
2. Mangler konfigurasjonen og MCP ikke kan opprette den, kopier en Gradle-konfigurasjon under `RunManager` i `.idea/workspace.xml`. Gi kopien et nytt navn, sett `taskNames` til ønsket oppgave og fjern uvedkommende `scriptParameters`. Behold eksisterende konfigurasjoner.
3. Åpne `workspace.xml` i IntelliJ og kontroller med `get_run_configurations` at konfigurasjonen er lastet inn. Kjør den med `execute_run_configuration`. Ikke commit lokale IDE-innstillinger.

## IntelliJ-verktøy

- Bruk IntelliJ MCP til å søke i kode, lese og redigere filer og inspisere kodeproblemer.
- Bruk `execute_run_configuration` bare med et eksisterende run configuration-navn fra prosjektet. Ikke anta at prosjektet har npm- eller Playwright-konfigurasjoner.
- Bruk IntelliJ-debuggeren når du skal undersøke runtime-feil. Sett et breakpoint før du starter debug-økten.
- Ikke bruk terminalverktøy til å omgå sikkerhetsbegrensninger i shell-miljøet.
