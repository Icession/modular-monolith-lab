-- Run this in the Supabase SQL editor (or via psql) against your project.

create table if not exists inventory (
    product_id text primary key,
    name        text not null,
    stock       integer not null check (stock >= 0)
);

create table if not exists orders (
    order_id    bigserial primary key,
    product_id  text not null,
    quantity    integer not null check (quantity > 0),
    status      text not null check (status in ('CONFIRMED', 'REJECTED')),
    reason      text,
    created_at  timestamptz not null default now()
);

-- Seed data as specified in the lab brief
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do update
    set name = excluded.name,
        stock = excluded.stock;
