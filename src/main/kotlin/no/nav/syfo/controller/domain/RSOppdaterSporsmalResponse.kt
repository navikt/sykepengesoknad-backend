package no.nav.syfo.controller.domain

import no.nav.syfo.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.syfo.controller.domain.sykepengesoknad.RSSykepengesoknad

data class RSOppdaterSporsmalResponse(val mutertSoknad: RSSykepengesoknad?, val oppdatertSporsmal: RSSporsmal)
