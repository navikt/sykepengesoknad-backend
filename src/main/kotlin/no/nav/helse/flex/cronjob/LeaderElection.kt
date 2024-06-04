package no.nav.helse.flex.cronjob

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.objectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.availability.ApplicationAvailability
import org.springframework.boot.availability.LivenessState
import org.springframework.boot.availability.ReadinessState
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.InetAddress

@Component
class LeaderElection(
    private val plainTextUtf8RestTemplate: RestTemplate,
    @param:Value("\${elector.path}") private val electorPath: String,
    private val applicationAvailability: ApplicationAvailability,
) {
    val log = logger()

    fun isLeader(): Boolean {
        if (applicationAvailability.readinessState == ReadinessState.REFUSING_TRAFFIC ||
            applicationAvailability.livenessState == LivenessState.BROKEN
        ) {
            log.info(
                "Ser ikke etter leader med readiness [ ${applicationAvailability.readinessState} ] og " +
                    "liveness [ ${applicationAvailability.livenessState} ]",
            )
            return false
        }

        if (electorPath == "dont_look_for_leader") {
            log.info("Ser ikke etter leader, returnerer at jeg er leader")
            return true
        }

        return kallElector()
    }

    private fun kallElector(): Boolean {
        val hostname: String = InetAddress.getLocalHost().hostName

        val uriString =
            UriComponentsBuilder.fromHttpUrl(getHttpPath(electorPath))
                .toUriString()
        val result =
            plainTextUtf8RestTemplate
                .exchange(
                    uriString,
                    HttpMethod.GET,
                    null,
                    String::class.java,
                )
        if (result.statusCode != HttpStatus.OK) {
            val message = "Kall mot elector feiler med HTTP-" + result.statusCode
            log.error(message)
            throw RuntimeException(message)
        }

        result.body?.let {
            val leader: Leader = objectMapper.readValue(it)
            return leader.name == hostname
        }

        val message = "Kall mot elector returnerer ikke data"
        log.error(message)
        throw RuntimeException(message)
    }

    private fun getHttpPath(url: String): String =
        when (url.startsWith("http://")) {
            true -> url
            else -> "http://$url"
        }

    private data class Leader(val name: String)
}
