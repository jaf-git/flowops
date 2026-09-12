alter table work_bracket add column closure_stands_in_for uuid references auth_user (id);

comment on column work_bracket.closure_stands_in_for is
    'R17.1 - whose right this is, when the current holder is standing in for an absent person. Null when '
    'the holder holds it in their own right. A handover never sets it: that bracket belongs to its '
    'successor permanently.';

alter table work_bracket
    add constraint work_bracket_nobody_stands_in_for_themselves
        check (closure_stands_in_for is null or closure_stands_in_for <> closure_right);

create index work_bracket_closure_standing_in
    on work_bracket (closure_stands_in_for)
    where closure_stands_in_for is not null and state in ('OPEN', 'WAITING');
