do $$
begin
    if exists (select 1 from step_definition) then
        raise exception 'step_definition still holds rows, and this migration deliberately has no back-fill.'
            using hint = 'These steps predate CONSTRAINT-TEMPLATE-FIRST-01 and name their work in free text, '
                         'so there is no task template to point them at. Recreate the database and reseed: '
                         'docker exec flowops-postgres psql -U flowops -d postgres '
                         '-c ''drop database if exists flowops'' -c ''create database flowops owner flowops''';
    end if;
end $$;

alter table step_definition
    add column task_template_id uuid not null references task_template (id);

alter table step_definition drop constraint step_definition_title_not_blank;
alter table step_definition drop column title;
alter table step_definition drop column description;

create index step_definition_task_template_ix on step_definition (task_template_id);

comment on column step_definition.task_template_id is
    'The work this step is. CONSTRAINT-TEMPLATE-FIRST-01: a step definition references a task template '
    'and never carries its own title, so one vocabulary is referenced everywhere rather than retyped.';
comment on column step_definition.expected_duration_hours is
    'How long this process expects to wait on this step (PROCESS_UC_01 extension 2c). Distinct from the '
    'task template''s own estimate, which is how long the work takes; Observe reads the template''s.';
