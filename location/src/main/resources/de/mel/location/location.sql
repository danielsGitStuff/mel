BEGIN TRANSACTION;
CREATE TABLE IF NOT EXISTS locations (
  id       INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  tid      TEXT NOT NULL,
  tst      INTEGER NOT NULL,
  lat      REAL NOT NULL,
  lon      REAL NOT NULL,
  alt      INTEGER,
  acc      INTEGER,
  vel      INTEGER,
  batt     INTEGER,
  raw_json TEXT NOT NULL,
  UNIQUE(tid, tst)
);
CREATE INDEX IF NOT EXISTS idx_locations_tst ON locations(tst);
COMMIT;
