DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'cloudsqliamuser')
        THEN
            REVOKE UPDATE ON sykepengesoknad FROM cloudsqliamuser;
        END IF;
    END
$$;
