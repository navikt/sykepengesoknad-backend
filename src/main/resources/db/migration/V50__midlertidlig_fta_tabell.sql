CREATE TABLE fta_peek
(
    id                  VARCHAR PRIMARY KEY DEFAULT uuid_generate_v4(),
    fnr                 VARCHAR                  NOT NULL,
    fom                 DATE                     NOT NULL,
    tom                 DATE                     NOT NULL,
    vedtak              JSONB                    NOT NULL
);

CREATE INDEX fta_peek_fnr_fom_tom_idx ON fta_peek (fnr, fom, tom);
