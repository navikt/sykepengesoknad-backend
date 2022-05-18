package no.nav.syfo.client.bucketuploader

import no.nav.syfo.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class BucketUploaderClient(
    @Value("\${flex.bucket.uploader.url}")
    private val bucketUploderUrl: String,
    private val flexBucketUploaderRestTemplate: RestTemplate,
) {

    private val log = logger()

    fun slettKvittering(blobName: String): Boolean {
        try {
            val response = flexBucketUploaderRestTemplate.getForEntity(
                "$bucketUploderUrl/maskin/slett/$blobName",
                VedleggRespons::class.java
            )
            return response.statusCode.is2xxSuccessful
        } catch (e: Exception) {
            log.warn("Kunne ikke slette kvittering med blobId $blobName", e)
        }
        return false
    }
}

data class VedleggRespons(val id: String? = null, val melding: String)
