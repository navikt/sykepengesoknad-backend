package no.nav.helse.flex.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import java.net.URI
import java.time.Duration

@Configuration
@Profile("!fakes")
@EnableCaching
class CacheConfig(
    @Value("\${VALKEY_URI_IDENTER}") val valkeyUriString: String,
    @Value("\${VALKEY_USERNAME_IDENTER}") val valkeyUsername: String,
    @Value("\${VALKEY_PASSWORD_IDENTER}") val valkeyPassword: String,
) {
    @Bean
    fun redisConnectionFactory(): LettuceConnectionFactory {
        val redisUri = URI.create(valkeyUriString)
        val redisConnection = RedisStandaloneConfiguration(redisUri.host, redisUri.port)

        redisConnection.username = valkeyUsername
        redisConnection.password = RedisPassword.of(valkeyPassword)

        val clientConfiguration =
            LettuceClientConfiguration
                .builder()
                .apply {
                    if ("default" != valkeyUsername) {
                        useSsl()
                    }
                }.build()

        return LettuceConnectionFactory(redisConnection, clientConfiguration)
    }

    @Bean
    fun cacheManager(redisConnectionFactory: RedisConnectionFactory): CacheManager {
        val cacheConfigurations: MutableMap<String, RedisCacheConfiguration> = HashMap()

        cacheConfigurations["flex-folkeregister-identer-med-historikk"] =
            RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))

        return RedisCacheManager
            .builder(redisConnectionFactory)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig())
            .withInitialCacheConfigurations(cacheConfigurations)
            .enableStatistics()
            .build()
    }
}
