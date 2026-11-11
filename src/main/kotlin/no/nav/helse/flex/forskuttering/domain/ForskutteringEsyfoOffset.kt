package no.nav.helse.flex.forskuttering.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table

@Table("forskuttering_esyfo_checkpoint")
data class ForskutteringEsyfoOffset(
    @Id
    @Embedded.Nullable
    val id: ForskutteringEsyfoOffsetId,
    val lastProcessedOffset: Long,
)

data class ForskutteringEsyfoOffsetId(
    val topic: String,
    val partition: Int,
)
