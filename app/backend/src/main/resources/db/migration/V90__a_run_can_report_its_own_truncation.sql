alter table pipeline_item_stage
    drop constraint pipeline_item_stage_kind_is_known;

alter table pipeline_item_stage
    add constraint pipeline_item_stage_kind_is_known
        check (item_kind in ('NODE', 'JOB', 'BRACKET', 'RUN'));

comment on column pipeline_item_stage.item_kind is
    'NODE, JOB or BRACKET for a thing the pipeline judged; RUN for a statement about the pass itself, '
    'which is how a truncated window reports that it did not read everything (V90).';
