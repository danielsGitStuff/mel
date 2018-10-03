BEGIN TRANSACTION;
DROP TABLE IF EXISTS fsentry;
DROP TABLE IF EXISTS stage;
DROP TABLE IF EXISTS stageset;
DROP TABLE IF EXISTS transfer;
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
CREATE INDEX edir
  ON fsentry (parentid, dir);
CREATE INDEX eparent
  ON fsentry (parentid);
CREATE INDEX ehash
  ON fsentry (contenthash);
CREATE INDEX enode
  ON fsentry (inode);
/*staging*/
CREATE TABLE stage (
  stageset    INTEGER NOT NULL,
  id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  parentid    INTEGER,
  fsid        INTEGER,
  name        TEXT,
  fsparentid  INTEGER,
  rem         INTEGER,
  version     INTEGER,
  contenthash TEXT,
  dir         INTEGER NOT NULL,
  inode       INTEGER,
  modified    INTEGER,
  deleted     INTEGER NOT NULL,
  size        INTEGER,
  synced      INTEGER,
  merged      INTEGER,
  ord         INTEGER NOT NULL,
  UNIQUE (stageset, ord),
  FOREIGN KEY (parentid) REFERENCES stage (id)
    ON DELETE SET NULL,
  FOREIGN KEY (stageset) REFERENCES stageset (id)
    ON DELETE CASCADE
);
CREATE TABLE stageset (
  id            INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  source        TEXT,
  origincert    INTEGER,
  originservice TEXT,
  status        TEXT,
  created       DATETIME                     DEFAULT (strftime('%s', 'now')),
  version       INTEGER
);
CREATE INDEX sid
  ON stage (id);
CREATE INDEX sversion
  ON stage (version);
CREATE INDEX sdir
  ON stage (parentid, dir);
CREATE INDEX sparent
  ON stage (parentid);
CREATE INDEX shash
  ON stage (contentHash);
CREATE INDEX snode
  ON stage (inode);
CREATE INDEX sstageid
  ON stage (fsparentid);
CREATE INDEX sstageparent
  ON stage (fsid);
CREATE INDEX sssssesion
  ON stage (stageSet);
CREATE TABLE transfer (
  id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  hash        TEXT    NOT NULL,
  certid      INTEGER NOT NULL,
  serviceuuid TEXT    NOT NULL,
  size        INTEGER NOT NULL,
  started     INTEGER NOT NULL,
  transferred INTEGER NOT NULL             DEFAULT 0,
  avail INTEGER,
  f_delete integer,
  UNIQUE (certid, serviceuuid, hash)
);
CREATE TABLE waste (
  id       INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  name     TEXT    NOT NULL,
  hash     TEXT    NOT NULL,
  deleted  DATETIME,
  modified INTEGER NOT NULL,
  size     INTEGER NOT NULL,
  inode    INTEGER NOT NULL UNIQUE,
  inplace  INTEGER NOT NULL,
  f_delete INTEGER NOT NULL
);
CREATE TRIGGER IF NOT EXISTS stamp1
  AFTER
  INSERT
  ON waste
BEGIN
  UPDATE waste
  SET deleted = current_timestamp
  WHERE hash = NEW.hash;
END;
CREATE TRIGGER IF NOT EXISTS stamp2
  AFTER
  UPDATE
  ON waste
BEGIN
  UPDATE waste
  SET deleted = current_timestamp
  WHERE hash = NEW.hash;
END;
CREATE INDEX inodeIndex
  ON waste (inode);
COMMIT;
