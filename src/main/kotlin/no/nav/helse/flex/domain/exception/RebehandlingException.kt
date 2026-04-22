package no.nav.helse.flex.domain.exception

import java.time.OffsetDateTime

abstract class SkalRebehandlesException(
    msg: String,
    val rebehandlingsTid: OffsetDateTime,
) : RuntimeException(msg)

class UventetArbeidssituasjonException(
    msg: String,
) : SkalRebehandlesException(msg, OffsetDateTime.now().plusMinutes(15))

class ManglerArbeidsgiverException(
    msg: String,
) : SkalRebehandlesException(msg, OffsetDateTime.now().plusMinutes(15))

class SykeforloepManglerSykemeldingException(
    msg: String,
) : SkalRebehandlesException(
        msg,
        OffsetDateTime.now().plusMinutes(15),
    )

class SlettSoknadException : SkalRebehandlesException("Feil ved sletting av søknad", OffsetDateTime.now().plusMinutes(1))
