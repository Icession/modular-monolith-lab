-- Lab 3 ONLY: adds supplier_orders WITHOUT touching your Lab 2 tables/data.
-- Use this if you want to keep your existing orders/notifications.
-- (sql/schema.sql rebuilds everything from scratch, including this table.)
create table if not exists supplier_orders (
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
