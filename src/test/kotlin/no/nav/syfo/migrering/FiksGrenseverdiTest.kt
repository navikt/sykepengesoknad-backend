package no.nav.syfo.migrering

import no.nav.syfo.soknadsopprettelse.PERMITTERT_NAA_NAR
import no.nav.syfo.soknadsopprettelse.settOppSoknadArbeidstaker
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should not be equal to`
import org.junit.jupiter.api.Test
import skapSoknadMetadata
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.format.DateTimeFormatter

class FiksGrenseverdiTest {

    @Test
    fun `test grenseverdi fixing uten feil grenseverdi`() {

        val sykepengesoknad = settOppSoknadArbeidstaker(
            soknadMetadata = skapSoknadMetadata(),
            erForsteSoknadISykeforlop = true,
            tidligsteFomForSykmelding = now()
        )
        val fiksaGrenseverdier = sykepengesoknad.fixGrenseverdier()

        sykepengesoknad `should be equal to` fiksaGrenseverdier
    }

    @Test
    fun `test grenseverdi fixing med feil grenseverdi`() {

        val sykepengesoknad = settOppSoknadArbeidstaker(
            soknadMetadata = skapSoknadMetadata(),
            erForsteSoknadISykeforlop = true,
            tidligsteFomForSykmelding = now()
        )
        val spmMedFeilVerdi = sykepengesoknad.getSporsmalMedTag(PERMITTERT_NAA_NAR)
            .copy(min = LocalDate.of(2021, 5, 2).format(DateTimeFormatter.ISO_LOCAL_DATE))

        val soknadMedFeilVerdi = sykepengesoknad.replaceSporsmal(spmMedFeilVerdi)
        val fiksaGrenseverdier = soknadMedFeilVerdi.fixGrenseverdier()

        soknadMedFeilVerdi `should not be equal to` fiksaGrenseverdier

        soknadMedFeilVerdi.getSporsmalMedTag(PERMITTERT_NAA_NAR).min `should be equal to` "2021-05-02"
        fiksaGrenseverdier.getSporsmalMedTag(PERMITTERT_NAA_NAR).min `should be equal to` "2020-02-01"

        soknadMedFeilVerdi.fjernSporsmal(PERMITTERT_NAA_NAR) `should be equal to` fiksaGrenseverdier.fjernSporsmal(
            PERMITTERT_NAA_NAR
        )
    }

    @Test
    fun `test grenseverdi fixing uten sporsmalet i seg`() {

        val sykepengesoknad = settOppSoknadArbeidstaker(
            soknadMetadata = skapSoknadMetadata(),
            erForsteSoknadISykeforlop = false,
            tidligsteFomForSykmelding = now()
        )
        val fiksaGrenseverdier = sykepengesoknad.fixGrenseverdier()

        sykepengesoknad `should be equal to` fiksaGrenseverdier
        sykepengesoknad.getSporsmalMedTagOrNull(PERMITTERT_NAA_NAR).`should be null`()
    }
}
