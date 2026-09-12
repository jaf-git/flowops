alter table analysis_finding

    add column subject_name text,

    add column context jsonb;

comment on column analysis_finding.subject_name is
    'What subject_key is called, resolved at write time by WorkName. Null where the run predates the '
    'column or where nobody has named the work.';
comment on column analysis_finding.context is
    'Clients, projects, engagement count, work types and date span the finding rests on, derived from '
    'its own evidence so it is always true of the finding.';
