drop table if exists channel_orders;
drop table if exists channel_cursor;
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

create table orders (
    order_id    bigserial primary key,
    status      text not null check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED', 'BACKORDERED')),
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
                  ('PENDING', 'SUBMITTED', 'IN_PROGRESS', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'FAILED', 'NEEDS_ATTENTION')),
    attempts    integer not null default 0,
    last_error  text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table if not exists channel_cursor (
    id          integer primary key,
    last_seq    bigint not null,
    updated_at  timestamptz not null default now()
);

create table if not exists channel_orders (
    tiangge_order_id        text primary key,
    shop_order_id           bigint,
    lines                   text not null,
    state                   text not null check (state in
                              ('NEW', 'ACCEPTED', 'REJECTED', 'BACKORDERED',
                               'BACKORDER_FILLED', 'BACKORDER_CANCELLED', 'CUSTOMER_CANCELLED')),
    decision                text check (decision in ('ACCEPTED', 'REJECTED', 'BACKORDERED')),
    decision_reason         text,
    decision_reported_at    timestamptz,
    resolution              text check (resolution in ('ACCEPTED', 'CANCELLED')),
    resolution_reported_at  timestamptz,
    cancel_requested_at     timestamptz,
    cancel_restocked        boolean,
    cancel_confirmed_at     timestamptz,
    placed_at               timestamptz,
    decision_deadline       timestamptz,
    decided_at              timestamptz,
    last_error              text,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now()
);

create index if not exists channel_orders_state_idx on channel_orders (state, created_at);
