create table task (
    id bigint auto_increment primary key,
    title varchar(255) not null,
    completed boolean not null default false
);

insert into task (title, completed) values ('Write release notes', false);
insert into task (title, completed) values ('Review dependency upgrades', true);
insert into task (title, completed) values ('Ship taskhub 2.0', false);
