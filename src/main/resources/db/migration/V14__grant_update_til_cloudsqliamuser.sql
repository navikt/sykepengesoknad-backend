DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'cloudsqliamuser')
        THEN
            GRANT SELECT ON sykepengesoknad TO cloudsqliamuser;
        END IF;
    END
$$;
