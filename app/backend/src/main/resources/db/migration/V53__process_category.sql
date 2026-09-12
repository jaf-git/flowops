create table process_category (
    id uuid primary key,
    workspace_id uuid not null references workspace (id),
    name varchar(80) not null,
    created_at timestamptz not null,
    constraint process_category_name_not_blank check (length(btrim(name)) > 0)
);

create unique index process_category_name_uk on process_category (workspace_id, lower(btrim(name)));

alter table process_instance
    add column category_id uuid references process_category (id) on delete set null;

create index process_instance_category_ix on process_instance (category_id)
    where category_id is not null;
