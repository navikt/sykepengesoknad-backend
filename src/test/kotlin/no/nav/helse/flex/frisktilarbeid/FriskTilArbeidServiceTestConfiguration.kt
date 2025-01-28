package no.nav.helse.flex.frisktilarbeid

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(
    FriskTilArbeidService::class,
    FrisktilArbeidSoknadService::class,
    FriskTilArbeidTestConfig::class,
)
class FriskTilArbeidServiceTestConfiguration
