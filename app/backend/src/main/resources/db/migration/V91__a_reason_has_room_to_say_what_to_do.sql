alter table pipeline_item_stage
    alter column reason type varchar(240);

comment on column pipeline_item_stage.reason is
    'Why this item stopped where it did. Usually a short code -- gate:no_work_type, veto:text_floor. '
    'The truncation note is a sentence: it states how much of the window was read, the range that was '
    'not, and what to do about it (V91).';
