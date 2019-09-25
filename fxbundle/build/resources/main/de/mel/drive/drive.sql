begin TRANSACTION;
DROP TABLE IF EXISTS fsentry;
DROP TABLE IF EXISTS stage;
DROP TABLE IF EXISTS stageset;
DROP TABLE IF EXISTS transfer;
drop table if exists filedist;
drop table if exists filedisttargets;
create table filedist
(
    id            integer not null primary key autoincrement,
    sourcepath    text,
    sourcehash    text    not null,
    sourcedetails text,
    deletesource  integer,
    fsize         integer,
    state         text    not null,
    t             real default current_timestamp
);
create table filedisttargets
(
    id     integer not null primary key autoincrement,
    taskid integer not null,
    tpath  text    not null,
    tfsid  integer,
    foreign key (taskid) references filedist (id) on delete cascade
);
create TABLE fsentry
(
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    parentid    INTEGER,
    version     INTEGER NOT NULL,
    contenthash TEXT    NOT NULL,
    dir         INTEGER NOT NULL,
    synced      INTEGER NOT NULL,
    inode       INTEGER,
    modified    INTEGER,
    created     INTEGER,
    size        INTEGER,
    sym         text,
    FOREIGN KEY (parentid) REFERENCES fsentry (id)
);
create INDEX eversion
    ON fsentry (version);
create INDEX edir
    ON fsentry (parentid, dir);
create INDEX eparent
    ON fsentry (parentid);
create INDEX ehash
    ON fsentry (contenthash);
create INDEX enode
    ON fsentry (inode);
/*staging*/
create TABLE stage
(
    stageset    INTEGER NOT NULL,
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    parentid    INTEGER,
    fsid        INTEGER,
    name        TEXT,
    fsparentid  INTEGER,
    version     INTEGER,
    contenthash TEXT,
    dir         INTEGER,
    sym         text,
    inode       INTEGER,
    modified    INTEGER,
    created     INTEGER,
    deleted     INTEGER NOT NULL,
    size        INTEGER,
    synced      INTEGER,
    merged      INTEGER,
    ord         INTEGER NOT NULL,
    FOREIGN KEY (parentid) REFERENCES stage (id)
        ON DELETE SET NULL,
    FOREIGN KEY (stageset) REFERENCES stageset (id)
        ON DELETE CASCADE
);
create TABLE stageset
(
    id            INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    source        TEXT,
    origincert    INTEGER,
    originservice TEXT,
    status        TEXT,
    created       DATETIME DEFAULT (strftime('%s', 'now')),
    version       INTEGER,
    bv            INTEGER not null
);
create INDEX sid
    ON stage (id);
create INDEX sversion
    ON stage (version);
create INDEX sdir
    ON stage (parentid, dir);
create INDEX sparent
    ON stage (parentid);
create INDEX shash
    ON stage (contentHash);
create INDEX snode
    ON stage (inode);
create INDEX sstageid
    ON stage (fsparentid);
create INDEX sstageparent
    ON stage (fsid);
create INDEX sstagestack
    ON stage (stageset, fsid);
create INDEX sstagelookup1
    ON stage (stageset, parentid);
create INDEX sssssesion
    ON stage (stageSet);
create TABLE transfer
(
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    hash        TEXT    NOT NULL,
    certid      INTEGER NOT NULL,
    serviceuuid TEXT    NOT NULL,
    size        INTEGER NOT NULL,
    state       TEXT    NOT NULL,
    transferred INTEGER NOT NULL DEFAULT 0,
    avail       INTEGER,
    f_delete    integer,
    UNIQUE (certid, serviceuuid, hash)
);
create TABLE missinghash
(
    `hash` TEXT NOT NULL,
    PRIMARY KEY (`hash`)
);
create TABLE waste
(
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
create trigger IF NOT EXISTS stamp1
    after
        insert
    on waste
begin
    update waste
    set deleted = current_timestamp
    where hash = NEW.hash;
end;
create trigger IF NOT EXISTS stamp2
    after
        update
    on waste
begin
    update waste
    set deleted = current_timestamp
    where hash = NEW.hash;
end;
create INDEX inodeIndex
    ON waste (inode);
commit;
