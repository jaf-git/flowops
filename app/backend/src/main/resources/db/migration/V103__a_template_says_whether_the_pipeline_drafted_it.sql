alter table task_template
    add column discovered_by_pipeline boolean not null default false;

comment on column task_template.discovered_by_pipeline is
    'True where NODE_PIPELINE drafted this entry from observed work. False where a person wrote it.';

update task_template
   set discovered_by_pipeline = true
 where keywords is not null
   and array_length(keywords, 1) > 0;
