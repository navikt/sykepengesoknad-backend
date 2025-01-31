ALTER TABLE sykepengesoknad
    ADD COLUMN frisk_til_arbeid_vedtak_id VARCHAR REFERENCES frisk_til_arbeid_vedtak(id);