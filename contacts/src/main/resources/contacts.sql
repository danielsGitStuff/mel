BEGIN TRANSACTION;
DROP TABLE IF EXISTS contacts;
DROP TABLE IF EXISTS phone;
DROP TABLE IF EXISTS email;
DROP TABLE IF EXISTS phonebook;
create table phonebook(
id integer not null primary key autoincrement,
version integer,
deephash text,
created integer not null
);
CREATE TABLE contacts (
  id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
   aid integer,
   pid integer not null,
      image blob,
      deephash text,
      foreign key (pid) references phonebook(id) on delete cascade
  );
  CREATE TABLE name (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    contactid integer not null,
    aid integer,
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
    foreign key (contactid) references contacts (id) on delete cascade
    );
  CREATE TABLE phone (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    contactid integer not null,
    aid integer,
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
    foreign key (contactid) references contacts (id) on delete cascade
    );
    CREATE TABLE email (
      id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
      contactid integer not null,
       aid integer,
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
      foreign key (contactid) references contacts (id) on delete cascade
      );
COMMIT;
