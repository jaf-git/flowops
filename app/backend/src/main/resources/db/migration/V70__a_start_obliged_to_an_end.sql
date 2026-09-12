alter table job add column project_label varchar(120);

comment on column job.project_label is
    'D1 - the project this job tracks, as a label rather than an entity. Part of the bracket address.';

create table work_bracket (
    id                  uuid primary key,
    job_id              uuid not null references job (id),

    conversation_id     uuid not null,
    counterparty_id     uuid references counterparty (id),
    project_label       varchar(120),
    work_type           varchar(40) not null,
    performer_ref       uuid references auth_user (id),

    opened_by_node      uuid not null,
    closed_by_node      uuid,

    closure_right       uuid not null references auth_user (id),

    state               varchar(10) not null,

    close_kind          varchar(16),

    output_kind         varchar(12),
    output_value        text,

    parent_bracket_id   uuid references work_bracket (id),
    depth               smallint not null default 0,

    continues_bracket_id uuid references work_bracket (id),
    disrupted           boolean not null default false,

    is_boundary         boolean not null default false,

    nudged_at           timestamptz,
    answered_nudge      boolean not null default false,

    opened_at           timestamptz not null,
    closed_at           timestamptz,
    last_activity_at    timestamptz not null,

    constraint work_bracket_state_is_known
        check (state in ('OPEN', 'WAITING', 'CLOSED', 'LAPSED')),

    constraint work_bracket_close_kind_is_known
        check (close_kind is null or close_kind in (
            'DELIVERED', 'DONE', 'DROPPED', 'LAPSED', 'PARENT_CLOSED',
            'OVERRIDE', 'HANDED_OVER', 'CADENCE_CLOSED', 'MERGED')),

    constraint work_bracket_output_kind_is_known
        check (output_kind is null or output_kind in ('TEXT', 'LINK', 'MESSAGE_REF')),

    constraint work_bracket_delivered_carries_its_output
        check (close_kind is distinct from 'DELIVERED'
            or (output_kind is not null and output_value is not null)),

    constraint work_bracket_ending_is_consistent
        check ((state in ('CLOSED', 'LAPSED')) = (close_kind is not null)),

    constraint work_bracket_closed_bracket_has_a_time
        check ((state in ('CLOSED', 'LAPSED')) = (closed_at is not null)),

    constraint work_bracket_depth_is_zero_or_one
        check (depth in (0, 1)),

    constraint work_bracket_handover_is_not_nesting
        check (continues_bracket_id is null or continues_bracket_id <> parent_bracket_id),

    constraint work_bracket_is_not_its_own_parent
        check (id <> parent_bracket_id and id <> continues_bracket_id)
);

create unique index work_bracket_one_open_per_address
    on work_bracket (job_id, conversation_id, counterparty_id, project_label, work_type, performer_ref)
    nulls not distinct
    where state in ('OPEN', 'WAITING');

create index work_bracket_by_job on work_bracket (job_id);
create index work_bracket_by_parent on work_bracket (parent_bracket_id) where parent_bracket_id is not null;
create index work_bracket_by_continues on work_bracket (continues_bracket_id) where continues_bracket_id is not null;
create index work_bracket_by_conversation on work_bracket (conversation_id);

create index work_bracket_unnudged
    on work_bracket (last_activity_at)
    where state in ('OPEN', 'WAITING') and nudged_at is null and is_boundary = false;

create index work_bracket_nudged_awaiting_answer
    on work_bracket (nudged_at)
    where state in ('OPEN', 'WAITING') and nudged_at is not null and answered_nudge = false;

comment on table work_bracket is
    'The unit of observation: a START obliged to an END, keyed by the five-part address. R1.1 is the '
    'partial unique index above, not a service check.';

alter table work_node add column bracket_id uuid references work_bracket (id);

alter table work_node add column node_role varchar(6);

alter table work_node add column conversation_id uuid;

alter table work_node add column work_type varchar(40);

alter table work_node add column marker_id uuid references auth_user (id);

alter table work_node add column evidence_origin varchar(12);

alter table work_node add column parent_node_id uuid references work_node (id);

alter table work_node
    add constraint work_node_role_is_known
        check (node_role is null or node_role in ('START', 'WORK', 'END'));

alter table work_node
    add constraint work_node_evidence_origin_is_known
        check (evidence_origin is null or evidence_origin in ('PRIMARY', 'ADDITIONAL', 'SPLIT', 'SELF_CLOSE'));

alter table work_node
    add constraint work_node_is_not_its_own_parent
        check (id <> parent_node_id);

create index work_node_by_bracket on work_node (bracket_id) where bracket_id is not null;
create index work_node_by_parent on work_node (parent_node_id) where parent_node_id is not null;

create index work_node_bracket_orphans on work_node (job_id) where bracket_id is null and node_role is not null;

create index work_node_performer_chain on work_node (job_id, performer_id, created_at desc)
    where performer_id is not null;

create table work_node_wait (
    id              uuid primary key,
    bracket_id      uuid not null references work_bracket (id),

    kind            varchar(10) not null,

    on_bracket_id   uuid references work_bracket (id),

    reason          text,

    expected_by     timestamptz,

    opened_at       timestamptz not null,

    satisfied_at    timestamptz,
    cancelled_at    timestamptz,

    constraint work_node_wait_kind_is_known
        check (kind in ('CLIENT', 'SUPPLIER', 'COLLEAGUE', 'APPROVAL')),

    constraint work_node_wait_ends_once
        check (satisfied_at is null or cancelled_at is null),

    constraint work_node_wait_does_not_wait_on_itself
        check (bracket_id <> on_bracket_id)
);

create index work_node_wait_open_by_bracket on work_node_wait (bracket_id)
    where satisfied_at is null and cancelled_at is null;

create index work_node_wait_open_on_bracket on work_node_wait (on_bracket_id)
    where on_bracket_id is not null and satisfied_at is null and cancelled_at is null;

create index work_node_wait_overdue on work_node_wait (expected_by)
    where expected_by is not null and satisfied_at is null and cancelled_at is null;

comment on table work_node_wait is
    'R7. Several per bracket; the bracket releases only when all are satisfied, and only a real '
    'completion satisfies one. CLIENT and SUPPLIER never enter a performance figure.';

create table client_artifact (
    id              uuid primary key,
    counterparty_id uuid references counterparty (id),
    job_id          uuid not null references job (id),
    bracket_id      uuid not null references work_bracket (id),

    kind            varchar(12) not null,
    value           text not null,

    conversation_id uuid not null,

    created_at      timestamptz not null,

    constraint client_artifact_kind_is_known
        check (kind in ('TEXT', 'LINK', 'MESSAGE_REF')),

    constraint client_artifact_one_per_bracket unique (bracket_id)
);

create index client_artifact_by_client on client_artifact (counterparty_id, created_at desc)
    where counterparty_id is not null;

create index client_artifact_by_job on client_artifact (job_id);

comment on table client_artifact is
    'R6 - a delivered output, so a terminal end feeds forward without a merge edge. D5 - the value is '
    'readable only by the originating conversation members and the job owner.';

alter table work_bracket
    add constraint work_bracket_opened_by_node_fkey
        foreign key (opened_by_node) references work_node (id);

alter table work_bracket
    add constraint work_bracket_closed_by_node_fkey
        foreign key (closed_by_node) references work_node (id);
