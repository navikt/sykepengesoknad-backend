package no.nav.helse.flex.fakes

import no.nav.helse.flex.domain.*
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import no.nav.helse.flex.repository.*
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.soknadsopprettelse.sorterSporsmal
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
@Profile("fakes")
@Primary
class SykepengesoknadDAOFake : SykepengesoknadDAO {
    @Autowired
    private lateinit var svarRepositoryFake: SvarRepositoryFake

    @Autowired
    lateinit var soknadLagrer: SoknadLagrer

    @Autowired
    lateinit var sykepengesoknadRepository: SykepengesoknadRepositoryFake

    @Autowired
    lateinit var sporsmalRepositoryFake: SporsmalRepositoryFake

    @Autowired
    lateinit var soknadsperiodeRepositoryFake: SoknadsperiodeRepositoryFake

    @Autowired
    lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    override fun finnSykepengesoknader(identer: FolkeregisterIdenter): List<Sykepengesoknad> {
        return sykepengesoknadRepository.findAll()
            .filter { it.fnr in identer.alle() }
            .map { it.hentOgDenormaliserSykepengesoknad() }
            .map { it.sorterSporsmal() }
            .sortedBy { it.opprettet }
    }

    override fun finnSykepengesoknader(
        identer: List<String>,
        soknadstype: Soknadstype?,
    ): List<Sykepengesoknad> {
        return finnSykepengesoknader(
            FolkeregisterIdenter(
                identer.first(),
                identer,
            ),
        ).filter {
            if (soknadstype == null) {
                true
            } else {
                it.soknadstype == soknadstype
            }
        }
    }

    fun SykepengesoknadDbRecord.hentOgDenormaliserSykepengesoknad(): Sykepengesoknad {
        val sporsmal = sporsmalRepositoryFake.findAll().filter { it.sykepengesoknadId == this.id }
        val spormalIDer = sporsmal.map { it.id }
        val svar = svarRepositoryFake.findAll().filter { it.sporsmalId in spormalIDer }
        val perioder = soknadsperiodeRepositoryFake.findAll().filter { it.sykepengesoknadId == this.id }
        return NormalisertSoknad(
            soknad = this,
            sporsmal = sporsmal,
            svar = svar,
            perioder = perioder,
        ).denormaliser().sorterSporsmal()
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
        return sykepengesoknadRepository.findAll()
            .filter { it.fnr in identer }
            .map { it.hentOgDenormaliserSykepengesoknad() }
            .sortedBy { it.opprettet }
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
        return slettSoknad(sykepengesoknad.id)
    }

    override fun slettSoknad(sykepengesoknadUuid: String) {
        val sykepengesoknadId = sykepengesoknadId(sykepengesoknadUuid)
        sporsmalRepositoryFake.slettSporsmalOgSvar(listOf(sykepengesoknadId))
        soknadsperiodeRepositoryFake.slettPerioder(listOf(sykepengesoknadId))

        medlemskapVurderingRepository.deleteBySykepengesoknadId(sykepengesoknadUuid)
        sykepengesoknadRepository.deleteById(sykepengesoknadId)
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
        val sendt = Instant.now()
        val sendtNav = if (Mottaker.NAV == mottaker || Mottaker.ARBEIDSGIVER_OG_NAV == mottaker) sendt else null
        val sendtArbeidsgiver =
            if (Mottaker.ARBEIDSGIVER == mottaker || Mottaker.ARBEIDSGIVER_OG_NAV == mottaker) sendt else null

        sykepengesoknadRepository.findBySykepengesoknadUuid(sykepengesoknad.id)?.also { sykepengesoknadDbRecord ->
            sykepengesoknadDbRecord.copy(
                status = Soknadstatus.SENDT,
                avsendertype = avsendertype,
                sendtNav = sendtNav,
                sendtArbeidsgiver = sendtArbeidsgiver,
                sendt = sendt,
            )
                .also { sykepengesoknadRepository.save(it) }
        }

        return finnSykepengesoknad(sykepengesoknad.id)
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
