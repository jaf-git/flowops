alter table finding_subject drop constraint finding_subject_kind_is_known;

alter table finding_subject
    add constraint finding_subject_kind_is_known
        check (subject_kind in ('NODE', 'BRACKET', 'JOB', 'WAIT', 'TEMPLATE'));

comment on column finding_subject.subject_kind is
    'What the evidence id points at. NODE, BRACKET, JOB and WAIT are the work graph; TEMPLATE is the '
    'library, added by V96 so S6 can name a never-matched template rather than describe it. This is NOT '
    'the same vocabulary as analysis_finding.subject_kind: that says what a finding is ABOUT, this says '
    'what it RESTS ON.';
