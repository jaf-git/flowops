create table task_category (
    id uuid primary key,
    workspace_id uuid not null references workspace (id),
    name varchar(80) not null,
    created_at timestamptz not null,
    constraint task_category_name_not_blank check (length(btrim(name)) > 0)
);

create unique index task_category_name_uk on task_category (workspace_id, lower(btrim(name)));

alter table task
    add column category_id uuid references task_category (id) on delete set null;

create index task_category_ix on task (category_id)
    where category_id is not null;
