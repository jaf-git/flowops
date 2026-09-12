-- The stage gates of work/TEMPLATE_FIRST_01_PLAN.md, asserted against a seeded database.
--
-- Run it through scripts/gates.sh, which owns the connection and turns these rows into an exit code.
-- This file is the assertions and nothing else: what the product must be true about, in SQL.
--
-- **Every gate supplies two numbers and never its own verdict.** A gate names the population it
-- examined and the violations it found; the rule that turns those into PASS or FAIL is stated once,
-- at the bottom, and is the same rule for all of them:
--
--     PASS  ⟺  population > 0  AND  violations = 0
--
-- The `population > 0` half is not decoration. IMPL-SOP-EVIDENCE step 9 has been re-learnt on this
-- project six times by six routes: a check of the form *nothing anywhere does X* passes over an empty
-- set in exactly the words it passes over a clean one. A seed that created nothing would otherwise
-- report every gate green, which is the single most expensive false report available here.
--
-- Adding a gate means adding one `select` to the union with those four columns. Nothing else changes
-- — not the runner, not the verdict rule, not the output format.
--
-- Two names this file gets right that cost the previous session a wrong run (handover §8): the phase
-- table is `task_phase_timer`, not `task_phase`, and the working phase is `ACTIVE`, not `WORK`. A
-- third was caught while rebuilding it: the task's column is `state`, not `status`, and its terminal
-- value is `CLOSED` — there is no `DONE` and no `CANCELLED`.

with

-- Gate 1 · People and reporting tree.
-- Invariant I2: exactly one root, and it is the owner. V6 enforces this with a partial unique index,
-- so the gate's real work is the population — an index over an empty table is satisfied by nothing.
gate_1 as (
    select 'people and reporting tree' as gate,
           count(*) as population,
           case when count(*) filter (where manager_id is null) = 1 then 0 else 1 end as violations
    from workspace_membership
),

-- Gate 2 · No task template is stuck in limbo.
--
-- **It used to require every template to be APPROVED, and that was wrong once the seed grew up.**
-- The full seed drives `AI-INSIGHT-UNUSED-TEMPLATE-01` to a decision: an `insight_decision` row with
-- `decision = 'APPLIED'` retires a template that had no usage at the moment it was judged. RETIRED is
-- therefore *correct demonstrated behaviour*, and a gate calling it a violation would be asserting
-- that a shipped feature must never have been exercised.
--
-- What the gate still means is the thing it was actually for: nothing sits unreachable. DRAFT and
-- PROPOSED are limbo — a draft is somebody's unfinished thought and a proposal is waiting on curation,
-- and neither can be stamped from. In a seeded workspace nothing should be left in either.
-- **Amended 2026-08-23, when drafting shipped and this gate started firing on correct behaviour.**
-- TASKLIB-DRAFT-FROM-TASK-01 means a person typing ad-hoc work now *always* leaves a DRAFT behind:
-- that is the curation queue filling up as designed, not something stuck. The gate went red the
-- first time a free-form task was created in the running product -- which is the reading it was
-- meant to have of the *seed*, and the wrong reading of a workspace somebody has actually used.
--
-- The distinction it now draws: a draft **no task points at** is limbo, because nothing refers to it
-- and nobody is waiting on it. A draft a task points at is doing its job -- it names real work and
-- sits in the owner's queue until curated. Ignoring every draft would have deleted the gate rather
-- than corrected it.
gate_2 as (
    select 'no task template is stuck in limbo' as gate,
           count(*) as population,
           count(*) filter (
               where status in ('DRAFT', 'PROPOSED')
                 and not exists (select 1 from task k where k.template_id = t.id)
           ) as violations
    from task_template t
),

-- Gate 3 · The restriction, asserted as SQL. CONSTRAINT-TEMPLATE-FIRST-01: a step definition
-- references a task template and never carries its own title. V55 makes the column NOT NULL, so a
-- violation here cannot happen while the migration is applied — which makes this a gate about the
-- migration having applied, and about steps existing at all to be constrained.
gate_3 as (
    select 'every step definition names its work' as gate,
           count(*) as population,
           count(*) filter (where task_template_id is null) as violations
    from step_definition
),

-- Gate 4 · A run remembers which work each step is.
-- V57's check constraint holds one direction — a template implies a definition. This holds the
-- other, which no constraint can: that steps cut from a definition actually carry their template.
gate_4 as (
    select 'a run remembers which work each step is' as gate,
           count(*) filter (where definition_id is not null) as population,
           count(*) filter (where definition_id is not null and task_template_id is null) as violations
    from instance_step
),

-- Gate 5 · Closed work has a closed active phase and a history.
-- The TASK-START-01 guarantee, asserted rather than assumed. `ended_at is not null` is the point of
-- it: an open phase means the timer was never stopped, which reads downstream as unbounded work and
-- would quietly poison every duration Observe computes.
closed_task as (
    select id from task where state = 'CLOSED'
),
gate_5 as (
    select 'closed work has a closed active phase' as gate,
           (select count(*) from closed_task) as population,
           (select count(*) from closed_task c
             where not exists (select 1 from task_phase_timer p
                                where p.task_id = c.id
                                  and p.phase_kind = 'ACTIVE'
                                  and p.ended_at is not null)
                or not exists (select 1 from task_state_transition t
                                where t.task_id = c.id)) as violations
),

-- Gate 6 · A process task carries the work its step references.
-- Not one of the plan's original eight, and the one the session's four silent defects argue hardest
-- for. `task.template_id` is the column TemplateUsageReadAdapter reads, and it reported nought
-- instances for templates that 1,124 tasks had come from. Asserted on the column, because an
-- aggregate over nothing is nought and reads exactly like an honest zero.
gate_6 as (
    select 'process tasks carry their template' as gate,
           count(*) as population,
           count(*) filter (where k.template_id is null) as violations
    from instance_step s
    join task k on k.id = s.task_id
    where s.task_template_id is not null
),

-- Gate 7 · Work is genuinely shared across processes.
-- This is the claim the whole template-first change exists to make good: *this work runs in three
-- processes*. The handover records it as written into the seed and never once checked.
--
-- It deliberately does not name "Present to the client" or "Client sign-off". Hardcoding seed titles
-- would make the gate fail when the seed is translated — and the product ships in two languages —
-- while testing nothing structural. What must be true is that some task template is referenced by
-- three or more distinct process templates; which one is a detail of the seed.
sharing as (
    select task_template_id, count(distinct template_id) as process_templates
    from step_definition
    group by task_template_id
),
gate_7 as (
    select 'some work is shared across three processes' as gate,
           (select count(*) from sharing) as population,
           case when (select max(process_templates) from sharing) >= 3 then 0 else 1 end as violations
),

-- Gate 8 · times_used counts every stamp.
-- V58 backfills it, and the handover records the backfill as written and never seeded. The precise
-- claim: any template that actually has tasks stamped from it must report a non-zero count. Templates
-- with no tasks are correctly zero and are not part of the population.
stamped as (
    select t.id, t.times_used, count(k.id) as tasks
    from task_template t
    join task k on k.template_id = t.id
    group by t.id, t.times_used
),
gate_8 as (
    select 'times_used counts every stamp' as gate,
           (select count(*) from stamped) as population,
           (select count(*) from stamped where times_used = 0) as violations
),

-- Gate 9 · No task exists without the work it is.
-- Owner-instructed, 2026-08-22: "there should not be tasks without a template". This is the strongest
-- reading of CONSTRAINT-TEMPLATE-FIRST-01 and the one the product is held to here.
--
-- TICKETs are excluded because the schema forbids them a template outright
-- (`task_ticket_has_no_template` in V48) -- work too small to file is deliberately not library work,
-- and counting it here would make the gate unsatisfiable by construction rather than by defect.
--
-- **This gate measures the product, not the seed.** It is satisfied today because every seeded task is
-- genuinely stamped through POST /api/task-templates/{id}/tasks. It will go red the day a person
-- creates free-form work in the running product, because TASK cannot resolve a template without
-- depending on TASKLIB, which closes a cycle (decision 537). That reddening is the point: it is the
-- open half of unit 5 announcing itself rather than staying invisible.
gate_9 as (
    select 'no task exists without its template' as gate,
           count(*) as population,
           count(*) filter (where template_id is null) as violations
    from task
    where kind = 'TASK'
),

-- Gate 10 · Every run is an instance of a process template.
-- `process_instance.template_id` is NOT NULL in V25, so the violation count cannot rise above zero
-- while the schema stands. The gate is therefore about its population: a workspace with no runs would
-- satisfy the constraint vacuously, and vacuous satisfaction reported as a pass is the failure this
-- whole file is shaped around.
gate_10 as (
    select 'every run instantiates a process template' as gate,
           count(*) as population,
           count(*) filter (where template_id is null) as violations
    from process_instance
),

-- Gate 11 · Nothing still running has been put away.
-- PROCESS-ARCHIVE-INSTANCE-01's one invariant. Archiving removes a run from the operations board,
-- which is the only screen showing work in flight, so archiving a live run would hide it from
-- everybody meant to act on it.
--
-- `V59` holds the same rule as a check constraint, and the two are not redundant: the constraint makes
-- it true of the column whatever writes to it, and this makes it true of the *data as seeded*, which
-- is the thing a person will actually look at. A constraint that has never had a row tested against it
-- and a gate that has never examined a row report identical success.
gate_11 as (
    select 'nothing still running has been archived' as gate,
           count(*) as population,
           count(*) filter (where archived_at is not null and state = 'RUNNING') as violations
    from process_instance
)

-- The verdict rule, stated once for every gate above.
select gate,
       population,
       violations,
       case
           when population = 0 then 'FAIL (nothing examined)'
           when violations > 0 then 'FAIL'
           else 'PASS'
       end as verdict
from (
    select * from gate_1
    union all select * from gate_2
    union all select * from gate_3
    union all select * from gate_4
    union all select * from gate_5
    union all select * from gate_6
    union all select * from gate_7
    union all select * from gate_8
    union all select * from gate_9
    union all select * from gate_10
    union all select * from gate_11
) all_gates
order by gate;

-- Three of the plan's eight stages have no gate here, and their absence is the honest report:
--
--   Free-form tasks proposing candidates  — unit 5's auto-draft half, which the plan lists as not
--       started and handover §9 lists as specced nowhere.
--   Recurring candidates                  — unit 6, clustering, not started. `template_schedule` is
--       the recurring *schedule*, a different table; pointing this gate at it would produce a green
--       about something the gate does not mean.
--   Conversions resolved in one query     — a statement about how many queries run, which nothing in
--       the result set can see. Its home is a round-trip test that counts statements.
--
-- Writing three gates that pass over the wrong tables is worse than writing none.
