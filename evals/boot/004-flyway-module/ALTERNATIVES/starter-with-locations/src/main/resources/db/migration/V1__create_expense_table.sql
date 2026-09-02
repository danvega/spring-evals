create table expense (
    id bigint auto_increment primary key,
    description varchar(255) not null,
    category varchar(100) not null,
    amount numeric(10,2) not null
);
