DROP TABLE IF EXISTS frisk_til_arbeid;
DROP TABLE IF EXISTS frisk_til_arbeid_vedtak;

CREATE TABLE frisk_til_arbeid_vedtak
(
    id                  VARCHAR PRIMARY KEY DEFAULT uuid_generate_v4(),
    vedtak_uuid         VARCHAR                  NOT NULL UNIQUE,
    key                 VARCHAR                  NOT NULL,
    opprettet           TIMESTAMP WITH TIME ZONE NOT NULL,
    fnr                 VARCHAR                  NOT NULL,
    fom                 DATE                     NOT NULL,
    tom                 DATE                     NOT NULL,
    vedtak              JSONB                    NOT NULL,
    behandlet_status    VARCHAR                  NOT NULL,
    behandlet_tidspunkt TIMESTAMP WITH TIME ZONE
);

CREATE INDEX fta_vedtak_uuid_idx ON frisk_til_arbeid_vedtak (vedtak_uuid);

CREATE INDEX fta_fnr_fom_tom_idx ON frisk_til_arbeid_vedtak (fnr, fom, tom);

CREATE INDEX fta_behandlet_status_idx ON frisk_til_arbeid_vedtak (behandlet_status);
