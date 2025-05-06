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

class RestFeilerException : SkalRebehandlesException("Restkall feiler", OffsetDateTime.now().plusMinutes(1))

class SykeforloepManglerSykemeldingException(
    msg: String = "Mangler periode",
) : SkalRebehandlesException(
        msg,
        OffsetDateTime.now().plusMinutes(15),
    )

class ManglerSykmeldingException :
    SkalRebehandlesException(
        "Kall mot smregister returnerer ikke data",
        OffsetDateTime.now().plusMinutes(15),
    )

class ProduserKafkaMeldingException :
    SkalRebehandlesException(
        "Klarer ikke legge inn melding på kafka",
        OffsetDateTime.now().plusMinutes(1),
    )

class SlettSoknadException : SkalRebehandlesException("Feil ved sletting av søknad", OffsetDateTime.now().plusMinutes(1))
