alter table analysis_run drop constraint analysis_run_stage_is_known;
alter table analysis_run
    add constraint analysis_run_stage_is_known
        check (reached_stage in ('OBSERVE', 'NORMALISE', 'MEASURE', 'DETECT', 'CORRELATE', 'RECOMMEND', 'ANALYSE'));

alter table analysis_finding drop constraint analysis_finding_stage_is_known;
alter table analysis_finding
    add constraint analysis_finding_stage_is_known
        check (stage in ('OBSERVE', 'NORMALISE', 'MEASURE', 'DETECT', 'CORRELATE', 'RECOMMEND', 'ANALYSE'));

alter table analysis_finding add column category varchar(24);

alter table analysis_finding add column severity varchar(12);
alter table analysis_finding add column confidence varchar(8);

alter table analysis_finding add column reach integer not null default 0;

alter table analysis_finding add column action text;

alter table analysis_finding add column because jsonb;

alter table analysis_finding add column lifecycle varchar(12);
alter table analysis_finding add column first_seen_at timestamptz;

alter table analysis_finding
    add constraint analysis_finding_lifecycle_is_known
        check (lifecycle is null or lifecycle in
               ('NEW', 'STILL_TRUE', 'WORSENING', 'IMPROVING', 'RESOLVED', 'DISMISSED'));

alter table analysis_finding
    add constraint analysis_finding_severity_is_known
        check (severity is null or severity in ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'));

alter table analysis_finding
    add constraint analysis_finding_confidence_is_known
        check (confidence is null or confidence in ('HIGH', 'MEDIUM', 'LOW'));

alter table analysis_finding
    add constraint analysis_finding_reach_is_a_count
        check (reach >= 0);

create index analysis_finding_by_identity
    on analysis_finding (detector, subject_kind, subject_key, created_at desc);

create table analyser_report (
    run_id         uuid        not null references analysis_run (id) on delete cascade,
    analyser       varchar(40) not null,
    items_read     integer     not null default 0,
    findings_count integer     not null default 0,

    failure        text,

    primary key (run_id, analyser),

    constraint analyser_report_counts_are_counts
        check (items_read >= 0 and findings_count >= 0)
);

create table analyser_absence (
    id       uuid         primary key,
    run_id   uuid         not null references analysis_run (id) on delete cascade,
    analyser varchar(40)  not null,

    what     varchar(60)  not null,

    detail   text         not null,

    blocking boolean      not null
);

create index analyser_absence_by_run on analyser_absence (run_id, analyser);

create table analyser_precondition (
    id       uuid        primary key,
    run_id   uuid        not null references analysis_run (id) on delete cascade,
    analyser varchar(40) not null,

    needed   text        not null,
    had      text        not null,
    met      boolean     not null
);

create index analyser_precondition_by_run on analyser_precondition (run_id, analyser);

comment on table analyser_report is
    'One row per analyser per run. items_read is what lets an empty result be told from a broken one: '
    'silence over 400 brackets and silence over 0 brackets are different facts.';

comment on table analyser_absence is
    'What an analyser could not see. Append-only within a run, and the diagnostic the Pipeline screen '
    'renders beside the findings.';
