CREATE TABLE medlemskap_vurdering
(
    id        VARCHAR PRIMARY KEY DEFAULT uuid_generate_v4(),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    svartid   BIGINT                   NOT NULL,
    fnr       VARCHAR(11)              NOT NULL,
    fom       DATE                     NOT NULL,
    tom       DATE                     NOT NULL,
    svartype  VARCHAR                  NOT NULL,
    sporsmal  JSON
)