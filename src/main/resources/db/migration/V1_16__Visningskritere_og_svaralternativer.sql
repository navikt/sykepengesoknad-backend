
CREATE TABLE SVARALTERNATIV (
  ID NUMBER(19)    NOT NULL PRIMARY KEY,
  SPORSMAL_ID NUMBER(19),
  VISNINGSKRITERIE VARCHAR(100),
  VERDI VARCHAR(100),
  CONSTRAINT SVARALTERNATIVER_FK FOREIGN KEY (SPORSMAL_ID) REFERENCES SPORSMAL(SPORSMAL_ID)
);

CREATE SEQUENCE SVARALTERNATIVER_ID_SEQ START WITH 1;

ALTER TABLE SPORSMAL ADD VISNINGSKRITERIE VARCHAR2(50);
