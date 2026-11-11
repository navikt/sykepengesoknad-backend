# Testing av backend med Kotlin og Spring

## Rammeverk og biblioteker

- Bruk JUnit 5 som testrammeverk.
- Bruk Kotest assertions. Dette assertion-biblioteket er under utprøving; valget er ikke endelig.

## Retningslinjer

- Legg mest mulig av testingen på rene funksjoner.
- Test Spring-komponenter som integrasjoner med hele Spring-konteksten, ikke som isolerte komponenter.
- Ikke bruk mocks eller stubs. Dette gjelder også Mockito, `@MockitoBean` og `@MockitoSpyBean` i Spring-tester.
- Skriv testnavn på norsk bokmål i backticks, med stor forbokstav.
- Bruk `@Nested`-testklasser når testene trenger inndeling. I Kotlin skal disse være `inner`-klasser.
