CREATE TABLE aktivering_retry_count
(
    sykepengesoknad_uuid VARCHAR DEFAULT uuid_generate_v4() PRIMARY KEY,
    retry_count          INTEGER                  NOT NULL,
    first_retry          TIMESTAMP WITH TIME ZONE NOT NULL,
    last_retry           TIMESTAMP WITH TIME ZONE NOT NULL
);