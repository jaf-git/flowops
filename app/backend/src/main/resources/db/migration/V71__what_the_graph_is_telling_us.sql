create table analysis_run (
    id              uuid primary key,

    window_from     timestamptz not null,
    window_to       timestamptz not null,

    started_at      timestamptz not null,
    finished_at     timestamptz,

    brackets_read   integer not null default 0,
    waits_read      integer not null default 0,

    reached_stage   varchar(12) not null,

    failure         text,

    constraint analysis_run_stage_is_known
        check (reached_stage in ('OBSERVE', 'NORMALISE', 'MEASURE', 'DETECT', 'CORRELATE', 'RECOMMEND')),

    constraint analysis_run_window_runs_forwards
        check (window_to > window_from)
);

create index analysis_run_recent on analysis_run (started_at desc);

comment on table analysis_run is
    'One pass of the six stages. Stores what it read as well as what it found, so an empty result is '
    'distinguishable from a broken one.';

create table analysis_finding (
    id              uuid primary key,
    run_id          uuid not null references analysis_run (id) on delete cascade,

    detector        varchar(60) not null,
    stage           varchar(12) not null,

    subject_kind    varchar(16) not null,
    subject_key     varchar(200) not null,

    headline        text not null,
    sample_size     integer not null,

    measure         numeric(14, 4),
    measure_unit    varchar(24),

    phrasing        text,
    phrased_by      varchar(12),

    created_at      timestamptz not null,

    constraint analysis_finding_stage_is_known
        check (stage in ('OBSERVE', 'NORMALISE', 'MEASURE', 'DETECT', 'CORRELATE', 'RECOMMEND')),

    constraint analysis_finding_never_keyed_to_a_person
        check (subject_kind in ('WORK_TYPE', 'CLIENT', 'ROLE_PAIR', 'ADDRESS', 'SHAPE', 'JOB', 'WORKSPACE')),

    constraint analysis_finding_states_its_sample
        check (sample_size >= 0),

    constraint analysis_finding_phrased_by_is_known
        check (phrased_by is null or phrased_by in ('TEMPLATE', 'MODEL'))
);

create index analysis_finding_by_run on analysis_finding (run_id);
create index analysis_finding_by_detector on analysis_finding (detector, created_at desc);

comment on table analysis_finding is
    'One thing the graph said. Keyed to work type, client, role pair, address or shape - never to a '
    'person, which the subject_kind check enforces.';

create table finding_subject (
    finding_id      uuid not null references analysis_finding (id) on delete cascade,
    subject_kind    varchar(10) not null,
    subject_id      uuid not null,

    primary key (finding_id, subject_kind, subject_id),

    constraint finding_subject_kind_is_known
        check (subject_kind in ('NODE', 'BRACKET', 'JOB', 'WAIT'))
);

create index finding_subject_reverse on finding_subject (subject_id, subject_kind);

comment on table finding_subject is
    'Bidirectional index between findings and the graph. Read forwards to highlight a canvas; read '
    'backwards to answer "why is this node coloured?".';

create table analysis_recommendation (
    id              uuid primary key,
    run_id          uuid not null references analysis_run (id) on delete cascade,

    finding_id      uuid not null references analysis_finding (id) on delete cascade,

    kind            varchar(32) not null,
    headline        text not null,
    detail          text,

    acted_on_at     timestamptz,
    acted_on_by     uuid references auth_user (id),
    dismissed_at    timestamptz,

    created_at      timestamptz not null,

    constraint analysis_recommendation_kind_is_known
        check (kind in (
            'WRITE_IT_DOWN', 'MOVE_STEP_EARLIER', 'SPLIT_WORK_TYPE',
            'CHANGE_WINDOW', 'ASK_FOR_A_SLOT', 'WORK_HAS_NO_OWNER')),

    constraint analysis_recommendation_decided_once
        check (acted_on_at is null or dismissed_at is null)
);

create index analysis_recommendation_open
    on analysis_recommendation (created_at desc)
    where acted_on_at is null and dismissed_at is null;

comment on table analysis_recommendation is
    'A proposed action with its evidence. The pipeline never acts - R12.3, nothing a human should decide '
    'is auto-created.';
