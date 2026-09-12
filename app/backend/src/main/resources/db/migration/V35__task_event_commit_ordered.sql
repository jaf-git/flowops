alter table task_event alter column seq drop default;

create function task_event_assign_seq() returns trigger as $$
begin
    -- The key is arbitrary and its only requirement is that every writer of `canvas_event_seq` uses
    -- this same one. A literal rather than `hashtext('canvas_event_seq')`: hashtext is an internal
    -- function with no compatibility promise, and a lock key that silently changed between major
    -- versions would stop two writers from excluding each other while looking entirely healthy.
    perform pg_advisory_xact_lock(8162031);

    -- Assigned unconditionally rather than coalesced with whatever was supplied. The column comment
    -- from V31 says the order is the database's and never the application's, and a writer permitted
    -- to bring its own number is a writer permitted to step outside the lock that orders them.
    new.seq := nextval('canvas_event_seq');
    return new;
end $$ language plpgsql;

create trigger task_event_seq_bi
    before insert on task_event
    for each row execute function task_event_assign_seq();

comment on column task_event.seq is
    'The stream cursor (DECISION-REALTIME-PROTOCOL-01). Assigned by task_event_seq_bi under an advisory lock held to commit, so allocation order is commit order and no committed event can sit behind a cursor that has passed it. Never written by the application. Rolled-back writes burn a number; the hole is never filled and nothing waits for it.';
