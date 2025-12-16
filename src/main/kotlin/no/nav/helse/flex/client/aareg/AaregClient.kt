package no.nav.helse.flex.client.aareg

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.serialisertTilString
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class AaregClient(
    @param:Value("\${AAREG_URL}")
    private val url: String,
    private val aaregRestTemplate: RestTemplate,
) {
    val log = logger()

    fun hentArbeidsforhold(fnr: String): List<Arbeidsforhold> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val result =
            aaregRestTemplate
                .exchange(
                    "$url/api/v2/arbeidstaker/arbeidsforhold",
                    HttpMethod.POST,
                    HttpEntity(
                        ArbeidsforholdRequest(
                            arbeidstakerId = fnr,
                            arbeidsforholdtyper = listOf("ordinaertArbeidsforhold", "maritimtArbeidsforhold", "forenkletOppgjoersordning"),
                            arbeidsforholdstatuser = listOf("AKTIV", "FREMTIDIG", "AVSLUTTET"),
                        ).serialisertTilString(),
                        headers,
                    ),
                    Array<Arbeidsforhold>::class.java,
                )

        if (result.statusCode != HttpStatus.OK) {
            val message = "Kall mot aareg feiler med HTTP-" + result.statusCode
            log.error(message)
            throw RuntimeException(message)
        }

        result.body?.let { return it.toList() }

        val message = "Kall mot aareg returnerer ikke data"
        log.error(message)
        throw RuntimeException(message)
    }
}
