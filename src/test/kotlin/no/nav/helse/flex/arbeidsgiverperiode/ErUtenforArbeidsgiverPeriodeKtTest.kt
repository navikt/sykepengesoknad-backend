package no.nav.helse.flex.arbeidsgiverperiode

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.mock.opprettNySoknad
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ErUtenforArbeidsgiverPeriodeKtTest {
    @Test
    fun `tolv dager er innenfor agp`() {
        val kortSoknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 7, 1),
                tom = LocalDate.of(2022, 7, 12),
            )

        kortSoknad.harDagerNAVSkalBetaleFor(emptyList()).`should be false`()
    }

    @Test
    fun `seksten dager er innenfor agp`() {
        val kortSoknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 7, 1),
                tom = LocalDate.of(2022, 7, 16),
            )

        kortSoknad.harDagerNAVSkalBetaleFor(emptyList()).`should be false`()
    }

    @Test
    fun `sytten dager er utenfor agp, siste dag er en søndag`() {
        val soknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 7, 1),
                tom = LocalDate.of(2022, 7, 17),
            )

        soknad.harDagerNAVSkalBetaleFor(emptyList()).`should be false`()
    }

    @Test
    fun `sytten dager er utenfor agp, siste dag er en lørdag`() {
        val soknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 6, 30),
                tom = LocalDate.of(2022, 7, 16),
            )

        soknad.harDagerNAVSkalBetaleFor(emptyList()).`should be false`()
    }

    @Test
    fun `sytten dager er utenfor agp, siste dag er en fredag`() {
        val soknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 6, 29),
                tom = LocalDate.of(2022, 7, 15),
            )

        soknad.harDagerNAVSkalBetaleFor(emptyList()).`should be true`()
    }

    @Test
    fun `sytten dager er utenfor agp, siste dag er en torsdag`() {
        val soknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 6, 28),
                tom = LocalDate.of(2022, 7, 14),
            )

        soknad.harDagerNAVSkalBetaleFor(emptyList()).`should be true`()
    }

    @Test
    fun `tjue dager er utenfor agp`() {
        val soknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 7, 1),
                tom = LocalDate.of(2022, 7, 20),
            )

        soknad.harDagerNAVSkalBetaleFor(emptyList()).`should be true`()
    }

    @Test
    fun `tolv dager med 5 egenmeldingsdager er utenfor agp`() {
        val kortSoknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 7, 11),
                tom = LocalDate.of(2022, 7, 22),
            ).copy(
                egenmeldingsdagerFraSykmelding =
                    listOf(
                        LocalDate.of(2022, 7, 6),
                        LocalDate.of(2022, 7, 7),
                        LocalDate.of(2022, 7, 8),
                        LocalDate.of(2022, 7, 9),
                        LocalDate.of(2022, 7, 10),
                    ).serialisertTilString(),
            )

        kortSoknad.harDagerNAVSkalBetaleFor(emptyList()).`should be true`()
    }

    @Test
    fun `to 10 dagere er utafor agp`() {
        val soknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 7, 11),
                tom = LocalDate.of(2022, 7, 20),
            )

        soknad.harDagerNAVSkalBetaleFor(
            listOf(
                opprettSendtSoknad(
                    fom = LocalDate.of(2022, 7, 1),
                    tom = LocalDate.of(2022, 7, 10),
                ),
            ),
        ).`should be true`()
    }

    @Test
    fun `5 og 10 er innenfor agp`() {
        val soknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2022, 7, 11),
                tom = LocalDate.of(2022, 7, 20),
            )

        soknad.harDagerNAVSkalBetaleFor(
            listOf(
                opprettSendtSoknad(
                    fom = LocalDate.of(2022, 7, 6),
                    tom = LocalDate.of(2022, 7, 10),
                ),
            ),
        ).`should be false`()
    }

    @Test
    fun `6 til 21 juli 2024 er innenfor agp`() {
        val soknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2024, 7, 16),
                tom = LocalDate.of(2024, 7, 21),
            )

        soknad.harDagerNAVSkalBetaleFor(
            listOf(
                opprettSendtSoknad(
                    fom = LocalDate.of(2024, 7, 6),
                    tom = LocalDate.of(2024, 7, 15),
                ),
            ),
        ).`should be false`()
    }

    @Test
    fun `6 til 22 juli 2024 er utenfor agp`() {
        val soknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2024, 7, 16),
                tom = LocalDate.of(2024, 7, 22),
            )

        soknad.harDagerNAVSkalBetaleFor(
            listOf(
                opprettSendtSoknad(
                    fom = LocalDate.of(2024, 7, 6),
                    tom = LocalDate.of(2024, 7, 15),
                ),
            ),
        ).`should be true`()
    }

    @Test
    fun `12 til 28 juli 2024 er innenfor agp`() {
        val soknad =
            opprettSendtSoknad(
                fom = LocalDate.of(2024, 7, 19),
                tom = LocalDate.of(2024, 7, 28),
            )

        soknad.harDagerNAVSkalBetaleFor(
            listOf(
                opprettSendtSoknad(
                    fom = LocalDate.of(2024, 7, 12),
                    tom = LocalDate.of(2024, 7, 18),
                ),
            ),
        ).`should be false`()
    }
}

fun opprettSendtSoknad(
    fom: LocalDate,
    tom: LocalDate,
) = opprettNySoknad().copy(
    fom = fom,
    tom = tom,
    status = Soknadstatus.SENDT,
)
