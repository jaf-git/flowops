insert into auth_role_permission (role_name, permission_name)
values ('OWNER', 'WORKSPACE_CONFIGURE');

alter table workspace_settings

    add column working_days varchar(7) not null default 'YYYYYNN',
    add column working_hours_start time not null default '09:00',
    add column working_hours_end time not null default '17:00',

    add column at_risk_window_hours integer not null default 24,

    add column escalation_intervals_hours varchar(120) not null default '24,72,168',

    add column quiet_hours_start time not null default '22:00',
    add column quiet_hours_end time not null default '06:00',

    add column invitation_approval_required boolean not null default false;

create table workspace_settings_change (
    id uuid primary key,
    event_id uuid not null references workspace_event (id),
    field varchar(60) not null,

    old_value varchar(200),
    new_value varchar(200) not null,
    constraint workspace_settings_change_moved check (old_value is distinct from new_value)
);

create index workspace_settings_change_event_ix on workspace_settings_change (event_id);

comment on table workspace_settings_change is
    'One row per field that actually moved. A settings change with no rows here did not change anything.';
