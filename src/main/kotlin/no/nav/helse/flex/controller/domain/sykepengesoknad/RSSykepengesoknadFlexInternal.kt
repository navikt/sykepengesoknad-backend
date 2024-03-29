package no.nav.helse.flex.controller.domain.sykepengesoknad
import java.time.LocalDate
import java.time.LocalDateTime

data class RSSykepengesoknadFlexInternal(
    val id: String,
    val sykmeldingId: String? = null,
    val soknadstype: RSSoknadstype,
    val status: RSSoknadstatus?,
    val fom: LocalDate? = null,
    val tom: LocalDate? = null,
    val opprettetDato: LocalDateTime?,
    val sendtTilNAVDato: LocalDateTime? = null,
    val sendtTilArbeidsgiverDato: LocalDateTime? = null,
    val avbruttDato: LocalDate? = null,
    val startSykeforlop: LocalDate? = null,
    val sykmeldingUtskrevet: LocalDateTime? = null,
    val sykmeldingSignaturDato: LocalDateTime? = null,
    val arbeidsgiver: RSArbeidsgiver? = null,
    val arbeidsgiverNavn: String? = null,
    val arbeidsgiverOrgnummer: String? = null,
    val korrigerer: String? = null,
    val korrigertAv: String? = null,
    val arbeidssituasjon: RSArbeidssituasjon? = null,
    val soknadPerioder: List<RSSoknadsperiode>? = null,
    val merknaderFraSykmelding: List<RSMerknad>?,
)
