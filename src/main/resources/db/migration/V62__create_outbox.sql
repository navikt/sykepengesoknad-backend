CREATE TABLE outbox
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type                TEXT                     NOT NULL,
    fnr                 TEXT                     NOT NULL,
    payload             JSONB                    NOT NULL,
    opprettet_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    sendt_timestamp     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX OUTBOX_USENDT_OPPRETTET_IDX ON outbox (opprettet_timestamp, id) WHERE sendt_timestamp IS NULL;
CREATE INDEX OUTBOX_USENDT_FNR_IDX ON outbox (fnr) WHERE sendt_timestamp IS NULL;
