drop trigger if exists trashtrigger;
drop table if exists trash;

create table trash (id integer not null primary key autoincrement, txt text, t integer);
--CREATE TRIGGER trashtrigger AFTER INSERT ON trash BEGIN UPDATE trash SET t = current_timestamp WHERE id = NEW.id; END;
insert into trash values(null,"text",12);

select * from trash;
