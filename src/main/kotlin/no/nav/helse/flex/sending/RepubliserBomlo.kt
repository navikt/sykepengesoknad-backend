package no.nav.helse.flex.sending

import no.nav.helse.flex.domain.mapper.SykepengesoknadTilSykepengesoknadDTOMapper
import no.nav.helse.flex.kafka.producer.AivenKafkaProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RepubliserBomlo(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val sykepengesoknadDTOMapper: SykepengesoknadTilSykepengesoknadDTOMapper,
    private val soknadProducer: AivenKafkaProducer,
) {
    private val tilBomlo =
        listOf(
            "aec14038-4935-3c5f-8182-ead9f93072a7",
            "3328f708-14df-3417-92f9-b492f7602b72",
            "91b1d2de-201e-31e1-92de-73d19e23a7ad",
            "77f32f67-fbbd-3f86-984b-656612f95203",
            "64cb6515-5a01-30c9-a051-958aaa3494c5",
            "e2dbb33f-b6d9-3c7a-816d-5ee5f44f42c0",
            "34c84fb9-3539-35b8-bc49-a1d445626dc7",
            "be7b7b60-fca4-3e44-95fe-e4a085ace1d1",
            "37479fea-296c-30e4-b101-2c9523718b71",
            "e0346262-e4c8-3c4b-b5ce-96482ad44406",
            "37eebf4e-18c5-331f-b14f-4926ee3bc07b",
            "ccbedb7a-b342-3fb0-9f19-86ecd1b03181",
            "45db37c8-4c02-3abd-ae39-6e252f13b632",
            "9fbf8cd5-b911-374a-af6b-8552d5df50fe",
            "fffdcfa2-0609-310f-b972-cadebdf80451",
            "d6d4b487-f0c5-3a0f-916d-bfdfd20b3bf6",
            "ab45833f-e77e-3fc3-a612-3ae4e14318ad",
            "db7d6de2-18fa-3b63-a0a0-c8c613c1e522",
            "2b25974d-e06e-3155-999c-c215a4f2e478",
            "8a4eff39-8fe2-307d-af5f-6056073df942",
            "08b23974-7a3d-3169-94c7-1a3b6295d90e",
            "50f27b8c-aea4-303b-92e3-88c1370fe11f",
            "dcafceb7-51e7-3238-8302-ccfa3163fbcd",
            "b4ff95c0-832f-3544-9c31-84504e53ab16",
            "36d40c40-72f7-3543-89ce-2226e6065cf4",
            "81662344-4a4a-3f41-917c-3092b17bc9cc",
            "ba79127c-f8ec-3f98-93b8-fcff34d4f944",
            "5bcbe11c-b78a-3a26-ba2d-f4d525093cdb",
            "3383f3aa-82e3-30f2-baea-49ed55efeef9",
            "3a904926-b776-3882-9ac5-f132a7d410eb",
            "bc38b4a8-2d6d-376f-bf5f-2307a2f4456c",
            "c5f408f4-5b86-3c19-9ce4-4e73b206a212",
            "1cc58df1-6eb0-3c16-a958-46e12f0d5fb5",
            "87bee2bc-8de4-30c6-becc-05c9414bef31",
            "e0a8e18a-628d-4d6e-9337-331b711bd949",
            "c26c0eef-c9c6-30a1-9fa4-fc36623d5421",
            "3bcb5414-81b9-30b3-9bdb-73a9f37b3d56",
            "95972dbb-3a11-3457-aba7-d2a1196fc68a",
            "9318dd21-1b07-3b95-9105-b858302331f9",
            "92071eb8-19f3-31b8-8ec0-87732febf40d",
            "c8cc9431-f06c-3ba9-8362-f3ab0d504b71",
            "11a3453a-e672-3eef-acd6-503866c40e7c",
            "aa62a10e-dc0d-3ca6-aa85-af2e2139be1b",
            "20bfd669-c0bc-32d3-b1e5-cbc62b944468",
            "4b67790d-42ae-33f6-af51-dcb945dde688",
            "963f7ad4-c8b4-3911-892a-1ee65dfc2684",
            "ae29d06a-411d-38bc-ad51-683ca93cbd6c",
            "d8e9510f-bfc5-344e-9c7b-881bf2bd0b59",
            "29d6c4ff-ff69-364d-8527-09d0884bdc40",
            "7974dadf-8d90-302d-b827-d17e204098a2",
            "9f2e6abb-f752-363c-9ff9-75612db7210e",
            "7fc12c7b-a9fe-3190-82d3-333179e7ee19",
            "124cb2ce-fa4d-352d-a9c9-5862a17c7d62",
            "26ab1525-cfed-3ab3-8c97-72332b9e5537",
            "4f36cd22-60bb-32e9-9ea6-9087e43c0513",
            "b03a0b5d-9ce3-399e-8492-56f953797b61",
            "a7eaa818-451c-31db-9f54-536bbfbd0ebd",
            "c44cd6a3-da5f-37b2-a94a-e0547523059c",
            "2fb2e8ff-df67-30cd-83a2-0df49474f3fb",
            "d7e1e00b-bd6a-3d37-9d4b-5c6899c97f3f",
            "19b8dbca-9b21-3f3e-a2e5-502a5947ac6e",
            "a31ad69a-2604-3997-98c6-a722eb9245cf",
            "73fc6c32-c8ac-3f22-9a56-bdd50964feb4",
            "ff9c0ffe-5faa-329f-83eb-715e0f49ed2a",
        )
    private val log = logger()

    @Scheduled(initialDelay = 3, fixedDelay = 86400)
    fun republiserBomlo() {
        tilBomlo.forEach {
            sykepengesoknadDAO.finnSykepengesoknad(it).let {
                log.info("Republiserer søknad $it til Bømlo.")
                soknadProducer.produserMelding(
                    sykepengesoknadDTOMapper.mapTilSykepengesoknadDTO(it),
                )
            }
        }
    }
}
