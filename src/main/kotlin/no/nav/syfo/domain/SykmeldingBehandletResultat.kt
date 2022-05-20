package no.nav.syfo.domain

enum class SykmeldingBehandletResultat {
    AVVENTENDE_SYKMELDING,
    IKKE_DIGITALISERT,
    INNENFOR_VENTETID,
    UNDER_BEHANDLING,
    IGNORERT,
    SOKNAD_OPPRETTET,
    SYKMELDING_OK,
}
