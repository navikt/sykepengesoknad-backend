package no.nav.helse.flex.fakes

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.repository.SoknadSomSkalDeaktiveres
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadDAOImpl
import no.nav.helse.flex.service.FolkeregisterIdenter
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Repository
@Profile("fakes")
class SykepengesoknadDAOFake : SykepengesoknadDAO {
    override fun finnSykepengesoknader(identer: FolkeregisterIdenter): List<Sykepengesoknad> {
        TODO("Not yet implemented")
    }

    override fun finnSykepengesoknader(
        identer: List<String>,
        soknadstype: Soknadstype?,
    ): List<Sykepengesoknad> {
        TODO("Not yet implemented")
    }

    override fun finnSykepengesoknad(sykepengesoknadId: String): Sykepengesoknad {
        TODO("Not yet implemented")
    }

    override fun finnSykepengesoknaderForSykmelding(sykmeldingId: String): List<Sykepengesoknad> {
        TODO("Not yet implemented")
    }

    override fun populerSoknadMedDataFraAndreTabeller(soknader: MutableList<Pair<String, Sykepengesoknad>>): List<Sykepengesoknad> {
        TODO("Not yet implemented")
    }

    override fun finnSykepengesoknaderUtenSporsmal(identer: List<String>): List<Sykepengesoknad> {
        TODO("Not yet implemented")
    }

    override fun finnMottakerAvSoknad(soknadUuid: String): Mottaker? {
        TODO("Not yet implemented")
    }

    override fun lagreSykepengesoknad(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
        TODO("Not yet implemented")
    }

    override fun oppdaterKorrigertAv(sykepengesoknad: Sykepengesoknad) {
        TODO("Not yet implemented")
    }

    override fun oppdaterStatus(sykepengesoknad: Sykepengesoknad) {
        TODO("Not yet implemented")
    }

    override fun settSendtNav(
        sykepengesoknadId: String,
        sendtNav: LocalDateTime,
    ) {
        TODO("Not yet implemented")
    }

    override fun settSendtAg(
        sykepengesoknadId: String,
        sendtAg: LocalDateTime,
    ) {
        TODO("Not yet implemented")
    }

    override fun aktiverSoknad(uuid: String) {
        TODO("Not yet implemented")
    }

    override fun avbrytSoknad(
        sykepengesoknad: Sykepengesoknad,
        dato: LocalDate,
    ) {
        TODO("Not yet implemented")
    }

    override fun gjenapneSoknad(sykepengesoknad: Sykepengesoknad) {
        TODO("Not yet implemented")
    }

    override fun slettAlleSvar(sykepengesoknad: Sykepengesoknad) {
        TODO("Not yet implemented")
    }

    override fun nullstillSoknader(fnr: String): Int {
        TODO("Not yet implemented")
    }

    override fun slettSoknad(sykepengesoknad: Sykepengesoknad) {
        TODO("Not yet implemented")
    }

    override fun slettSoknad(sykepengesoknadUuid: String) {
        TODO("Not yet implemented")
    }

    override fun finnAlleredeOpprettetSoknad(identer: FolkeregisterIdenter): Sykepengesoknad? {
        TODO("Not yet implemented")
    }

    override fun byttUtSporsmal(oppdatertSoknad: Sykepengesoknad) {
        TODO("Not yet implemented")
    }

    override fun sykepengesoknadId(uuid: String): String {
        TODO("Not yet implemented")
    }

    override fun klippSoknadTom(
        sykepengesoknadUuid: String,
        nyTom: LocalDate,
        tom: LocalDate,
        fom: LocalDate,
    ): List<Soknadsperiode> {
        TODO("Not yet implemented")
    }

    override fun klippSoknadFom(
        sykepengesoknadUuid: String,
        nyFom: LocalDate,
        fom: LocalDate,
        tom: LocalDate,
    ): List<Soknadsperiode> {
        TODO("Not yet implemented")
    }

    override fun oppdaterTom(
        sykepengesoknadId: String,
        nyTom: LocalDate,
        tom: LocalDate,
        fom: LocalDate,
    ) {
        TODO("Not yet implemented")
    }

    override fun oppdaterFom(
        sykepengesoknadId: String,
        nyFom: LocalDate,
        fom: LocalDate,
        tom: LocalDate,
    ) {
        TODO("Not yet implemented")
    }

    override fun sendSoknad(
        sykepengesoknad: Sykepengesoknad,
        mottaker: Mottaker,
        avsendertype: Avsendertype,
    ): Sykepengesoknad {
        TODO("Not yet implemented")
    }

    override fun sykepengesoknadRowMapper(): RowMapper<Pair<String, Sykepengesoknad>> {
        TODO("Not yet implemented")
    }

    override fun finnGamleUtkastForSletting(): List<SykepengesoknadDAOImpl.GammeltUtkast> {
        TODO("Not yet implemented")
    }

    override fun deaktiverSoknader(): List<SoknadSomSkalDeaktiveres> {
        TODO("Not yet implemented")
    }

    override fun finnUpubliserteUtlopteSoknader(): List<String> {
        TODO("Not yet implemented")
    }

    override fun settUtloptPublisert(
        sykepengesoknadId: String,
        publisert: LocalDateTime,
    ) {
        TODO("Not yet implemented")
    }
}
