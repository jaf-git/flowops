create table auth_permission (
    name varchar(60) primary key,

    delegable boolean not null
);

create table auth_role (
    name varchar(40) primary key
);

create table auth_role_permission (
    role_name varchar(40) not null references auth_role (name) on delete cascade,
    permission_name varchar(60) not null references auth_permission (name) on delete cascade,
    constraint auth_role_permission_pk primary key (role_name, permission_name)
);

create table auth_user (
    id uuid primary key,
    email varchar(320) not null unique,
    account_state varchar(20) not null,
    role_name varchar(40) not null references auth_role (name),
    created_at timestamptz not null
);

create table auth_user_permission (
    user_id uuid not null references auth_user (id) on delete cascade,
    permission_name varchar(60) not null references auth_permission (name) on delete cascade,
    constraint auth_user_permission_pk primary key (user_id, permission_name)
);

create table auth_credential (
    user_id uuid primary key references auth_user (id) on delete cascade,
    password_hash varchar(255) not null,
    algorithm varchar(30) not null,
    updated_at timestamptz not null
);

create table auth_signup_passcode (
    id uuid primary key,
    email varchar(320) not null,
    code_hash varchar(255) not null,
    issued_at timestamptz not null,
    expires_at timestamptz not null,
    used boolean not null,
    failure_count integer not null
);

create index auth_signup_passcode_email_ix on auth_signup_passcode (email, issued_at desc);

create table auth_session_metadata (
    session_id varchar(36) primary key,
    reference uuid not null unique,
    user_id uuid not null references auth_user (id) on delete cascade,
    ip_address varchar(45),
    device_summary varchar(200),
    coarse_location varchar(100),
    created_at timestamptz not null
);

create index auth_session_metadata_user_ix on auth_session_metadata (user_id);

create table auth_event (
    id uuid primary key,
    action varchar(50) not null,
    actor_user_id uuid,
    target_user_id uuid,
    subject_email varchar(320),
    occurred_at timestamptz not null,
    ip_address varchar(45),
    device_summary varchar(200),
    coarse_location varchar(100)
);

create index auth_event_occurred_ix on auth_event (occurred_at desc);
create index auth_event_actor_ix on auth_event (actor_user_id, occurred_at desc);

create table auth_login_attempt (
    id uuid primary key,
    subject varchar(320) not null,
    subject_kind varchar(20) not null,
    purpose varchar(20) not null,
    attempted_at timestamptz not null
);

create index auth_login_attempt_lookup_ix
    on auth_login_attempt (purpose, subject_kind, subject, attempted_at desc);

insert into auth_permission (name, delegable) values
    ('SESSION_VIEW_OWN', true),
    ('SESSION_END_OWN', true),
    ('CREDENTIAL_REAUTH_OWN', true),
    ('CREDENTIAL_CHANGE_OWN', true),
    ('SESSION_VIEW_ANY', false),
    ('SESSION_TERMINATE_ANY', false);

insert into auth_role (name) values ('OWNER'), ('MANAGER'), ('EMPLOYEE');

insert into auth_role_permission (role_name, permission_name)
select 'OWNER', name from auth_permission;

insert into auth_role_permission (role_name, permission_name)
select roles.name, permissions.name
from (values ('MANAGER'), ('EMPLOYEE')) as roles (name)
cross join (values
    ('SESSION_VIEW_OWN'),
    ('SESSION_END_OWN'),
    ('CREDENTIAL_REAUTH_OWN'),
    ('CREDENTIAL_CHANGE_OWN')) as permissions (name);
