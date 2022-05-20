package no.nav.helse.flex.controller.domain

import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad

data class RSOppdaterSporsmalResponse(val mutertSoknad: RSSykepengesoknad?, val oppdatertSporsmal: RSSporsmal)
