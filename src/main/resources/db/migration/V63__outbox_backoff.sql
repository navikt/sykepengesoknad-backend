ALTER TABLE outbox
    ADD COLUMN antall_forsok          INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN neste_forsok_timestamp TIMESTAMP WITH TIME ZONE,
    ADD COLUMN siste_feil             TEXT;
