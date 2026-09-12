create table task_completion_proof (
    id uuid primary key,

    task_id uuid not null unique references task (id),
    note text not null,
    external_link text,
    submitted_at timestamptz not null,
    constraint task_completion_proof_note_not_blank check (length(btrim(note)) > 0)
);

comment on table task_completion_proof is
    'What the assignee says they did. Prose and an optional link, never a file, and never rewritten by erasure.';
