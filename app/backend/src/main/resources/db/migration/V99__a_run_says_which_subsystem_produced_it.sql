alter table analysis_run

    add column produced_by varchar(16) not null default 'DISCOVERY',
    add constraint analysis_run_producer_is_known check (produced_by in ('DISCOVERY', 'ANALYSER'));

update analysis_run set produced_by = 'ANALYSER' where reached_stage = 'ANALYSE';

create index analysis_run_by_producer on analysis_run (produced_by, seq desc);

comment on column analysis_run.produced_by is
    'DISCOVERY or ANALYSER. The two subsystems share this table and answer different questions; a read '
    'that does not filter on this renders one pass''s numbers under the other pass''s name.';
