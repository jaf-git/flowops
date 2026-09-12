create table job (
    id               uuid primary key,
    name             varchar(200) not null,

    status           varchar(20) not null,
    standing         boolean not null default false,
    opened_at        timestamptz not null,
    closed_at        timestamptz,
    opened_by        uuid not null references auth_user (id),
    last_activity_at timestamptz not null
);

create index job_by_status on job (status);

comment on table job is
    'One engagement. The CONTAINER that groups tracks -- not the case identifier, which is the track. '
    'Created by the one click a human makes to supply the outer boundary (DISCOVERY-OPEN-JOB-01).';

create table track (
    id            uuid primary key,
    job_id        uuid not null references job (id),

    from_role_id  uuid references functional_role (id),
    to_role_id    uuid references functional_role (id),

    performer_id  uuid not null references auth_user (id),

    solo          boolean not null default false,

    key_basis     varchar(20) not null,

    state         varchar(20) not null,
    opened_at     timestamptz not null,
    closed_at     timestamptz,

    close_reason  varchar(20),
    disrupted     boolean not null default false,

    completeness  varchar(12),
    last_activity_at timestamptz not null
);

create index track_by_job on track (job_id);
create index track_by_key on track (job_id, from_role_id, to_role_id, performer_id) where closed_at is null;

alter table track
    add constraint track_completeness_belongs_to_a_closed_track check (
        completeness is null or closed_at is not null
    );

comment on table track is
    'One thread of work inside a job, keyed by a role pair and a performer. THE CASE IDENTIFIER for '
    'this zone -- DECISION-CASE-BOUNDARY-01 keeps the run as the case inside the application, and there '
    'is no run here to supply one.';

create table work_node (
    id                uuid primary key,
    job_id            uuid not null references job (id),

    track_id          uuid references track (id),
    text              text not null,
    detail            text,
    creator_id        uuid not null references auth_user (id),
    creator_role_id   uuid references functional_role (id),
    performer_id      uuid references auth_user (id),
    performer_role_id uuid references functional_role (id),
    created_at        timestamptz not null,
    started_at        timestamptz,
    closed_at         timestamptz,

    state             varchar(16) not null,

    closure           varchar(10),

    direction         varchar(12) not null,

    post_close        boolean not null default false,

    output_type       varchar(12),

    kind              varchar(12) not null
);

create index work_node_by_track on work_node (track_id);
create index work_node_by_job on work_node (job_id);

create index work_node_orphans on work_node (job_id) where track_id is null;

alter table work_node
    add constraint work_node_a_query_never_opens_a_track check (
        direction <> 'QUERY' or track_id is null
    );

comment on column work_node.direction is
    'REQUEST, COMPLETION, STANDALONE or QUERY. QUERY is set by a human at the nudge and is NEVER '
    'inferred from wording -- reading content to classify is refused, and a misread question would '
    'fabricate a duration.';

create table work_node_evidence (
    id           uuid primary key,
    work_node_id uuid not null references work_node (id) on delete cascade,

    message_id   uuid not null references message (id),
    added_at     timestamptz not null,

    origin       varchar(10) not null
);

create index work_node_evidence_by_node on work_node_evidence (work_node_id);

create index work_node_evidence_by_message on work_node_evidence (message_id, origin);

insert into auth_permission (name, delegable) values
    ('WORK_NODE_MARK', false),
    ('WORK_NODE_ASSIGN_SUBJECT', false),
    ('DISCOVERY_CANVAS_VIEW', false),
    ('DISCOVERY_TYPE_CURATE', false);

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'WORK_NODE_MARK'),
    ('MANAGER', 'WORK_NODE_MARK'),
    ('EMPLOYEE', 'WORK_NODE_MARK'),
    ('OWNER', 'WORK_NODE_ASSIGN_SUBJECT'),
    ('MANAGER', 'WORK_NODE_ASSIGN_SUBJECT'),
    ('OWNER', 'DISCOVERY_CANVAS_VIEW'),
    ('MANAGER', 'DISCOVERY_CANVAS_VIEW'),

    ('OWNER', 'DISCOVERY_TYPE_CURATE');
