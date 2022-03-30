# Sykepengesoknad-backend

## Om Sykepengesoknad-backend
Syfosoknad tilbyr et API mot frontenden for å hente og gjøre operasjoner på sykepengesøknader. Videre legger den søknader på kafka-topics ved spesielle hendelser. (som opprettet, sendt osv.) Disse kan bli plukket opp av andre apper bakover i løypa som f.eks sykepengesoknad-narmeste-leder-varsel. 

## Redis
Sykepengesoknad-backend bruker redis for cache. Denne deployes ved endringer i redis-config.yaml av en egen GHA workflow.

## Data
Sykepengesoknad-backend har en postgres database. Her lagres alle søknadene strukturert.
Søknadene inneholder, spørsmål, svar, status og identifikator på personen søknaden hører til.
Det er ikke noe sletting av søknader fra denne databasen, når søknader utløper etter 4 måneder endres kun statusen, data ligger der fortsatt.

Databasen lagrer også dødsmeldinger som kommer inn på personer som har en søknad.
Denne dødsmeldingen ligger lagret i 2 uker før den slettes samtidig som NYe søknader automatisk sendes inn.

Ved endring av status på søknad så publiserers også hele søknaden på Aiven kafka slik at andre apper kan lese dataene.
Dette kafkatopicet lagrer dataene i 6 måneder etter publisering.

# Komme i gang

Bygges med gradle. Standard spring boot oppsett.

---

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles til flex@nav.no

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #flex.
