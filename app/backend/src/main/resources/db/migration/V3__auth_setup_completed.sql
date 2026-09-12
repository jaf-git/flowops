alter table auth_user
    add column setup_completed boolean not null default false;
