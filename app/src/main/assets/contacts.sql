BEGIN TRANSACTION;
DROP TABLE IF EXISTS contacts;
DROP TABLE IF EXISTS delta;
DROP TABLE IF EXISTS history;
CREATE TABLE contacts (
  id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  displayname        TEXT    not null,
  displaynamealternative        TEXT,
    displaynameprimitive        TEXT,
      displaynamesource        TEXT,
      image blob
  );

  CREATE TABLE phone (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    contactid integer not null,
    data1        TEXT,
    data2        TEXT,
    data3        TEXT,
    data4        TEXT,
    data5        TEXT,
    data6        TEXT,
    data7        TEXT,
    data8        TEXT,
    data9        TEXT,
    data10        TEXT,
    data11        TEXT,
    data12        TEXT,
    data13        TEXT,
    data14        TEXT,
    data15        TEXT,
    foreign key (contactid) references contacts (id)
    );
    CREATE TABLE email (
      id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
      contactid integer not null,
      data1        TEXT,
      data2        TEXT,
      data3        TEXT,
      data4        TEXT,
      data5        TEXT,
      data6        TEXT,
      data7        TEXT,
      data8        TEXT,
      data9        TEXT,
      data10        TEXT,
      data11        TEXT,
      data12        TEXT,
      data13        TEXT,
      data14        TEXT,
      data15        TEXT,
      foreign key (contactid) references contacts (id)
      );
COMMIT;
