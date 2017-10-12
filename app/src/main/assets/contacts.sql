BEGIN TRANSACTION;
DROP TABLE IF EXISTS contacts;
DROP TABLE IF EXISTS phone;
DROP TABLE IF EXISTS email;
DROP TABLE IF EXISTS phonebook;
CREATE TABLE phonebook (
  id       INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  version  INTEGER,
  deephash TEXT,
  created  INTEGER NOT NULL
);
CREATE TABLE contacts (
  id       INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  aid      INTEGER,
  pid      INTEGER NOT NULL,
  image    BLOB,
  deephash TEXT,
  FOREIGN KEY (pid) REFERENCES phonebook (id)
    ON DELETE CASCADE
);
CREATE TABLE appendix (
  id        INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  contactid INTEGER NOT NULL,
  aid       INTEGER,
  mime      TEXT    NOT NULL,
  data1     TEXT,
  data2     TEXT,
  data3     TEXT,
  data4     TEXT,
  data5     TEXT,
  data6     TEXT,
  data7     TEXT,
  data8     TEXT,
  data9     TEXT,
  data10    TEXT,
  data11    TEXT,
  data12    TEXT,
  data13    TEXT,
  data14    TEXT,
  data15    TEXT,
  FOREIGN KEY (contactid) REFERENCES contacts (id)
    ON DELETE CASCADE
);
CREATE TABLE name (
  id        INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  contactid INTEGER NOT NULL,
  aid       INTEGER,
  data1     TEXT,
  data2     TEXT,
  data3     TEXT,
  data4     TEXT,
  data5     TEXT,
  data6     TEXT,
  data7     TEXT,
  data8     TEXT,
  data9     TEXT,
  data10    TEXT,
  data11    TEXT,
  data12    TEXT,
  data13    TEXT,
  data14    TEXT,
  data15    TEXT,
  FOREIGN KEY (contactid) REFERENCES contacts (id)
    ON DELETE CASCADE
);
CREATE TABLE phone (
  id        INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  contactid INTEGER NOT NULL,
  aid       INTEGER,
  data1     TEXT,
  data2     TEXT,
  data3     TEXT,
  data4     TEXT,
  data5     TEXT,
  data6     TEXT,
  data7     TEXT,
  data8     TEXT,
  data9     TEXT,
  data10    TEXT,
  data11    TEXT,
  data12    TEXT,
  data13    TEXT,
  data14    TEXT,
  data15    TEXT,
  FOREIGN KEY (contactid) REFERENCES contacts (id)
    ON DELETE CASCADE
);
CREATE TABLE email (
  id        INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  contactid INTEGER NOT NULL,
  aid       INTEGER,
  data1     TEXT,
  data2     TEXT,
  data3     TEXT,
  data4     TEXT,
  data5     TEXT,
  data6     TEXT,
  data7     TEXT,
  data8     TEXT,
  data9     TEXT,
  data10    TEXT,
  data11    TEXT,
  data12    TEXT,
  data13    TEXT,
  data14    TEXT,
  data15    TEXT,
  FOREIGN KEY (contactid) REFERENCES contacts (id)
    ON DELETE CASCADE
);
COMMIT;
