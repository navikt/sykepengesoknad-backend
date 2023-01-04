DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'cloudsqliamuser')
        THEN
            GRANT UPDATE ON sykepengesoknad TO cloudsqliamuser;
        END IF;
    END
$$;
