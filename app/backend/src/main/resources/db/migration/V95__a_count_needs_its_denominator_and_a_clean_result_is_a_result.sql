alter table analysis_finding add column reach_of integer;

alter table analysis_finding
    add constraint analysis_finding_reach_fits_its_population
        check (reach_of is null or (reach_of >= 0 and reach <= reach_of));

comment on column analysis_finding.reach_of is
    'What `reach` is out of. Null means the count stands on its own. When present the lifecycle compares '
    'the SHARE rather than the count -- a cumulative count grows with the corpus and would otherwise '
    'read as worsening forever.';

alter table analysis_finding add column times_seen integer not null default 1;

alter table analysis_finding
    add constraint analysis_finding_times_seen_is_a_count
        check (times_seen >= 1);

comment on column analysis_finding.times_seen is
    'How many consecutive runs this finding has appeared in. Reset by WORSENING -- worse than when you '
    'last looked is a new fact. Read by the Phase 6 priority formula, which decays a STILL_TRUE or '
    'IMPROVING finding by 0.8 per appearance after a two-run grace.';

create table analyser_clean (
    id       uuid        primary key,
    run_id   uuid        not null references analysis_run (id) on delete cascade,
    analyser varchar(40) not null,

    what     varchar(60) not null,

    detail   text        not null
);

create index analyser_clean_by_run on analyser_clean (run_id, analyser);

comment on table analyser_clean is
    'What an analyser looked at and found nothing wrong with. NOT an absence: an absence is "I could not '
    'see", this is "I saw, and it is fine". An analyser must emit at least one of a finding, an absence '
    'or a clean result on every run -- enforced in the runner, because all three empty looks exactly '
    'like a healthy silence.';
