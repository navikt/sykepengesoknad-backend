package no.nav.helse.flex.domain

import java.io.Serializable

data class Sporsmal(
    val id: String? = null,
    val tag: String,
    val sporsmalstekst: String? = null,
    val undertekst: String? = null,
    val svartype: Svartype,
    val min: String? = null,
    val max: String? = null,
    val kriterieForVisningAvUndersporsmal: Visningskriterie? = null,
    val svar: List<Svar> = emptyList(),
    val undersporsmal: List<Sporsmal> = emptyList(),
) : Serializable {
    class SporsmalBuilder(
        var id: String? = null,
        var tag: String? = null,
        var sporsmalstekst: String? = null,
        var undertekst: String? = null,
        var svartype: Svartype? = null,
        var min: String? = null,
        var max: String? = null,
        var kriterieForVisningAvUndersporsmal: Visningskriterie? = null,
        var svar: List<Svar> = emptyList(),
        var undersporsmal: List<Sporsmal> = emptyList(),
    ) {
        fun id(id: String?): SporsmalBuilder {
            this.id = id
            return this
        }

        fun tag(tag: String): SporsmalBuilder {
            this.tag = tag
            return this
        }

        fun sporsmalstekst(sporsmalstekst: String?): SporsmalBuilder {
            this.sporsmalstekst = sporsmalstekst
            return this
        }

        fun undertekst(undertekst: String?): SporsmalBuilder {
            this.undertekst = undertekst
            return this
        }

        fun svartype(svartype: Svartype): SporsmalBuilder {
            this.svartype = svartype
            return this
        }

        fun min(min: String?): SporsmalBuilder {
            this.min = min
            return this
        }

        fun max(max: String?): SporsmalBuilder {
            this.max = max
            return this
        }

        fun kriterieForVisningAvUndersporsmal(kriterieForVisningAvUndersporsmal: Visningskriterie?): SporsmalBuilder {
            this.kriterieForVisningAvUndersporsmal = kriterieForVisningAvUndersporsmal
            return this
        }

        fun svar(svar: List<Svar>): SporsmalBuilder {
            this.svar = svar
            return this
        }

        fun undersporsmal(undersporsmal: List<Sporsmal>): SporsmalBuilder {
            this.undersporsmal = undersporsmal
            return this
        }

        fun build() =
            Sporsmal(
                id = id,
                tag = tag!!,
                sporsmalstekst = sporsmalstekst,
                undertekst = undertekst,
                svartype = svartype!!,
                min = min,
                max = max,
                kriterieForVisningAvUndersporsmal = kriterieForVisningAvUndersporsmal,
                svar = svar,
                undersporsmal = undersporsmal,
            )
    }

    fun toBuilder() =
        SporsmalBuilder(
            id = id,
            tag = tag,
            sporsmalstekst = sporsmalstekst,
            undertekst = undertekst,
            svartype = svartype,
            min = min,
            max = max,
            kriterieForVisningAvUndersporsmal = kriterieForVisningAvUndersporsmal,
            svar = svar,
            undersporsmal = undersporsmal,
        )

    val forsteSvar: String?
        get() =
            if (svar.isEmpty()) {
                null
            } else {
                svar[0].verdi
            }
}

fun sporsmalBuilder(): Sporsmal.SporsmalBuilder = Sporsmal.SporsmalBuilder()
