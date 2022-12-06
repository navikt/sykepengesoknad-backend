package no.nav.helse.flex.cronjob

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Component
class LangSykemelding(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val leaderElection: LeaderElection,
) {
    val log = logger()
    val sykmeldingId = "8ab87dff-723f-42d2-a042-d7ea6bff02c0"

    data class Soknad(
        val soknadId: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val status: Soknadstatus,
    )

    val forventedeSoknader = setOf(
        Soknad(
            "b27bc92a-985a-3653-b27b-5e4133d5e50e",
            LocalDate.parse("2023-04-29"),
            LocalDate.parse("2023-05-26"),
            Soknadstatus.valueOf("FREMTIDIG")
        ),
        Soknad(
            "56f90566-1cb0-3e58-865e-6acf7bbd77c8",
            LocalDate.parse("2023-03-31"),
            LocalDate.parse("2023-04-28"),
            Soknadstatus.valueOf("FREMTIDIG")
        ),
        Soknad(
            "af8ca5f3-9ed3-3b27-849e-7517cca68b13",
            LocalDate.parse("2023-03-02"),
            LocalDate.parse("2023-03-30"),
            Soknadstatus.valueOf("FREMTIDIG")
        ),
        Soknad(
            "0bf97423-115c-31f7-9e84-9c7f5784170e",
            LocalDate.parse("2023-02-01"),
            LocalDate.parse("2023-03-01"),
            Soknadstatus.valueOf("FREMTIDIG")
        ),
        Soknad(
            "b32add95-2646-39a3-b255-4296fd863c7e",
            LocalDate.parse("2023-01-03"),
            LocalDate.parse("2023-01-31"),
            Soknadstatus.valueOf("FREMTIDIG")
        ),
        Soknad(
            "afd63cc9-df6e-34c9-b34e-528d36c8c432",
            LocalDate.parse("2022-12-05"),
            LocalDate.parse("2023-01-02"),
            Soknadstatus.valueOf("FREMTIDIG")
        ),
        Soknad(
            "63c050c6-72dd-31d2-ac1f-333963f447fc",
            LocalDate.parse("2022-11-06"),
            LocalDate.parse("2022-12-04"),
            Soknadstatus.valueOf("NY")
        ),
        Soknad(
            "decc7084-cedd-3688-87ca-dd203fa88beb",
            LocalDate.parse("2022-10-27"),
            LocalDate.parse("2022-11-05"),
            Soknadstatus.valueOf("AVBRUTT")
        ),
        Soknad(
            "5ce858f4-4967-3b52-b5c4-c75f14996485",
            LocalDate.parse("2022-09-09"),
            LocalDate.parse("2022-09-28"),
            Soknadstatus.valueOf("SENDT")
        ),
        Soknad(
            "6fcf3713-9768-304a-bd36-5711073b8f7f",
            LocalDate.parse("2022-09-08"),
            LocalDate.parse("2022-09-08"),
            Soknadstatus.valueOf("SENDT")
        )
    )

    @Scheduled(initialDelay = 5, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun fjernUbrukteSoknader() {
        val leader = leaderElection.isLeader()
        log.info("Starter cronjob fjernUbrukteSoknader leader=$leader")

        if (leader) {
            val soknader = sykepengesoknadDAO.finnSykepengesoknaderForSykmelding(sykmeldingId)

            val samenlignbareSoknader = soknader.map { Soknad(it.id, it.fom!!, it.tom!!, it.status) }
            require(samenlignbareSoknader.all { forventedeSoknader.contains(it) })
            require(samenlignbareSoknader.size == 10)

            val soknaderSomSkalSlettes = soknader.filter { it.fom!! > LocalDate.of(2022, 10, 26) }
            require(soknaderSomSkalSlettes.size == 8)

            log.info("Soknader som skal slettes [ ${soknaderSomSkalSlettes.map { it.id }} ], soknader som ikke slettes [ ${(soknader - soknaderSomSkalSlettes).map { it.id }} ]")
        }
    }
}
