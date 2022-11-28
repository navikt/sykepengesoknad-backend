DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 FROM pg_user where usename = 'bigquery-dataprodukt')
        THEN
            GRANT SELECT ON svar TO "bigquery-dataprodukt";
            GRANT SELECT ON sporsmal TO "bigquery-dataprodukt";
            GRANT SELECT ON soknadperiode TO "bigquery-dataprodukt";
        END IF;
    END
$$;