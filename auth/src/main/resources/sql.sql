BEGIN TRANSACTION;
DROP TABLE IF EXISTS  type ;
DROP TABLE IF EXISTS  service ;
DROP TABLE IF EXISTS  authentication ;
DROP TABLE IF EXISTS  certificate ;
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
CREATE TABLE  certificate  (
   id           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
   uuid         TEXT    NOT NULL UNIQUE,
   name         TEXT    NOT NULL,
   answeruuid   TEXT,
   certificate  BLOB    NOT NULL,
   address      TEXT,
   port         INTEGER,
   certport     INTEGER,
   trusted      INTEGER,
   hash text,
  UNIQUE (uuid),
  UNIQUE (certificate)
);
CREATE TABLE  approval  (
   id             INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
   certificateid  INTEGER NOT NULL,
   serviceid      INTEGER NOT NULL,
  FOREIGN KEY ( certificateid ) REFERENCES certificate (id),
  FOREIGN KEY ( serviceid ) REFERENCES service (id),
  UNIQUE (serviceid, certificateid)
);
CREATE INDEX uuidindex
  ON certificate (uuid);
CREATE INDEX ownuuidindex
  ON certificate (answerUuid);
COMMIT;
