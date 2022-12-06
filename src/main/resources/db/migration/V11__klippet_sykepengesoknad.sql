CREATE TABLE klippet_sykepengesoknad
(
    id                      VARCHAR                     PRIMARY KEY DEFAULT uuid_generate_v4(),
    sykepengesoknad_uuid    VARCHAR                     NOT NULL,
    sykmelding_uuid         VARCHAR                     NOT NULL,
    klipp_variant           VARCHAR                     NOT NULL,
    periode_for             TEXT                        NOT NULL,
    periode_etter           TEXT,
    timestamp               TIMESTAMP WITH TIME ZONE    NOT NULL
)
