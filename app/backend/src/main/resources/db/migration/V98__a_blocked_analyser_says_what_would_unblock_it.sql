alter table analyser_precondition

    add column remedy text;

comment on column analyser_precondition.remedy is
    'Imperative: the one act that would satisfy this precondition. Null where it was met, or where the '
    'run predates the column.';
