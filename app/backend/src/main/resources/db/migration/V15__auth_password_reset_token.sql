create table auth_password_reset_token (
    id uuid primary key,
    user_id uuid not null references auth_user (id) on delete cascade,
    token_hash varchar(255) not null,
    issued_at timestamptz not null,
    expires_at timestamptz not null,
    spent_at timestamptz
);

create index auth_password_reset_token_hash_ix on auth_password_reset_token (token_hash);

create index auth_password_reset_token_user_ix on auth_password_reset_token (user_id, issued_at desc);
