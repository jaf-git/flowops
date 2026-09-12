alter table analysis_run

    add column nodes_read integer not null default 0,
    add column jobs_read  integer not null default 0,

    add column signature varchar(64),

    add column ai_mode varchar(8) not null default 'OFF',

    add constraint analysis_run_ai_mode_is_known check (ai_mode in ('OFF', 'ON', 'COMPARE'));

comment on column analysis_run.signature is
    'A fingerprint of the configuration this run used. Two runs with the same signature and different '
    'results mean the graph changed; the same results and different signatures mean the rules did.';

create table pipeline_item_stage (
    run_id     uuid        not null references analysis_run (id) on delete cascade,

    item_id    varchar(64) not null,
    item_kind  varchar(12) not null,

    last_stage varchar(12) not null,

    reason     varchar(80) not null,

    primary key (run_id, item_id, item_kind),

    constraint pipeline_item_stage_kind_is_known
        check (item_kind in ('NODE', 'JOB', 'BRACKET')),
    constraint pipeline_item_stage_stage_is_known
        check (last_stage in ('OBSERVE', 'NORMALISE', 'MEASURE', 'DETECT', 'CORRELATE', 'RECOMMEND'))
);

create index pipeline_item_stage_by_run on pipeline_item_stage (run_id, last_stage, reason);

comment on table pipeline_item_stage is
    'Where each item stopped and why. The only table here that records things which produced nothing, '
    'which is what lets the Pipeline screen answer "why is this not in the output?".';

create table pipeline_decision (
    id                    uuid        primary key,
    run_id                uuid        not null references analysis_run (id) on delete cascade,

    finding_kind          varchar(20) not null,

    subject               varchar(64) not null,

    subject_template      varchar(64),

    deterministic_outcome varchar(16) not null,

    score                 numeric(5, 3),
    confidence            numeric(5, 3),
    separation            numeric(5, 3),
    reason                varchar(80),

    ai_outcome            varchar(16),
    ai_reason             varchar(120),
    ai_confidence         numeric(5, 3),

    ai_failed             boolean     not null default false,

    model_id              varchar(60),
    prompt_version        varchar(20),

    cta_shown             boolean     not null default false,

    outcome               varchar(16),
    outcome_at            timestamptz,
    outcome_by            uuid references auth_user (id),

    created_at            timestamptz not null,

    constraint pipeline_decision_finding_kind_is_known
        check (finding_kind in ('NODE_MATCH', 'JOB_MATCH', 'STEP_KIND', 'DRAFT_TEMPLATE', 'DRAFT_PROCESS')),
    constraint pipeline_decision_deterministic_outcome_is_known
        check (deterministic_outcome in ('ABSTAIN', 'OK', 'NUDGE', 'ADJUST', 'ROLE_MISMATCH')),
    constraint pipeline_decision_ai_outcome_is_known
        check (ai_outcome is null or ai_outcome in ('ABSTAIN', 'OK', 'NUDGE', 'ADJUST', 'ROLE_MISMATCH')),
    constraint pipeline_decision_outcome_is_known
        check (outcome is null or outcome in ('ACCEPTED', 'DISMISSED', 'CORRECTED')),

    constraint pipeline_decision_only_a_shown_decision_is_acted_on
        check (outcome is null or cta_shown),

    constraint pipeline_decision_an_outcome_says_who_and_when
        check ((outcome is null) = (outcome_at is null) and (outcome is null) = (outcome_by is null)),

    constraint pipeline_decision_a_failed_call_has_no_verdict
        check (not ai_failed or ai_outcome is null)
);

create index pipeline_decision_by_run on pipeline_decision (run_id, finding_kind);
create index pipeline_decision_by_subject on pipeline_decision (subject, created_at desc);

create index pipeline_decision_answered on pipeline_decision (outcome, created_at desc)
    where outcome is not null;

create index pipeline_decision_disagreements on pipeline_decision (run_id)
    where ai_outcome is not null and ai_outcome <> deterministic_outcome;

comment on table pipeline_decision is
    'One row per decision per run, abstentions included -- precision is measurable from what the '
    'pipeline said, recall only from what it did not. `outcome` is the human''s and the pipeline never '
    'writes it (R12.3).';
