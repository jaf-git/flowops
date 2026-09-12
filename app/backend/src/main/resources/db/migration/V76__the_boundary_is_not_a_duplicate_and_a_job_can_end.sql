drop index work_bracket_one_open_per_address;

create unique index work_bracket_one_open_per_address
    on work_bracket (job_id, conversation_id, counterparty_id, project_label, work_type, performer_ref)
    nulls not distinct
    where state in ('OPEN', 'WAITING') and is_boundary = false;

comment on index work_bracket_one_open_per_address is
    'R1.1 - exactly one open bracket per address, as a constraint rather than a service check. R4a.1 - '
    'the boundary is excluded, because it is the job container and real work opens beside it at the '
    'same address rather than joining it.';

alter table work_bracket drop constraint work_bracket_close_kind_is_known;

alter table work_bracket
    add constraint work_bracket_close_kind_is_known
        check (close_kind is null or close_kind in (
            'DELIVERED', 'DONE', 'DROPPED', 'LAPSED', 'PARENT_CLOSED',
            'OVERRIDE', 'HANDED_OVER', 'CADENCE_CLOSED', 'MERGED', 'JOB_END'));

alter table job add column close_reason text;

comment on column job.close_reason is
    'R16.4 - mandatory on a force close, and the constraint below enforces it. Null on every other '
    'closing path, because a job that ended normally ended for the ordinary reason.';

alter table job add column shape_eligible boolean not null default true;

comment on column job.shape_eligible is
    'R15.5 - false when any bracket ended in an unknown outcome (LAPSED, PARENT_CLOSED) or the job was '
    'force-closed. A known outcome, even a negative one, is evidence; an unknown one is a hole.';

alter table job
    add constraint job_status_is_known
        check (status in (
            'OPEN', 'STANDING', 'DORMANT', 'CLOSED', 'READY_TO_CLOSE', 'AUTO_CLOSED', 'FORCE_CLOSED'));

alter table job
    add constraint job_a_forced_ending_says_why
        check (status <> 'FORCE_CLOSED' or close_reason is not null);

alter table job
    add constraint job_a_forced_ending_teaches_nothing
        check (status <> 'FORCE_CLOSED' or shape_eligible = false);

alter table job
    add constraint job_an_ended_engagement_has_a_time
        check ((status in ('CLOSED', 'AUTO_CLOSED', 'FORCE_CLOSED')) = (closed_at is not null));
