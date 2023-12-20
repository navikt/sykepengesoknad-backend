ALTER TABLE sykepengesoknad
    ADD COLUMN inntektsopplysninger_ny_kvittering         BOOLEAN,
    ADD COLUMN inntektsopplysninger_innsending_id         VARCHAR,
    ADD COLUMN inntektsopplysninger_innsending_dokumenter VARCHAR;

