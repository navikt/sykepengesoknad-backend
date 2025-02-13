package no.nav.helse.flex.client.brreg

class Rolle(
    val rolletype: Rolletype,
    val organisasjonsnummer: String,
    val organisasjonsnavn: String,
)

enum class Rolletype(
    val beskrivelse: List<String>,
) {
    ADOS(listOf("Administrativ enhet - offentlig sektor")),
    BEST(listOf("Bestyrende reder")),
    BOBE(listOf("Bostyrer")),
    DAGL(listOf("Daglig leder", "Daglig leder/ adm.direktør", "Dagleg leiar/ adm.direktør")),
    DTPR(listOf("Deltaker med proratarisk ansvar (delt ansvar)", "Deltaker med delt ansvar")),
    DTSO(listOf("Deltaker med solidarisk ansvar (fullt ansvarlig)", "Deltaker med fullt ansvar")),
    EIKM(listOf("Eierkommune")),
    FFØR(listOf("Forretningsfører", "Forretningsførar")),
    HFOR(listOf("Opplysninger om foretaket i hjemlandet")),
    HLSE(listOf("Helseforetak")),
    INNH(listOf("Innehaver", "Innehavar")),
    KDEB(listOf("Konkursdebitor")),
    KENK(listOf("Den personlige konkursen angår")),
    KIRK(listOf("Inngår i kirkelig fellesråd")),
    KOMP(listOf("Komplementar")),
    KONT(listOf("Kontaktperson")),
    KTRF(listOf("Inngår i kontorfellesskap")),
    LEDE(listOf("Styrets leder", "Styreleiar")),
    MEDL(listOf("Styremedlem")),
    NEST(listOf("Nestleder", "Nestleiar")),
    OBS(listOf("Observatør")),
    OPMV(listOf("er særskilt oppdelt enhet til")),
    ORGL(listOf("Organisasjonsledd i offentlig sektor")),
    POFE(listOf("Prokura i fellesskap")),
    POHV(listOf("Prokura hver for seg", "Prokura kvar for seg")),
    PROK(listOf("Prokura")),
    REGN(listOf("Regnskapsfører", "Rekneskapsførar")),
    REPR(listOf("Norsk representant for utenlandsk enhet", "Norsk repr. for utenl. enhet")),
    REVI(listOf("Revisor")),
    SIHV(listOf("Signatur hver for seg")),
    SIFE(listOf("Signatur i fellesskap")),
    SIGN(listOf("Signatur")),
    VARA(listOf("Varamedlem")),
    UKJENT(listOf("Ukjent rolle")),
    ;

    fun erSelvstendigNaringdrivende() = this == INNH || this == DTPR || this == DTSO || this == KOMP
}

data class HentRollerRequest(
    val fnr: String,
    val rolleTyper: List<Rolletype>? = null,
)
