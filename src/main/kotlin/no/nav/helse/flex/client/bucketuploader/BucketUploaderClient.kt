package no.nav.helse.flex.client.bucketuploader

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class BucketUploaderClient(
    @Value("\${flex.bucket.uploader.url}")
    private val bucketUploderUrl: String,
    private val flexBucketUploaderRestTemplate: RestTemplate,
) {

    fun slettKvittering(blobName: String): Boolean {
        val response = flexBucketUploaderRestTemplate.getForEntity(
            "$bucketUploderUrl/maskin/slett/$blobName",
            VedleggRespons::class.java
        )
        return response.statusCode.is2xxSuccessful
    }
}

data class VedleggRespons(val id: String? = null, val melding: String)
