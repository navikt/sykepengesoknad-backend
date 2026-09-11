package no.nav.helse.flex.domain.exception

/** Exception for å nacke en melding som ikke kan behandles enda fordi en annen tråd har låst brukeren.
 * For å hindre at aktivering og opprettelse av søknader skjer samtidig for samme person. Men at det ikke blir utsatt for lenge.
 */
class AdvisoryLockConflictException(
    msg: String,
) : RuntimeException(msg)
