alter table pipeline_decision
    alter column subject type text;

comment on column pipeline_decision.subject is
    'What this decision is about, read against finding_kind: a node uuid for NODE_MATCH, a job uuid '
    'for JOB_MATCH, a composed StepKind id for STEP_KIND, and for DRAFT_PROCESS the discovered shape '
    'joined with " -> ". Unbounded because the last two are composed from a variable number of parts '
    'and every fixed width is a crash that has not happened yet (V101).';
