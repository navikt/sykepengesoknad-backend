ALTER TABLE SVARALTERNATIV
  DROP CONSTRAINT SVARALTERNATIVER_FK;

DROP TABLE SVARALTERNATIV;
DROP SEQUENCE SVARALTERNATIVER_ID_SEQ;

ALTER TABLE SPORSMAL
  DROP COLUMN VISNINGSKRITERIE;