BEGIN TRANSACTION;
DROP TABLE IF EXISTS fsentry;
DROP TABLE IF EXISTS stage;
DROP TABLE IF EXISTS stageset;
DROP TABLE IF EXISTS transfer;
CREATE TABLE fsentry (
  id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  name        TEXT    NOT NULL,
  parentId    INTEGER,
  version     INTEGER NOT NULL,
  contentHash TEXT    NOT NULL,
  dir         INTEGER NOT NULL,
  synced      INTEGER NOT NULL,
  inode       INTEGER UNIQUE,
  modified    INTEGER,
  size        INTEGER,
  FOREIGN KEY (parentId) REFERENCES fsentry (id)
);
CREATE INDEX eversion
  ON fsentry (version);
CREATE INDEX edir
  ON fsentry (parentId, dir);
CREATE INDEX eparent
  ON fsentry (parentId);
CREATE INDEX ehash
  ON fsentry (contentHash);
CREATE INDEX enode
  ON fsentry (inode);
/*staging*/
CREATE TABLE stage (
  id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  parentid    INTEGER,
  fsid        INTEGER,
  fsparentId  INTEGER,
  name        TEXT, -- forget the not null stuff, cause of root directory
  version     INTEGER,
  contentHash TEXT,
  dir         INTEGER NOT NULL,
  inode       INTEGER,
  modified    INTEGER,
  deleted     INTEGER,
  stageset    INTEGER NOT NULL,
  size        INTEGER,
  synced      INTEGER,
  FOREIGN KEY (parentid) REFERENCES stage (id),
  FOREIGN KEY (stageset) REFERENCES stageset (id) ON DELETE CASCADE
);
CREATE TABLE stageset (
  id            INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  type          TEXT,
  origincert    INTEGER,
  originservice TEXT
);
CREATE INDEX sid
  ON stage (id);
CREATE INDEX sversion
  ON stage (version);
CREATE INDEX sdir
  ON stage (parentId, dir);
CREATE INDEX sparent
  ON stage (parentId);
CREATE INDEX shash
  ON stage (contentHash);
CREATE INDEX snode
  ON stage (inode);
CREATE INDEX sstageid
  ON stage (fsparentId);
CREATE INDEX sstageparent
  ON stage (fsid);
CREATE INDEX sssssesion
  ON stage (stageSet);
CREATE TABLE transfer (
  id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  hash        TEXT    NOT NULL,
  certid      INTEGER NOT NULL,
  serviceuuid TEXT    NOT NULL,
  size        INTEGER NOT NULL
);
CREATE TABLE waste (
  name     TEXT    NOT NULL,
  hash     TEXT    NOT NULL UNIQUE PRIMARY KEY,
  deleted  DATETIME,
  modified INTEGER NOT NULL,
  size     INTEGER NOT NULL,
  inode    INTEGER NOT NULL UNIQUE,
  inplace  INTEGER NOT NULL
);
CREATE TRIGGER IF NOT EXISTS stamp1
AFTER INSERT ON waste
BEGIN
  UPDATE waste
  SET deleted = current_timestamp
  WHERE hash = NEW.hash;
END;
CREATE TRIGGER IF NOT EXISTS stamp2
AFTER UPDATE ON waste
BEGIN
  UPDATE waste
  SET deleted = current_timestamp
  WHERE hash = NEW.hash;
END;
CREATE INDEX inodeIndex
  ON waste (inode);
COMMIT;
