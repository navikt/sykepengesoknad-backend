CREATE TABLE forskuttering_esyfo
(
    id                        VARCHAR(36) DEFAULT UUID_GENERATE_V4() PRIMARY KEY,
    oppdatert                 TIMESTAMP WITH TIME ZONE NOT NULL,
    narmeste_leder_id         UUID NOT NULL UNIQUE,
    orgnummer                 VARCHAR NOT NULL,
    bruker_fnr                VARCHAR NOT NULL,
    arbeidsgiver_forskutterer BOOLEAN,
    aktiv_fom                 DATE NOT NULL,
    aktiv_tom                 DATE,
    timestamp                 TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX forskuttering_esyfo_bruker_org_idx ON forskuttering_esyfo (bruker_fnr, orgnummer);

CREATE TABLE forskuttering_esyfo_checkpoint
(
    topic                 VARCHAR NOT NULL,
    partition             INTEGER NOT NULL,
    last_processed_offset BIGINT NOT NULL DEFAULT -1,
    PRIMARY KEY (topic, partition)
);
