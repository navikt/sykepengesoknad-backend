ALTER TABLE frisk_til_arbeid
    ADD COLUMN status VARCHAR NOT NULL;

CREATE INDEX frisk_til_arbeid_status_idx ON frisk_til_arbeid (status);