DELETE from klipp_metrikk ;

ALTER TABLE klipp_metrikk
    ADD COLUMN eksisterende_sykepengesoknad_id VARCHAR NOT NULL;

ALTER TABLE klipp_metrikk
    ADD COLUMN endring_i_uforegrad VARCHAR NOT NULL;

ALTER TABLE klipp_metrikk
    ADD COLUMN klippet BOOLEAN NOT NULL;

