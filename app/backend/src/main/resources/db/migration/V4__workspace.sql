create table workspace (
    id uuid primary key,

    name varchar(120),

    workspace_use varchar(20),
    singleton boolean not null default true,
    created_at timestamptz not null,
    constraint workspace_is_singleton unique (singleton),
    constraint workspace_singleton_is_true check (singleton)
);

insert into workspace (id, singleton, created_at)
values ('9c8b6f42-1d55-4a0e-9f3a-0b7c5e2d4a10', true, now());

create table workspace_settings (
    id uuid primary key,
    workspace_id uuid not null references workspace (id),

    timezone varchar(64) not null,
    effective_from timestamptz not null,

    effective_to timestamptz
);

create unique index workspace_settings_in_force_ix
    on workspace_settings (workspace_id)
    where effective_to is null;

create index workspace_settings_effective_ix
    on workspace_settings (workspace_id, effective_from desc);

create table workspace_event (
    id uuid primary key,
    action varchar(50) not null,
    actor_user_id uuid,
    workspace_id uuid not null references workspace (id),
    occurred_at timestamptz not null
);

create index workspace_event_occurred_ix on workspace_event (occurred_at desc);
create index workspace_event_actor_ix on workspace_event (actor_user_id, occurred_at desc);
