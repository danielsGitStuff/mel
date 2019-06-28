BEGIN TRANSACTION;
DROP TABLE IF EXISTS  type ;
DROP TABLE IF EXISTS  service ;
DROP TABLE IF EXISTS  authentication ;
DROP TABLE IF EXISTS  certificate ;
DROP TABLE IF EXISTS  approval ;
DROP TABLE IF EXISTS  servicetype ;


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
   active INTEGER  not null,
   lasterror text,
  FOREIGN KEY ( typeid ) REFERENCES servicetype (id)
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
   greeting     TEXT not null,
   trusted      INTEGER,
   hash text,
   wifi text,
  UNIQUE (uuid),
  UNIQUE (certificate)
);
CREATE TABLE  approval  (
   id             INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
   certificateid  INTEGER NOT NULL,
   serviceid      INTEGER NOT NULL,
  FOREIGN KEY ( certificateid ) REFERENCES certificate (id) on delete cascade ,
  FOREIGN KEY ( serviceid ) REFERENCES service (id) on delete cascade ,
  UNIQUE (serviceid, certificateid)
);
CREATE INDEX uuidindex
  ON certificate (uuid);
CREATE INDEX ownuuidindex
  ON certificate (answerUuid);
COMMIT;
