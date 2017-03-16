BEGIN TRANSACTION;
DROP TABLE IF EXISTS  certificate ;
DROP TABLE IF EXISTS  service ;
drop table if exists servicetype;
DROP TABLE IF EXISTS  approval ;
CREATE TABLE "servicetype" (
   id           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
   type         TEXT    NOT NULL UNIQUE,
   description  TEXT    NOT NULL
);
CREATE TABLE "service" (
   id      INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
   uuid    TEXT    NOT NULL UNIQUE,
   name    TEXT    NOT NULL,
   typeid  INTEGER NOT NULL,
  FOREIGN KEY ( typeid ) REFERENCES type (id)
);
CREATE TABLE  "certificate"  (
   id           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
   uuid         TEXT    NOT NULL UNIQUE,
   name         TEXT    NOT NULL,
   answeruuid   TEXT,
   certificate  BLOB    NOT NULL,
   address      TEXT,
   port         INTEGER,
   certport     INTEGER,
   greeting     TEXT,
   trusted      INTEGER,
   hash text not null,
  UNIQUE (uuid),
  UNIQUE (certificate)
);
CREATE TABLE  approval  (
   id             INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
   certificateid  INTEGER NOT NULL,
   serviceId      INTEGER NOT NULL,
  FOREIGN KEY ( certificateid ) REFERENCES certificate (id),
  FOREIGN KEY ( serviceId ) REFERENCES service (id),
  UNIQUE (serviceId, certificateid)
);
CREATE INDEX uuidindex
  ON certificate (uuid);
CREATE INDEX ownuuidindex
  ON certificate (answerUuid);
COMMIT;
