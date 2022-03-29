package no.nav.syfo.config

import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import java.time.Duration

@Configuration
class CacheConfig {
    @Bean
    fun cacheManager(
        redisConnectionFactory: RedisConnectionFactory,
        environmentToggles: EnvironmentToggles
    ): CacheManager {
        val cacheConfigurations: MutableMap<String, RedisCacheConfiguration> = HashMap()

        val narmestelederTtl = if (environmentToggles.isProduction()) {
            300L
        } else {
            10L
        }

        cacheConfigurations["arbeidsgiver"] = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(narmestelederTtl))
        cacheConfigurations["narmesteleder-aktive"] = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(narmestelederTtl))
        cacheConfigurations["forskuttering"] = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(narmestelederTtl))
        cacheConfigurations["forskuttering-narmesteleder"] = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(narmestelederTtl))
        cacheConfigurations["aktor-for-fnr"] = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(3600L))
        cacheConfigurations["folkeregister-identer-med-historikk"] = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(3600L))

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig())
            .withInitialCacheConfigurations(cacheConfigurations)
            .build()
    }
}
