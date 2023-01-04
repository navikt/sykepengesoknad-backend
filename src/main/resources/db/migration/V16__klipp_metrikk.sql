CREATE TABLE klipp_metrikk
(
    id                      VARCHAR                     PRIMARY KEY DEFAULT uuid_generate_v4(),
    sykmelding_uuid         VARCHAR                     NOT NULL,
    variant                 VARCHAR                     NOT NULL,
    soknadstatus            VARCHAR                     NOT NULL,
    timestamp               TIMESTAMP WITH TIME ZONE    NOT NULL
)
