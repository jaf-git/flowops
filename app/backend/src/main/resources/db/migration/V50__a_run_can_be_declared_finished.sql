alter table process_instance
    add column closure_note text;

alter table process_instance
    add constraint process_instance_ends_one_way
        check (abandoned_reason is null or closure_note is null);

comment on column process_instance.closure_note is
    'Why a run was declared finished with steps still open (PROCESS-CLOSE-INSTANCE-01). Required '
    'whenever it is written, for the reason the abandonment reason is: everybody who held a task in '
    'the run reads it. Null on a run that completed by its last step closing, which needs no '
    'explanation.';
