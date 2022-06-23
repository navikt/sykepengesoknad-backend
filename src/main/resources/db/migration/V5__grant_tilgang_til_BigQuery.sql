DO
$$
BEGIN
        IF EXISTS
            (SELECT 1 FROM pg_user where usename = 'bigquery-dataprodukt')
        THEN
            GRANT SELECT ON sykepengesoknad TO "bigquery-dataprodukt";
END IF;
END
$$;