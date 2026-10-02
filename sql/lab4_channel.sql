alter table orders drop constraint if exists orders_status_check;
alter table orders add constraint orders_status_check
    check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED', 'BACKORDERED'));

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
