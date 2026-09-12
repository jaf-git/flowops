create table counterparty (
    id            uuid primary key,
    name          varchar(200) not null,

    kind          varchar(14) not null default 'UNCLASSIFIED',

    classified_by uuid references auth_user (id),
    classified_at timestamptz
);

create index counterparty_by_name on counterparty (lower(name));
create index counterparty_by_kind on counterparty (kind);

comment on table counterparty is
    'Who the work is for. Its KIND changes behaviour rather than describing it, which is why a manager '
    'sets it and an employee cannot (DISCOVERY_03 section 4.5).';

create table counterparty_kind_event (
    id              uuid primary key,
    counterparty_id uuid not null references counterparty (id) on delete cascade,
    from_kind       varchar(14) not null,
    to_kind         varchar(14) not null,
    at              timestamptz not null,
    by_person_id    uuid not null references auth_user (id),

    reason          varchar(12) not null
);

create index counterparty_kind_event_by_counterparty
    on counterparty_kind_event (counterparty_id, at desc);

alter table counterparty_kind_event
    add constraint counterparty_kind_event_moved check (from_kind <> to_kind);

comment on table counterparty_kind_event is
    'Append-only history of a counterparty''s kind. CONVERSION is dated and forward-only; CORRECTION is '
    'retroactive. Treating a conversion as a correction is refused (DISCOVERY_03 section 4.5).';

create table node_phase_row (
    id           uuid primary key,
    work_node_id uuid not null references work_node (id) on delete cascade,

    phase        varchar(16) not null,
    started_at   timestamptz not null,

    ended_at     timestamptz,

    waiting_on   varchar(12)
);

create index node_phase_row_by_node on node_phase_row (work_node_id, started_at);

create index node_phase_row_open on node_phase_row (work_node_id) where ended_at is null;

alter table node_phase_row
    add constraint node_phase_row_waiting_on_belongs_to_a_wait check (
        waiting_on is null or phase in ('EXTERNAL_WAIT', 'INTERNAL_WAIT')
    );

alter table node_phase_row
    add constraint node_phase_row_ends_after_it_starts check (
        ended_at is null or ended_at >= started_at
    );

comment on table node_phase_row is
    'One clock segment of one unit of work. THESE ROWS ARE NEVER SUMMED INTO A SINGLE TOTAL (invariant '
    'I9): a duration is never presented without its phase, and there is deliberately no total column '
    'for anybody to reach for. The EXTERNAL/INTERNAL split is what makes I4 computable -- the '
    'application''s own PhaseKind has one BLOCKED phase and a free-text reason, which cannot answer '
    '"did the client keep us waiting", so Discovery cannot borrow it.';

comment on column node_phase_row.waiting_on is
    'CLIENT, COLLEAGUE, SUPPLIER or APPROVAL, and null outside a wait phase. CLIENT waits are external '
    'and are excluded from every performance figure (I4).';

create table track_handover (
    id             uuid primary key,
    track_id       uuid not null references track (id) on delete cascade,
    from_performer uuid not null references auth_user (id),
    to_performer   uuid not null references auth_user (id),
    at             timestamptz not null,

    cause          varchar(12) not null
);

create index track_handover_by_track on track_handover (track_id, at);

alter table track_handover
    add constraint track_handover_changes_hands check (from_performer <> to_performer);

comment on table track_handover is
    'A thread changing hands. ORDINARY continues it; DEACTIVATION sets track.disrupted and removes the '
    'thread from discovery (DISCOVERY_03 section 4.2).';

create table work_node_step (
    id           uuid primary key,
    work_node_id uuid not null references work_node (id) on delete cascade,

    position     integer not null,
    text         text not null,
    added_by     uuid not null references auth_user (id),
    added_at     timestamptz not null
);

create unique index work_node_step_position_uk on work_node_step (work_node_id, position);

comment on table work_node_step is
    'Employee-written steps of one unit of work. Becomes the task template''s checklist when the node '
    'is formalised (DISCOVERY_03 section 3).';

create table work_edge (
    id                uuid primary key,
    from_node         uuid not null references work_node (id) on delete cascade,
    to_node           uuid not null references work_node (id) on delete cascade,

    kind              varchar(10) not null,

    weight            numeric(6, 4) not null default 1.0,

    observation_count integer not null default 0,

    consistency_ratio numeric(4, 3) not null default 1.0,
    first_seen        timestamptz not null,
    last_seen         timestamptz not null,
    verified_by_owner boolean not null default false
);

create unique index work_edge_pair_uk on work_edge (from_node, to_node, kind);
create index work_edge_by_from on work_edge (from_node);
create index work_edge_by_to on work_edge (to_node);

alter table work_edge
    add constraint work_edge_joins_two_nodes check (from_node <> to_node);

comment on table work_edge is
    'An inferred ordering between two units of work. Promoted to FOLLOWS at the configured consistency '
    'ratio over the configured number of tracks. I1 and I2 -- no edge crosses a track or a job -- are '
    'enforced in the query, never by a score (DISCOVERY_03 section 5).';

comment on column work_edge.observation_count is
    'C5. Counts ordered pairs within one track, never messages and never cycles. Does not decay -- '
    'count answers HOW OFTEN and weight answers HOW CURRENT, and conflating them causes two opposite '
    'bugs (DISCOVERY_03 section 6).';

create table loop (
    id                     uuid primary key,
    job_id                 uuid not null references job (id) on delete cascade,

    member_node_ids        uuid[] not null,

    cycle_count            integer not null default 1,

    exit_condition         varchar(16),

    total_work_ms          bigint not null default 0,
    total_external_wait_ms bigint not null default 0
);

create index loop_by_job on loop (job_id);

comment on table loop is
    'A repeated cycle between units of work, first-class with a counter. Confirmed at the configured '
    'number of cycles. The exit condition is required at close because approved and cancelled are '
    'different outcomes and averaging them is a lie (DISCOVERY_03 section 4.4).';

create table track_type (
    id                   uuid primary key,

    name                 varchar(200),
    from_role_id         uuid references functional_role (id),
    to_role_id           uuid references functional_role (id),
    confirmed_by_owner   boolean not null default false,

    occurrence_count     integer not null default 0,

    terminal_output_type varchar(12),

    status               varchar(12) not null default 'CANDIDATE'
);

create index track_type_by_status on track_type (status);

create index track_type_by_name on track_type (lower(name)) where name is not null;

alter table track_type
    add constraint track_type_named_means_named check (
        (status in ('NAMED', 'PROVISIONAL', 'SUPERSEDED')) = (name is not null)
    );

comment on table track_type is
    'A discovered class of similar threads, reusable across every job. Proposed at the configured '
    'number of completed tracks; named only by the owner (DISCOVERY_03 section 4.6).';

comment on column track_type.occurrence_count is
    'C2. Counts COMPLETED TRACKS matching this type -- never messages, never cycles, never rows. Does '
    'not decay (DISCOVERY_03 section 6).';

alter table work_node
    add column fingerprint                     text,
    add column fingerprint_performer_role_id   uuid references functional_role (id),

    add column fingerprint_median_work_ms      bigint,
    add column fingerprint_output_type         varchar(12),
    add column fingerprint_preceding_role_id   uuid references functional_role (id),
    add column fingerprint_preceding_direction varchar(12),
    add column fingerprint_following_role_id   uuid references functional_role (id),

    add column fingerprint_position_in_track   integer,

    add column weight                          numeric(6, 4) not null default 1.0,

    add column subject_source                  varchar(10),

    add column nudged_at                       timestamptz,
    add column task_template_id                uuid;

create index work_node_by_fingerprint on work_node (fingerprint) where fingerprint is not null;

create index work_node_unnudged on work_node (created_at) where nudged_at is null and closed_at is null;

alter table work_node
    add constraint work_node_fingerprint_position_starts_at_one check (
        fingerprint_position_in_track is null or fingerprint_position_in_track >= 1
    );

alter table work_node
    add constraint work_node_fingerprint_preceding_is_one_component check (
        (fingerprint_preceding_role_id is null) = (fingerprint_preceding_direction is null)
    );

comment on column work_node.fingerprint is
    'The canonical form of the six-component signature (DISCOVERY_03 section 7) -- the key nodes cluster '
    'by. Language-independent by construction. Null until the track closes, because position, following '
    'role and median duration all need the close. The fingerprint_* columns beside it hold the '
    'components, because a canonical string cannot be parsed back without a second definition of the '
    'format.';

comment on column work_node.nudged_at is
    'When the ONE nudge was sent. One per node, ever -- a second nudge teaches people to ignore the '
    'first, and DISCOVERY_07 section 5 refuses to make the count configurable in case somebody tries.';

comment on column work_node.task_template_id is
    'The template this unit of work became, or null. NO FOREIGN KEY, deliberately: Discovery records '
    'that the crossing happened and does not own TASKLIB''s table. A constraint would make those tables '
    'undroppable from this side and turn a one-way crossing into a mutual coupling '
    '(DECISION-DISCOVERY-ZONE-01).';

alter table track
    add column track_type_id       uuid references track_type (id),

    add column continues_track_id  uuid references track (id),
    add column process_template_id uuid;

create index track_by_type on track (track_type_id) where track_type_id is not null;
create index track_by_continuation on track (continues_track_id) where continues_track_id is not null;

comment on column track.continues_track_id is
    'The closed thread this one continues, within the configured continuation window. Outside that '
    'window the work is rework and opens a new job instead (DISCOVERY_07 threshold 5).';

comment on column track.process_template_id is
    'The process template this thread became, or null. NO FOREIGN KEY, for the same reason as '
    'work_node.task_template_id: Discovery records the crossing and does not own PROCESS''s table.';

alter table job

    add column counterparty_id  uuid references counterparty (id),

    add column job_type_id      uuid,

    add column is_rework        boolean not null default false,
    add column rework_of_job_id uuid references job (id);

create index job_by_counterparty on job (counterparty_id) where counterparty_id is not null;
create index job_by_rework_of on job (rework_of_job_id) where rework_of_job_id is not null;

alter table job
    add constraint job_rework_points_somewhere check (
        is_rework = (rework_of_job_id is not null)
    );

comment on column job.job_type_id is
    'The discovered class this engagement belongs to, or null. No foreign key: the job-type table is a '
    'later slice''s and this column exists so that slice needs no migration of its own.';

alter table workspace_settings

    add column discovery_tracks_to_propose_type      integer not null default 5,

    add column discovery_tracks_to_form_candidate    integer not null default 3,

    add column discovery_cycles_to_confirm_loop      integer not null default 3,

    add column discovery_ordering_consistency_ratio  numeric(3, 2) not null default 0.80,
    add column discovery_ordering_min_tracks         integer not null default 3,

    add column discovery_continuation_window_days    integer not null default 7,

    add column discovery_role_coverage_floor_percent integer not null default 60,

    add column discovery_role_degeneracy_percent     integer not null default 60,

    add column discovery_drift_window_completions    integer not null default 5,

    add column discovery_variant_band_low_percent    integer not null default 20,
    add column discovery_variant_band_high_percent   integer not null default 80,

    add column discovery_weight_decay_factor         numeric(3, 2) not null default 0.90,

    add column discovery_pairing_signals_to_auto     integer not null default 4,
    add column discovery_pairing_signals_to_ask      integer not null default 3;

comment on column workspace_settings.discovery_tracks_to_propose_type is
    'DISCOVERY_07 threshold 1. Completed tracks before a discovered type reaches the owner. A GUESS, '
    'not a measurement -- see DISCOVERY_07 section 6 for how it will be calibrated.';

comment on column workspace_settings.discovery_ordering_consistency_ratio is
    'DISCOVERY_07 threshold 4, with discovery_ordering_min_tracks. Below this consistency an observed '
    'order stays an observation and never becomes a FOLLOWS edge.';

comment on column workspace_settings.discovery_weight_decay_factor is
    'DISCOVERY_07 threshold 10. Applied per MISSED EXPECTED OCCURRENCE against the pattern''s own '
    'observed cadence -- never per day, which faded quarterly work below the floor between its own '
    'occurrences.';

comment on column workspace_settings.discovery_pairing_signals_to_auto is
    'DISCOVERY_07 threshold 11, with discovery_pairing_signals_to_ask. Four of four links a completion '
    'to its request silently; three of four asks. A wrong pair fabricates a duration.';
