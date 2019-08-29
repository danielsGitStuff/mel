create table  if not exists blogentry
(
    id integer not null  primary key autoincrement,
    title text,
    text text not null,
    tstamp integer not null,
    published integer not null default 0
);