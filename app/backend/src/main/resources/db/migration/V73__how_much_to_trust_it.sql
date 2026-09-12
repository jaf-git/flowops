alter table analysis_recommendation
    add column confidence varchar(16) not null default 'WORTH_LOOKING';

alter table analysis_recommendation
    add constraint analysis_recommendation_confidence_is_known
        check (confidence in ('STRONG', 'WORTH_LOOKING', 'UNDERMINED'));

comment on column analysis_recommendation.confidence is
    'STRONG, WORTH_LOOKING or UNDERMINED. Never a percentage - see the migration note.';
