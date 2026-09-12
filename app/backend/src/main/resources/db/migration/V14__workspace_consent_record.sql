create table workspace_consent_record (
    id uuid primary key,
    user_id uuid not null references auth_user (id),
    language varchar(8) not null,
    version varchar(64) not null,
    consent_text text not null,
    agreed_at timestamptz not null
);

create index workspace_consent_record_person_ix on workspace_consent_record (user_id, agreed_at desc);
