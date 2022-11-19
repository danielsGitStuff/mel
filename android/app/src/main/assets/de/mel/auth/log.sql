drop table if exists "log";
create table "log" (
ord integer,
msg text,
mode text,
timestamp integer default current_timestamp

);
create index logi on log(ord);
