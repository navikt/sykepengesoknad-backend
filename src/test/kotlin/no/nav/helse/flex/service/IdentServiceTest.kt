package no.nav.helse.flex.service

import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.FellesTestOppsett
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.data.redis.cache.FixedDurationTtlFunction
import org.springframework.data.redis.cache.RedisCache
import java.time.Duration
import java.util.concurrent.TimeUnit

class IdentServiceTest : FellesTestOppsett() {
    @Autowired
    private lateinit var identService: IdentService

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @AfterEach
    fun `Vi toemmer identcachen`() {
        cacheManager.getCache(CACHE_NAVN)?.clear()
    }

    @Test
    fun `Vi tester hent historikk for fnr`() {
        val responseData = identService.hentFolkeregisterIdenterMedHistorikkForFnr("31111111111")
        responseData.andreIdenter shouldBe listOf("11111111111", "21111111111")
        responseData.originalIdent shouldBe "31111111111"
    }

    @Test
    fun `Andre kall til samme ident treffer cachen og registrerer treff og bom`() {
        val ident = "cache-test-ident"
        val antallKallFoer = pdlMockWebserver.requestCount
        val treffFoer = antallCacheOppslag("hit")
        val bomFoer = antallCacheOppslag("miss")

        identService.hentFolkeregisterIdenterMedHistorikkForFnr(ident)
        await().atMost(5, TimeUnit.SECONDS).until {
            cacheManager.getCache(CACHE_NAVN)?.get(ident) != null
        }
        identService.hentFolkeregisterIdenterMedHistorikkForFnr(ident)

        pdlMockWebserver.requestCount - antallKallFoer shouldBe 1
        antallCacheOppslag("hit") - treffFoer shouldBeGreaterThanOrEqual 1.0
        antallCacheOppslag("miss") - bomFoer shouldBeGreaterThanOrEqual 1.0
    }

    @Test
    fun `Identcachen har en times levetid`() {
        val cache = cacheManager.getCache(CACHE_NAVN)
        cache.shouldBeInstanceOf(RedisCache::class)
        val redisCache = cache as RedisCache
        val ttlFunksjon = redisCache.cacheConfiguration.ttlFunction as FixedDurationTtlFunction

        ttlFunksjon.duration shouldBe Duration.ofHours(1)
    }

    private fun antallCacheOppslag(resultat: String): Double =
        meterRegistry
            .find("cache.gets")
            .tags("cache", CACHE_NAVN, "result", resultat)
            .functionCounter()
            ?.count()
            ?: 0.0

    private companion object {
        const val CACHE_NAVN = "flex-folkeregister-identer-med-historikk"
    }
}
