package no.nav.helse.flex.fakes

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.repository.*
import no.nav.helse.flex.service.FolkeregisterIdenter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
@Profile("fakes")
@Primary
class SykepengesoknadDAOFake : SykepengesoknadDAO {
    @Autowired
    lateinit var soknadLagrer: SoknadLagrer

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepositoryFake

    @Autowired
    lateinit var sporsmalRepositoryFake: SporsmalRepositoryFake

    override fun finnSykepengesoknader(identer: FolkeregisterIdenter): List<Sykepengesoknad> {
        return sykepengesoknadRepository.findAll()
            .filter { it.fnr in identer.alle() }
            .map { it.hentOgDenormaliserSykepengesoknad() }
    }

    override fun finnSykepengesoknader(
        identer: List<String>,
        soknadstype: Soknadstype?,
    ): List<Sykepengesoknad> {
        TODO("Not yet implemented")
    }

    fun SykepengesoknadDbRecord.hentOgDenormaliserSykepengesoknad(): Sykepengesoknad {
        return NormalisertSoknad(
            soknad = this,
            sporsmal = emptyList(),
            svar = emptyList(),
            perioder = emptyList(),
        ).denormaliser()
    }

    override fun finnSykepengesoknad(sykepengesoknadId: String): Sykepengesoknad {
        val sykepengesoknad =
            sykepengesoknadRepository.findAll().firstOrNull { it.sykepengesoknadUuid == sykepengesoknadId }
                ?.hentOgDenormaliserSykepengesoknad()

        return sykepengesoknad ?: throw SykepengesoknadDAO.SoknadIkkeFunnetException()
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
        soknadLagrer.lagreSoknad(sykepengesoknad)
        return finnSykepengesoknad(sykepengesoknad.id)
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
        sykepengesoknadRepository.findBySykepengesoknadUuid(uuid)?.also {
            it.copy(
                status = Soknadstatus.NY,
                aktivertDato = LocalDate.now(),
            )
                .also { sykepengesoknadRepository.save(it) }
        }
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
        val sykepengesoknadId = sykepengesoknadId(oppdatertSoknad.id)
        sporsmalRepositoryFake.slettSporsmalOgSvar(listOf(sykepengesoknadId))
        soknadLagrer.lagreSporsmalOgSvarFraSoknad(oppdatertSoknad)
    }

    override fun sykepengesoknadId(uuid: String): String {
        return sykepengesoknadRepository.findAll().first {
            it.sykepengesoknadUuid == uuid
        }.id ?: throw RuntimeException("Fant ikke sykepengesoknad med uuid $uuid")
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

    override fun finnGamleUtkastForSletting(): List<SykepengesoknadDAOPostgres.GammeltUtkast> {
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
