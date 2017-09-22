BEGIN TRANSACTION;
DROP TABLE IF EXISTS contacts;
DROP TABLE IF EXISTS delta;
DROP TABLE IF EXISTS history;
CREATE TABLE fsentry (
  id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  name        TEXT    NOT NULL,
  parentid    INTEGER,
  version     INTEGER NOT NULL,
  contenthash TEXT    NOT NULL,
  dir         INTEGER NOT NULL,
  synced      INTEGER NOT NULL,
  inode       INTEGER,
  modified    INTEGER,
  size        INTEGER,
  FOREIGN KEY (parentid) REFERENCES fsentry (id)
);
CREATE INDEX eversion
  ON fsentry (version);
COMMIT;
