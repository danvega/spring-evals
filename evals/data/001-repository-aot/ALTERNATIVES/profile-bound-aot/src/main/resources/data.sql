insert into customer (first_name, last_name, active) values ('Ada', 'Lovelace', true);
insert into customer (first_name, last_name, active) values ('Grace', 'Hopper', true);
insert into customer (first_name, last_name, active) values ('Edsger', 'Dijkstra', false);

insert into invoice (customer_id, amount, due_date, paid) values (1, 120.00, '2026-01-15', false);
insert into invoice (customer_id, amount, due_date, paid) values (2, 80.50, '2026-02-01', true);

insert into prospect (company, email) values ('Initech', 'peter@initech.example');
insert into prospect (company, email) values ('Hooli', 'gavin@hooli.example');
insert into prospect (company, email) values ('Initech Partners', 'sales@initech.example');
insert into prospect (company, email) values ('Initech Europe', 'anna@initech.example.eu');
