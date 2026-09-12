create table task_link (
    id uuid primary key,
    task_id uuid not null references task (id) on delete cascade,

    url text not null,

    label varchar(200),

    role varchar(20) not null,

    added_by_user_id uuid not null references auth_user (id),
    added_at timestamptz not null,
    constraint task_link_url_not_blank check (length(btrim(url)) > 0),
    constraint task_link_role_known check (role in ('INPUT', 'OUTPUT', 'REFERENCE'))
);

create index task_link_task_ix on task_link (task_id, role);

create table task_checklist_item (
    id uuid primary key,
    task_id uuid not null references task (id) on delete cascade,

    position integer not null,
    text varchar(500) not null,

    done boolean not null default false,
    done_at timestamptz,
    authored_by_user_id uuid not null references auth_user (id),
    created_at timestamptz not null,
    constraint task_checklist_text_not_blank check (length(btrim(text)) > 0),

    constraint task_checklist_done_has_a_time check ((done and done_at is not null) or (not done and done_at is null))
);

create unique index task_checklist_position_ix on task_checklist_item (task_id, position);
