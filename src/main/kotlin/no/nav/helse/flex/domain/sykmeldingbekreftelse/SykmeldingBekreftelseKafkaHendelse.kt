package no.nav.helse.flex.domain.sykmeldingbekreftelse

import no.nav.helse.flex.domain.Arbeidssituasjon
import kotlin.time.Instant

data class SykmeldingBekreftelseKafkaHendelse(
    val sykmeldingId: String,
    val hendelseType: SykmeldingBekreftelseHendelseType,
    val sykmelding: Sykmelding,
    val validering: SykmeldingValidering,
    val sykmeldtBekreftelse: SykmeldtBekreftelse,
)

enum class SykmeldingBekreftelseHendelseType {
    NY,
    SYKMELDING_OPPDATERT,
    VALIDERING_OPPDATERT,
    BRUKERSTATUS_OPPDATERT,
}

data class Sykmelding(
    val sykmeldingId: String,
)

enum class SykmeldingValideringStatus {
    OK,
    UGJYLDIG,
    BEHANDLES,
}

data class SykmeldingValidering(
    val valideringStatus: SykmeldingValideringStatus,
)

enum class SykmeldtBekreftelseStatus {
    APEN,
    BEKREFTET,
    BEKREFTET_AVVIST,
    AVBRUTT,
}

data class SykmeldtBekreftelse(
    val status: SykmeldtBekreftelseStatus,
    val tidspunkt: Instant,
    val situasjon: SykmeldtSituasjon? = null,
)

data class SykmeldtSituasjon(
    val arbeidssituasjon: Arbeidssituasjon,
)
