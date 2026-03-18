package no.nav.syfo.model.sykmelding.arbeidsgiver

import no.nav.syfo.model.Merknad
import java.time.LocalDate
import java.time.OffsetDateTime

data class ArbeidsgiverSykmeldingDTO(
    val id: String,
    val mottattTidspunkt: OffsetDateTime,
    val syketilfelleStartDato: LocalDate?,
    val behandletTidspunkt: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverAGDTO,
    val sykmeldingsperioder: List<SykmeldingsperiodeAGDTO>,
    val prognose: PrognoseAGDTO?,
    val tiltakArbeidsplassen: String?,
    val meldingTilArbeidsgiver: String?,
    val kontaktMedPasient: KontaktMedPasientAGDTO,
    val behandler: BehandlerAGDTO?,
    val egenmeldt: Boolean,
    val papirsykmelding: Boolean,
    val harRedusertArbeidsgiverperiode: Boolean,
    val merknader: List<Merknad>?,
    val utenlandskSykmelding: UtenlandskSykmeldingAGDTO?,
    val signaturDato: OffsetDateTime?,
) {
    val fom: LocalDate?
        get() = sykmeldingsperioder.minOfOrNull { it.fom }

    val tom: LocalDate?
        get() = sykmeldingsperioder.maxOfOrNull { it.tom }

    val loglinje: String
        get() = "$id ($fom $tom)"
}
