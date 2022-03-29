package no.nav.syfo.domain

import java.io.Serializable

data class Sporsmal(
    val id: String? = null,
    val tag: String,
    val sporsmalstekst: String? = null,
    val undertekst: String? = null,
    val svartype: Svartype,
    val min: String? = null,
    val max: String? = null,
    val pavirkerAndreSporsmal: Boolean = false,
    val kriterieForVisningAvUndersporsmal: Visningskriterie? = null,
    val svar: List<Svar> = emptyList(),
    val undersporsmal: List<Sporsmal> = emptyList()
) : Serializable {
    class SporsmalBuilder(
        var id: String? = null,
        var tag: String? = null,
        var sporsmalstekst: String? = null,
        var undertekst: String? = null,
        var svartype: Svartype? = null,
        var min: String? = null,
        var max: String? = null,
        var pavirkerAndreSporsmal: Boolean = false,
        var kriterieForVisningAvUndersporsmal: Visningskriterie? = null,
        var svar: List<Svar> = emptyList(),
        var undersporsmal: List<Sporsmal> = emptyList()
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

        fun pavirkerAndreSporsmal(pavirkerAndreSporsmal: Boolean): SporsmalBuilder {
            this.pavirkerAndreSporsmal = pavirkerAndreSporsmal
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
                pavirkerAndreSporsmal = pavirkerAndreSporsmal,
                kriterieForVisningAvUndersporsmal = kriterieForVisningAvUndersporsmal,
                svar = svar,
                undersporsmal = undersporsmal
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
            pavirkerAndreSporsmal = pavirkerAndreSporsmal,
            kriterieForVisningAvUndersporsmal = kriterieForVisningAvUndersporsmal,
            svar = svar,
            undersporsmal = undersporsmal
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Sporsmal

        if (id != other.id) return false
        if (tag != other.tag) return false
        if (sporsmalstekst != other.sporsmalstekst) return false
        if (undertekst != other.undertekst) return false
        if (svartype != other.svartype) return false
        if (min != other.min) return false
        if (max != other.max) return false
        if (pavirkerAndreSporsmal != other.pavirkerAndreSporsmal) return false
        if (kriterieForVisningAvUndersporsmal != other.kriterieForVisningAvUndersporsmal) return false
        if (undersporsmal != other.undersporsmal) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + tag.hashCode()
        result = 31 * result + (sporsmalstekst?.hashCode() ?: 0)
        result = 31 * result + (undertekst?.hashCode() ?: 0)
        result = 31 * result + svartype.hashCode()
        result = 31 * result + (min?.hashCode() ?: 0)
        result = 31 * result + (max?.hashCode() ?: 0)
        result = 31 * result + pavirkerAndreSporsmal.hashCode()
        result = 31 * result + (kriterieForVisningAvUndersporsmal?.hashCode() ?: 0)
        result = 31 * result + undersporsmal.hashCode()
        return result
    }

    val forsteSvar: String?
        get() = if (svar.isEmpty())
            null
        else
            svar[0].verdi
}

fun sporsmalBuilder(): Sporsmal.SporsmalBuilder =
    Sporsmal.SporsmalBuilder()
