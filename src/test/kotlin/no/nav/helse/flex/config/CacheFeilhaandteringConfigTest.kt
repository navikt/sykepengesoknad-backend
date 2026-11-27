package no.nav.helse.flex.config

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.client.pdl.FOLKEREGISTERIDENT
import no.nav.helse.flex.client.pdl.PdlClient
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@SpringBootTest(classes = [CacheFeilhaandteringConfigTest.TestConfig::class])
class CacheFeilhaandteringConfigTest {
    @Autowired
    private lateinit var identService: IdentService

    @Autowired
    private lateinit var pdlClient: PdlClient

    @Test
    fun `cache-lesefeil kaster ikke og metoden henter fra PDL`() {
        `when`(pdlClient.hentIdenterMedHistorikk("fnr"))
            .thenReturn(listOf(PdlIdent(gruppe = FOLKEREGISTERIDENT, ident = "annen-ident")))

        repeat(2) {
            identService.hentFolkeregisterIdenterMedHistorikkForFnr("fnr") shouldBe
                FolkeregisterIdenter(
                    originalIdent = "fnr",
                    andreIdenter = listOf("annen-ident"),
                )
        }

        verify(pdlClient, times(2)).hentIdenterMedHistorikk("fnr")
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    @Import(CacheFeilhaandteringConfig::class)
    class TestConfig {
        @Bean
        fun pdlClient(): PdlClient = mock(PdlClient::class.java)

        @Bean
        fun identService(pdlClient: PdlClient): IdentService = IdentService(pdlClient)

        @Bean
        fun cacheManager(): CacheManager =
            object : CacheManager {
                override fun getCache(name: String): Cache = CacheMedFeil(name)

                override fun getCacheNames(): Collection<String> = listOf("flex-folkeregister-identer-med-historikk")
            }
    }

    private class CacheMedFeil(name: String) : ConcurrentMapCache(name) {
        override fun lookup(key: Any): Any = throw IllegalStateException("Valkey er utilgjengelig")

        override fun put(key: Any, value: Any?) {
            throw IllegalStateException("Valkey er utilgjengelig")
        }
    }
}
