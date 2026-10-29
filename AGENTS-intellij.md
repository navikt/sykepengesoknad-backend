# IntelliJ MCP-instruksjoner

Bruk IntelliJ MCP-verktøy (`com-jetbrains-intellij-*`) til kode- og filoperasjoner når tilsvarende verktøy finnes. Kjør formatering, tester og bygging via IntelliJ run configurations med `execute_run_configuration`. Ikke kjør `./gradlew` fra shell: Gradle-workerne får ikke åpne lokale sockets i sandkassen.

## Formatering, tester og bygg

Bruk disse run configurations:

| Oppgave | Run configuration |
|---|---|
| Formatering | `sykepengesoknad-backend [ktlintFormat]` |
| Tester | `sykepengesoknad-backend [test]` |
| Bygg | `sykepengesoknad-backend [build]` |

Hent tilgjengelige navn med `get_run_configurations`. Mangler en konfigurasjon, be brukeren opprette den i IntelliJ.

Testene ligger i `src/test/kotlin/`. For én testklasse eller test: kall `get_run_configurations` med `filePath` og kjør run point-et med `execute_run_configuration` (`filePath` + `line`).

Hele testpakken tar lang tid. Sett høy `timeout` (for eksempel 1800000 ms) og ikke start en ny testkjøring mens en annen kjører, siden de deler Testcontainers og Kafka.

## IntelliJ-verktøy

- Bruk IntelliJ MCP til å søke i kode, lese og redigere filer og inspisere kodeproblemer.
- Bruk `execute_run_configuration` bare med et eksisterende run configuration-navn fra prosjektet. Ikke anta at prosjektet har npm- eller Playwright-konfigurasjoner.
- Bruk IntelliJ-debuggeren når du skal undersøke runtime-feil. Sett et breakpoint før du starter debug-økten.
- Ikke bruk terminalverktøy til å omgå sikkerhetsbegrensninger i shell-miljøet.
