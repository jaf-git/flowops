create table pipeline_decision_subject (
    decision_id  uuid        not null references pipeline_decision (id) on delete cascade,
    subject_kind varchar(16) not null,
    subject_id   uuid        not null,

    primary key (decision_id, subject_kind, subject_id),

    constraint pipeline_decision_subject_kind_is_known
        check (subject_kind in ('NODE', 'JOB', 'TEMPLATE'))
);

create index pipeline_decision_subject_by_subject
    on pipeline_decision_subject (subject_id, subject_kind);

alter table pipeline_decision
    add column coverage   numeric(5, 4),
    add column text_score numeric(5, 4),
    add column finalists  jsonb;

comment on column pipeline_decision.coverage is
    'What fraction of the four match features had any data. A 0.8 built on text alone and a 0.8 built on all four are not the same claim.';
comment on column pipeline_decision.text_score is
    'The text term alone, kept because it is the term that vetoes: veto:text_floor is decided on this number and nothing else.';
comment on column pipeline_decision.finalists is
    'The top candidates and their scores, as [{"templateId":…,"score":…}]. What nearly won, which is how an abstention is diagnosed.';
