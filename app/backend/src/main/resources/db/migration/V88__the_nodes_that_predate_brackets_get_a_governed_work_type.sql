update work_node n
   set work_type = case r.name
                       when 'Content writer' then 'CONTENT'
                       when 'Ads specialist' then 'ADS'
                       when 'Designer'       then 'DESIGN'
                   end
  from functional_role r
 where r.id = n.performer_role_id
   and n.work_type is null
   and r.name in ('Content writer', 'Ads specialist', 'Designer');

comment on column work_node.work_type is
    'The governed kind of work, from work_type_vocabulary. Written by WorkBracketAdapter; V88 '
    'backfilled the pre-bracket rows from the performer role where that mapping is unambiguous. '
    'Rows whose role maps to several codes -- Account manager -- are deliberately still null and fall '
    'back to the role name in StepDiscovery.signatureOf, which mixes vocabularies and is the reason '
    'this backfill exists.';
