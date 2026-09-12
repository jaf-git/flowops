#!/usr/bin/env bash
# Run the stage gates in scripts/gates.sql against the seeded database and turn them into an exit code.
#
# **Why this is a script and not a note.** The gates were measured by hand on 2026-08-22, the SQL lived
# in a session scratchpad, and the scratchpad went away — so the next session's first job was to write
# them again from a table in a plan file, and got two names wrong doing it. This repository has learnt
# the same lesson at least four times, in check-disjunction-witnesses.sh and
# check-heartbeat-agrees-with-client.sh among others: a control that depends on somebody remembering
# it fails on the day it matters. Something that exits non-zero cannot be forgotten.
#
# The split of responsibility is deliberate. `gates.sql` is the assertions and knows nothing about
# where the database lives; this file owns the connection, the presentation and the verdict, and knows
# nothing about what is being asserted. Adding a gate touches only the SQL.
#
# There is a third verdict, `N/A (discovery mode)`, and the zone detection that produces it lives here
# rather than in the SQL for the same reason: it is not an assertion about the product, it is a fact
# about which zone the database in front of us belongs to, and the verdict rule is this file's.
#
# Usage:  bash scripts/gates.sh            # against the running demo database
#         bash scripts/gates.sh --quiet    # exit code only
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sql="$here/scripts/gates.sql"

container="${FLOWOPS_PG_CONTAINER:-flowops-postgres}"
database="${FLOWOPS_PG_DATABASE:-flowops}"
user="${FLOWOPS_PG_USER:-flowops}"

quiet=false
[[ "${1:-}" == "--quiet" ]] && quiet=true
say() { $quiet || printf '%s\n' "$*"; }

if ! docker exec "$container" true >/dev/null 2>&1; then
    printf 'NO CONCLUSION: container "%s" is not running. Start the stack, then run this again.\n' "$container" >&2
    # Exit 2, never 1. "I could not tell" and "it failed" are different answers, and collapsing them
    # is the defect IMPL-SOP-EVIDENCE keeps finding by new routes — a gate that cannot run must not be
    # readable as a gate that ran and passed, nor as one that ran and failed.
    exit 2
fi

# -t rows only, -A unaligned, -F| one separator: the SQL stays a pure result set and this file owns how
# it is shown. --no-psqlrc so a developer's own settings cannot change what a gate appears to say.
rows=$(docker exec -i "$container" \
    psql --no-psqlrc -v ON_ERROR_STOP=1 -U "$user" -d "$database" -t -A -F'|' -f - < "$sql" 2>&1)
status=$?

if [[ "$status" -ne 0 ]]; then
    printf 'NO CONCLUSION: psql failed.\n%s\n' "$rows" >&2
    # A gate that errors is a gate that has not run. The previous session's gate 5 named a table and a
    # phase that do not exist, and had it been written to swallow that error it would have reported
    # green over nothing — which is why ON_ERROR_STOP is set and why this is exit 2.
    exit 2
fi

if [[ -z "${rows//[[:space:]]/}" ]]; then
    printf 'NO CONCLUSION: the gate query returned no rows at all.\n' >&2
    exit 2
fi

# **Which zone is this database in?** DISCOVERY_00_OVERVIEW §1 splits the product in two: Discovery
# observes — people and chat and nothing else — while the application executes templates, tasks and
# runs. They share no tables. A database seeded for Discovery alone therefore makes every
# application-zone gate report FAIL (nothing examined), which is each gate's correct answer and a
# useless report in aggregate: a gate that had genuinely broken would sit unnoticed among ten that
# were only ever going to be empty.
#
# Two conditions must both hold, and the pair is what makes this a mode rather than an excuse. There
# are messages, so the database is populated rather than merely empty; and there is no application
# work of *any* of the four kinds, so no gate is being let off a population it should have had. One
# task template anywhere and this is an ordinary database, held to the ordinary standard.
#
# The detection is of the whole database and never of a gate. A gate that could exempt itself is
# worse than a gate that cries wolf, because the exemption is invisible in the row it prints.
mode=$(docker exec "$container" \
    psql --no-psqlrc -v ON_ERROR_STOP=1 -U "$user" -d "$database" -t -A -c "
        select case when (select count(*) from message) > 0
                     and (select count(*) from task_template) = 0
                     and (select count(*) from task) = 0
                     and (select count(*) from process_template) = 0
                     and (select count(*) from process_instance) = 0
                    then 'discovery' else 'application' end" 2>&1)
status=$?
mode="${mode//[[:space:]]/}"

# Same reasoning as the gate query above: a detection that errored has not run, and a runner that
# cannot tell which zone it is in must not guess. Guessing "application" turns a Discovery database
# back into ten expected failures; guessing "discovery" silences every gate at once.
if [[ "$status" -ne 0 || ( "$mode" != discovery && "$mode" != application ) ]]; then
    printf 'NO CONCLUSION: zone detection failed or returned "%s".\n' "$mode" >&2
    exit 2
fi

failures=0
examined=0
skipped=0
say ""
say "$(printf '%-45s %12s %11s   %s' 'GATE' 'POPULATION' 'VIOLATIONS' 'VERDICT')"
say "$(printf '%.0s─' {1..90})"
while IFS='|' read -r gate population violations verdict; do
    [[ -z "$gate" ]] && continue
    # Only an empty gate can be inapplicable, and only in the Discovery zone. A gate that found rows
    # found application work the mode says is not there, so it is answered normally whatever the mode
    # — which is how "people and reporting tree" keeps passing, and keeps being able to fail.
    if [[ "$mode" == discovery && "$population" == 0 ]]; then
        verdict='N/A (discovery mode)'
        skipped=$((skipped + 1))
    else
        examined=$((examined + 1))
        [[ "$verdict" == PASS ]] || failures=$((failures + 1))
    fi
    say "$(printf '%-45s %12s %11s   %s' "$gate" "$population" "$violations" "$verdict")"
done <<< "$rows"
say ""

# The runner asserts its own population for the same reason every gate does. A SQL file edited into
# returning nothing would otherwise report "0 failures" in the words of a clean run.
if [[ $((examined + skipped)) -eq 0 ]]; then
    printf 'NO CONCLUSION: no gate rows were parsed from a non-empty result.\n' >&2
    exit 2
fi

# And nothing applicable at all is the same sentence again, one level up: a run with every gate ruled
# out has examined nothing, and "0 of 0 passed" is exactly the vacuous green this file exists against.
if [[ "$examined" -eq 0 ]]; then
    printf 'NO CONCLUSION: all %s gates were ruled inapplicable; nothing was examined.\n' "$skipped" >&2
    exit 2
fi

if [[ "$skipped" -gt 0 ]]; then
    # Worded differently from the ordinary run on purpose. "All 1 gates passed" over a workspace that
    # holds eleven of them is a true sentence read as a false reassurance; naming the ten that did not
    # apply is the whole point of the mode.
    if [[ "$failures" -gt 0 ]]; then
        say "$failures of $examined applicable gates FAILED — $skipped not applicable (discovery mode)."
        exit 1
    fi
    say "$examined of $examined applicable gates PASSED — $skipped not applicable (discovery mode)."
    exit 0
fi

if [[ "$failures" -gt 0 ]]; then
    say "$failures of $examined gates FAILED."
    exit 1
fi
say "All $examined gates passed."
exit 0
