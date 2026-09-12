create table conversation_shelf (
    id              uuid primary key,
    conversation_id uuid not null,

    kind            varchar(12) not null,
    value           text not null,

    label           varchar(200),

    placed_by       uuid not null references auth_user (id),
    placed_at       timestamptz not null,

    removed_at      timestamptz,

    constraint conversation_shelf_kind_is_known
        check (kind in ('TEXT', 'LINK', 'MESSAGE_REF'))
);

create index conversation_shelf_current
    on conversation_shelf (conversation_id, placed_at desc)
    where removed_at is null;

comment on table conversation_shelf is
    'References a conversation keeps to hand. R11 - TEXT, LINK or MESSAGE_REF, never a file. Readable by '
    'exactly the people who can read the thread.';
