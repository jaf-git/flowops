alter table process_template
    add column status varchar(12) not null default 'APPROVED',

    add column origin varchar(28) not null default 'AUTHORED',

    add column version integer not null default 1,
    add column supersedes_id uuid references process_template (id),

    add constraint process_template_status_is_known
        check (status in ('DRAFT', 'PROPOSED', 'APPROVED', 'RETIRED')),
    add constraint process_template_origin_is_known
        check (origin in ('AUTHORED', 'COMPOSED_FROM_DISCOVERY', 'CONVERTED_FROM_TASK')),
    add constraint process_template_version_counts_from_one
        check (version >= 1),

    add constraint process_template_supersedes_something_else
        check (supersedes_id is null or supersedes_id <> id);

create index process_template_by_status on process_template (status) where status <> 'APPROVED';

comment on column process_template.status is
    'DRAFT | PROPOSED | APPROVED | RETIRED, mirroring task_template. Everything this pipeline composes '
    'is a DRAFT -- ADR-015: a template is read by everyone and cannot be born from a guess.';
comment on column process_template.origin is
    'Whether a person designed this or a clusterer noticed it. Different amounts of trust are owed to '
    'each, and a reader cannot tell them apart without it.';

alter table step_definition

    add column label text,

    add column lane integer not null default 0,

    add column repeatable boolean not null default false,
    add column typical_repeats numeric(4, 2),

    add constraint step_definition_lane_is_not_negative check (lane >= 0),
    add constraint step_definition_repeats_are_positive
        check (typical_repeats is null or typical_repeats > 0),

    add constraint step_definition_only_a_repeatable_step_repeats
        check (typical_repeats is null or repeatable);

comment on column step_definition.lane is
    'SAME lane means sequential; a DIFFERENT lane means these may run at once. Built inverted once, '
    'which drew zero dependencies and looked like a correct parallel process.';

alter table step_dependency

    add column kind varchar(10) not null default 'CONFIRMED',

    add column confidence numeric(4, 3),

    add column confirmed_by uuid references auth_user (id),
    add column confirmed_at timestamptz,

    add constraint step_dependency_kind_is_known
        check (kind in ('OBSERVED', 'CONFIRMED')),
    add constraint step_dependency_confidence_is_a_fraction
        check (confidence is null or (confidence >= 0 and confidence <= 1)),

    add constraint step_dependency_a_promotion_says_who_and_when
        check ((confirmed_by is null) = (confirmed_at is null));

create index step_dependency_confirmed on step_dependency (template_id, dependent_step_id)
    where kind = 'CONFIRMED';

comment on column step_dependency.kind is
    'OBSERVED means the pipeline saw this order happen; CONFIRMED means a person decided it must. '
    'Only CONFIRMED edges participate in cycle checking and reachability, so an observed edge can '
    'never strand a step. ADR-004.';

create table process_template_evidence (
    process_template_id uuid primary key references process_template (id) on delete cascade,
    pipeline_run_id     uuid references analysis_run (id) on delete set null,

    job_ids             text[] not null default '{}',
    node_ids            text[] not null default '{}',

    certainty           numeric(4, 3) not null,
    order_confidence    numeric(4, 3),

    because             text[] not null default '{}',

    created_at          timestamptz not null,

    constraint process_template_evidence_certainty_is_a_fraction
        check (certainty >= 0 and certainty <= 1),
    constraint process_template_evidence_order_confidence_is_a_fraction
        check (order_confidence is null or (order_confidence >= 0 and order_confidence <= 1)),

    constraint process_template_evidence_names_its_jobs
        check (cardinality(job_ids) > 0)
);

comment on table process_template_evidence is
    'What a composed process was drawn from. A template that cannot say where it came from is an '
    'assertion, and nobody should approve one.';
