alter table work_node
    add column paired_node_id uuid references work_node (id) on delete set null;

comment on column work_node.paired_node_id is
    'R15.10 - the other end of this START/END pair, written in both directions when a bracket closes.';

create index work_node_pairing on work_node (paired_node_id) where paired_node_id is not null;

update work_node n
set paired_node_id = b.closed_by_node
from work_bracket b
where b.closed_by_node is not null
  and n.id = b.opened_by_node;

update work_node n
set paired_node_id = b.opened_by_node
from work_bracket b
where b.closed_by_node is not null
  and n.id = b.closed_by_node;
