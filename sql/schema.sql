-- Run this in the Supabase SQL editor (or via psql) against your project.
-- This recreates the ENTIRE schema from scratch for Lab 2 (multi-item
-- orders, cancellation/restock, notifications). If you're re-running this
-- against an existing Lab 1 database, it will drop and rebuild the
-- inventory/orders tables to match the new shape.

drop table if exists supplier_orders;
drop table if exists notifications;
drop table if exists order_items;
drop table if exists orders;
drop table if exists inventory;

create table inventory (
    product_id text primary key,
    name        text not null,
    stock       integer not null check (stock >= 0)
);

-- An order no longer carries a single product_id/quantity - those moved
-- to order_items, since one order can now have multiple line items.
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

-- Seed data as specified in the lab brief
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do update
    set name = excluded.name,
        stock = excluded.stock;

-- ---------------------------------------------------------------------------
-- Lab 3: supplier module (LegacySupply Anti-Corruption Layer)
-- ---------------------------------------------------------------------------
-- status holds OUR enum (SupplierOrderStatus), never LegacySupply's codes.
-- request_id is fixed when the row is created and reused on every send
-- (X-Request-Id), so a reorder can never become two purchase orders.
drop table if exists supplier_orders;

create table supplier_orders (
    id          bigserial primary key,
    product_id  text not null,
    buyer_ref   text unique,
    request_id  text not null unique,
    po_number   text,
    cases       integer not null check (cases between 1 and 99),
    units       integer not null check (units > 0),
    status      text not null check (status in
                  ('PENDING', 'SUBMITTED', 'IN_PROGRESS', 'SHIPPED', 'DELIVERED', 'FAILED', 'NEEDS_ATTENTION')),
    attempts    integer not null default 0,
    last_error  text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
