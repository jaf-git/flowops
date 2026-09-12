alter table work_node add column enriched_by uuid references auth_user (id);

comment on column work_node.enriched_by is
    'Who first described this work. Any marker or performer may fill an EMPTY enrichment field; only '
    'this person may CHANGE one that already has an answer, and only while the node is open. Never '
    'moved once set -- see V94. work_type is not enrichable at all.';
