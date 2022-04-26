CREATE TABLE sykepengesoknad
(
    id                       VARCHAR DEFAULT uuid_generate_v4() PRIMARY KEY,
    sykepengesoknad_uuid     VARCHAR                  NOT NULL UNIQUE,
    soknadstype              VARCHAR                  NOT NULL,
    status                   VARCHAR                  NOT NULL,
    fom                      DATE,
    tom                      DATE,
    sykmelding_uuid          VARCHAR,
    aktivert_dato            DATE,
    korrigerer               VARCHAR,
    korrigert_av             VARCHAR,
    avbrutt_dato             DATE,
    arbeidssituasjon         VARCHAR,
    start_sykeforlop         DATE,
    arbeidsgiver_orgnummer   VARCHAR,
    arbeidsgiver_navn        VARCHAR,
    sendt_arbeidsgiver       TIMESTAMP WITH TIME ZONE,
    sendt_nav                TIMESTAMP WITH TIME ZONE,
    sykmelding_skrevet       TIMESTAMP WITH TIME ZONE,
    opprettet                TIMESTAMP WITH TIME ZONE NOT NULL,
    opprinnelse              VARCHAR,
    avsendertype             VARCHAR,
    fnr                      VARCHAR(11),
    egenmeldt_sykmelding     BOOLEAN,
    merknader_fra_sykmelding VARCHAR,
    utlopt_publisert         TIMESTAMP WITH TIME ZONE,
    avbrutt_feilinfo         BOOLEAN
);

CREATE INDEX sykepengesoknad_fnr_index ON sykepengesoknad (fnr);

CREATE TABLE SPORSMAL
(
    id                   VARCHAR DEFAULT uuid_generate_v4() PRIMARY KEY,
    sykepengesoknad_id   VARCHAR NOT NULL REFERENCES sykepengesoknad (id),
    under_sporsmal_id    VARCHAR REFERENCES SPORSMAL (id),
    tekst                VARCHAR,
    undertekst           VARCHAR,
    tag                  VARCHAR NOT NULL,
    svartype             VARCHAR NOT NULL,
    min                  VARCHAR,
    max                  VARCHAR,
    kriterie_for_visning VARCHAR
);


CREATE INDEX sporsmal_soknad_id_index ON sporsmal (sykepengesoknad_id);

CREATE INDEX under_sporsmal_id_index ON sporsmal (under_sporsmal_id);

CREATE TABLE svar
(
    id          VARCHAR DEFAULT uuid_generate_v4() PRIMARY KEY,
    sporsmal_id VARCHAR NOT NULL REFERENCES sporsmal (id),
    verdi       VARCHAR NOT NULL
);

CREATE INDEX svar_sporsmal_id_index ON svar (sporsmal_id);


CREATE TABLE soknadperiode
(
    id                 VARCHAR DEFAULT uuid_generate_v4() PRIMARY KEY,
    sykepengesoknad_id VARCHAR NOT NULL REFERENCES sykepengesoknad (id),
    fom                DATE    NOT NULL,
    tom                DATE    NOT NULL,
    grad               INTEGER NOT NULL,
    sykmeldingstype    VARCHAR
);

CREATE INDEX soknadperiode_soknad_id_index ON soknadperiode (sykepengesoknad_id);


CREATE TABLE dodsmelding
(
    id                   VARCHAR DEFAULT uuid_generate_v4() PRIMARY KEY,
    fnr                  VARCHAR(11)              NOT NULL UNIQUE,
    dodsdato             DATE                     NOT NULL,
    melding_mottatt_dato TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE julesoknadkandidat
(
    id                   VARCHAR DEFAULT uuid_generate_v4() PRIMARY KEY,
    sykepengesoknad_uuid VARCHAR                  NOT NULL UNIQUE,
    opprettet            TIMESTAMP WITH TIME ZONE NOT NULL
);
