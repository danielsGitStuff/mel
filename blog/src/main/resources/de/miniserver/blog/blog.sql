create table if not exists blogentry
(
    id        integer not null primary key autoincrement,
    title     text,
    text      text    not null,
    tstamp    integer not null,
    published integer not null default 0
);
create index ttime on blogentry (tstamp);
create table if not exists visits
(
    dayid  integer not null,
    src    text    not null,
    visits integer not null default 1,
    constraint pk primary key (dayid, src)
);
create index dd on visits (dayid);
create index ddd on visits (src);