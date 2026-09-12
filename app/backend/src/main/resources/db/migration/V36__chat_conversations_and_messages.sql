create table conversation (
    id           uuid primary key,
    workspace_id uuid        not null references workspace (id),
    kind         varchar(20) not null,
    created_at   timestamptz not null,

    participant_lo uuid references auth_user (id),
    participant_hi uuid references auth_user (id)
);

alter table conversation add constraint conversation_kind
    check (kind in ('DIRECT', 'ANNOUNCEMENT'));

alter table conversation add constraint conversation_pair_matches_kind
    check ((kind = 'DIRECT') = (participant_lo is not null and participant_hi is not null));

alter table conversation add constraint conversation_pair_is_normalised
    check (participant_lo is null or participant_lo < participant_hi);

create unique index conversation_direct_pair_uk
    on conversation (workspace_id, participant_lo, participant_hi)
    where kind = 'DIRECT';

create unique index conversation_announcement_uk
    on conversation (workspace_id)
    where kind = 'ANNOUNCEMENT';

create table conversation_participant (
    conversation_id      uuid not null references conversation (id) on delete cascade,
    person_id            uuid not null references auth_user (id) on delete cascade,

    last_read_message_id uuid,
    primary key (conversation_id, person_id)
);

create index conversation_participant_person_ix
    on conversation_participant (person_id);

create table message (
    id              uuid primary key,
    conversation_id uuid        not null references conversation (id) on delete cascade,
    author_id       uuid        not null references auth_user (id),
    body            text        not null,
    sent_at         timestamptz not null,
    edited_at       timestamptz,

    deleted_at      timestamptz,

    converted_task_id uuid,
    seq             bigint      not null
);

alter table message add constraint message_body_not_blank
    check (length(btrim(body)) > 0);

create unique index message_converted_task_uk
    on message (converted_task_id)
    where converted_task_id is not null;

create index message_conversation_ix
    on message (conversation_id, seq desc);

create function message_assign_seq() returns trigger as $$
begin
    -- The same literal key `task_event_assign_seq` takes. A different key would let a chat message and
    -- a task event allocate concurrently, which is precisely the interleaving the lock exists to
    -- prevent -- and it would look entirely healthy until a client missed a message.
    perform pg_advisory_xact_lock(8162031);

    -- Assigned unconditionally rather than coalesced with whatever the application supplied. A writer
    -- permitted to bring its own number is a writer permitted to step outside the lock that orders
    -- them.
    new.seq := nextval('canvas_event_seq');
    return new;
end $$ language plpgsql;

create trigger message_seq_bi
    before insert on message
    for each row execute function message_assign_seq();

comment on column message.seq is
    'The stream cursor (DECISION-REALTIME-PROTOCOL-01), drawn from canvas_event_seq and assigned by message_seq_bi under advisory lock 8162031 -- the same lock task_event_assign_seq takes, so allocation order is commit order across both tables. Never written by the application. Rolled-back writes burn a number; the hole is never filled and nothing waits for it.';

comment on table conversation is
    'CHAT_03 section 1. A DIRECT conversation is uniquely keyed by its normalised participant pair; exactly one ANNOUNCEMENT exists per workspace. No administrative read exists over either (DECISION-CHAT-PRIVACY-01).';
