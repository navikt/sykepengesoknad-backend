package no.nav.syfo.model.sykmelding.kafka

import no.nav.syfo.model.Merknad
import no.nav.syfo.model.sykmelding.model.*
import java.time.LocalDate
import java.time.OffsetDateTime

data class EnkelSykmelding(
    val id: String,
    val mottattTidspunkt: OffsetDateTime,
    val legekontorOrgnummer: String?,
    val behandletTidspunkt: OffsetDateTime,
    val meldingTilArbeidsgiver: String?,
    val navnFastlege: String?,
    val tiltakArbeidsplassen: String?,
    val syketilfelleStartDato: LocalDate?,
    val behandler: BehandlerDTO,
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    val arbeidsgiver: ArbeidsgiverDTO,
    val kontaktMedPasient: KontaktMedPasientDTO,
    val prognose: PrognoseDTO?,
    val egenmeldt: Boolean,
    val papirsykmelding: Boolean,
    val harRedusertArbeidsgiverperiode: Boolean,
    val merknader: List<Merknad>?
)
