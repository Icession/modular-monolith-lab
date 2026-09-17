drop table if exists notifications;
drop table if exists order_items;
drop table if exists orders;
drop table if exists inventory;

create table inventory (
    product_id text primary key,
    name        text not null,
    stock       integer not null check (stock >= 0)
);


create table orders (
    order_id    bigserial primary key,
    status      text not null check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED')),
    reason      text,
    created_at  timestamptz not null default now()
);

create table order_items (
    id          bigserial primary key,
    order_id    bigint not null references orders (order_id),
    product_id  text not null,
    quantity    integer not null check (quantity > 0)
);

create table notifications (
    notification_id bigserial primary key,
    message          text not null,
    created_at       timestamptz not null default now()
);

insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do update
    set name = excluded.name,
        stock = excluded.stock;
