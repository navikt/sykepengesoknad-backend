CREATE TABLE frisk_til_arbeid
(
    id            VARCHAR PRIMARY KEY DEFAULT uuid_generate_v4(),
    timestamp     TIMESTAMP WITH TIME ZONE NOT NULL,
    fnr           VARCHAR(11)              NOT NULL,
    fom           DATE                     NOT NULL,
    tom           DATE                     NOT NULL,
    begrunnelse   VARCHAR                  NOT NULL,
    vedtak_status JSON
);

CREATE INDEX frisk_til_arbeid_fnr_fom_tom_idx ON frisk_til_arbeid (fnr, fom, tom);