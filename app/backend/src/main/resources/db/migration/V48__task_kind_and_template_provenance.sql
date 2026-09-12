create extension if not exists pg_trgm;
create extension if not exists unaccent;

alter table task

    add column kind varchar(10) not null default 'TASK',

    add column template_id uuid;

alter table task
    add constraint task_kind check (kind in ('TASK', 'TICKET'));

alter table task
    add constraint task_ticket_has_no_template check (kind <> 'TICKET' or template_id is null);

create index task_template_provenance_ix on task (template_id) where template_id is not null;

comment on column task.kind is
    'TASK for work worth filing, TICKET for work too small to file (TASK-CREATE-TICKET-01). Both are '
    'tasks in every way the product measures; the word decides how much ceremony the screens ask for.';
comment on column task.template_id is
    'The library entry this task was stamped from (TASKLIB-USE-TEMPLATE-01), or null when it was written '
    'from scratch. Unconstrained on purpose: TASKLIB owns that row and retiring it must not touch this one.';
