

CREATE TABLE vedtaksperiode_behandling
(
    id                             VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    opprettet_database             TIMESTAMP WITH TIME ZONE NOT NULL,
    oppdatert_database             TIMESTAMP WITH TIME ZONE NOT NULL,
    siste_spleisstatus             VARCHAR                  NOT NULL,
    siste_spleisstatus_tidspunkt   TIMESTAMP WITH TIME ZONE NOT NULL,
    vedtaksperiode_id              VARCHAR(36)              NOT NULL,
    behandling_id                  VARCHAR(36)              NOT NULL UNIQUE,
    CONSTRAINT unique_vedtaksperiode_id_behandling_id UNIQUE (vedtaksperiode_id, behandling_id)

);

CREATE TABLE vedtaksperiode_behandling_sykepengesoknad
(
    id                           VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    vedtaksperiode_behandling_id VARCHAR(36) NOT NULL REFERENCES vedtaksperiode_behandling (id),
    sykepengesoknad_uuid         VARCHAR(36) NOT NULL,
    CONSTRAINT unique_vedtaksperiode_behandling_sykepengesoknad UNIQUE (vedtaksperiode_behandling_id, sykepengesoknad_uuid)
);

CREATE TABLE vedtaksperiode_behandling_status
(
    id                           VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY,
    vedtaksperiode_behandling_id VARCHAR(36)              NOT NULL REFERENCES vedtaksperiode_behandling (id),
    opprettet_database           TIMESTAMP WITH TIME ZONE NOT NULL,
    tidspunkt                    TIMESTAMP WITH TIME ZONE NOT NULL,
    status                       VARCHAR                  NOT NULL
);


CREATE INDEX vbs_vedtaksperiode_behandling_id_idx
    ON vedtaksperiode_behandling_sykepengesoknad (vedtaksperiode_behandling_id);

CREATE INDEX vbs_sykepengesoknad_uuid_idx
    ON vedtaksperiode_behandling_sykepengesoknad (sykepengesoknad_uuid);

CREATE INDEX vbst_vedtaksperiode_behandling_id_idx
    ON vedtaksperiode_behandling_status (vedtaksperiode_behandling_id);
