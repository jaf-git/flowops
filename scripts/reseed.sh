#!/usr/bin/env bash
# Bring the local installation to a known, freshly seeded state, and prove it landed.
#
# **Why this exists.** The reseed is five commands in a handover, one of which is a `drop database`
# and one of which is a Maven run whose completion is a line in a log. Typed by hand it takes a
# session's attention for several minutes and goes wrong in ways that are quiet: a seed that refused
# because the workspace was not empty looks, from the outside, exactly like a seed that worked.
#
# It composes rather than reimplements. `preflight.sh` decides whether it is safe to start, `gates.sh`
# decides whether the result is correct, and this file owns only the sequence between them: destroy,
# start, wait, verify, stop. Adding an assertion about the seed means editing `gates.sql` and nothing
# here.
#
# Usage:  bash scripts/reseed.sh --yes              # wipe, reseed, run the gates, stop
#         bash scripts/reseed.sh --yes --thin       # the small history, for a fast loop
#         bash scripts/reseed.sh --yes --keep-running   # leave the app up to click around
#
# **It stops the application when it finishes, and that default is deliberate.** A left-over JVM is
# exactly what `preflight.sh` refuses to start a suite beside, so a script that quietly leaves one
# running would make the next `suite.sh` refuse for a reason nobody would connect to this. Ask for
# `--keep-running` when you want the product up.
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backend="$here/app/backend"

container="${FLOWOPS_PG_CONTAINER:-flowops-postgres}"
database="${FLOWOPS_PG_DATABASE:-flowops}"
user="${FLOWOPS_PG_USER:-flowops}"
mailpit="${FLOWOPS_MAILPIT_ROOT:-http://localhost:8025}"

# Minutes, not seconds. The seed drives every task through the product's own endpoints on purpose --
# that is what makes the history real rather than manufactured -- and the full run takes a while.
seed_timeout="${FLOWOPS_SEED_TIMEOUT:-1500}"

assume_yes=false
keep_running=false
thin=false
for arg in "$@"; do
    case "$arg" in
        --yes|-y) assume_yes=true ;;
        --keep-running) keep_running=true ;;
        --thin) thin=true ;;
        *) printf 'unknown argument: %s\n' "$arg" >&2; exit 2 ;;
    esac
done

log_file="$here/app/backend/target/reseed.log"

step() { printf '\n== %s ==\n' "$*"; }
fail() { printf '\n%s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------------------------
# 1 · Is it safe to start, and is Docker up? Asked of preflight, which owns that question.
# ---------------------------------------------------------------------------------------------
step "preflight"
bash "$here/scripts/preflight.sh" --start-docker || fail "Refusing to reseed. Clear the above first."

# ---------------------------------------------------------------------------------------------
# 2 · Look at what is about to be destroyed before destroying it.
#
# IMPL-SOP-DESTROY: a database is not disposable because it is local. The counts are printed rather
# than assumed because the person running this may not be the person who last seeded it, and a
# history somebody is mid-demonstration with looks identical from the command line to a stale one.
# ---------------------------------------------------------------------------------------------
step "what will be destroyed"
if docker exec "$container" true >/dev/null 2>&1; then
    docker exec -i "$container" psql --no-psqlrc -U "$user" -d "$database" -t -A -F' ' -c "
        select 'people', count(*) from workspace_membership
        union all select 'task templates', count(*) from task_template
        union all select 'process runs', count(*) from process_instance
        union all select 'tasks', count(*) from task;" 2>/dev/null \
        || printf 'no readable database yet — nothing to lose\n'
else
    fail "The container \"$container\" is not running, and preflight said Docker was up. Start the stack."
fi

if ! $assume_yes; then
    # No interactive prompt. This runs under agents and CI as often as under a person, and a read that
    # silently sees EOF would be indistinguishable from a person typing yes -- which, for a command
    # whose first act is `drop database`, is the wrong way to be wrong.
    fail "Refusing to drop the database without --yes. Re-run with --yes once the list above is acceptable."
fi

# ---------------------------------------------------------------------------------------------
# 3 · Drop and recreate.
#
# Drop-and-recreate rather than truncate, and the seeder's own refusal message explains why: emptying
# the tables would also empty the roles and permissions the migrations own, leaving a schema in which
# nobody can sign up. Flyway rebuilds all of it from V1 on the next start.
# ---------------------------------------------------------------------------------------------
step "dropping and recreating \"$database\""
docker exec -i "$container" psql --no-psqlrc -v ON_ERROR_STOP=1 -U "$user" -d postgres \
    -c "drop database if exists $database" \
    -c "create database $database owner $user" \
    || fail "Could not recreate the database. An open connection will block the drop -- stop the app first."

step "clearing the mailbox"
# The seed reads verification codes out of Mailpit, so yesterday's codes are a source of confusion
# rather than of harm. Failure here is reported and not fatal: a stale mailbox costs clarity, not
# correctness, and refusing the whole reseed over it would be out of proportion.
curl -fsS -X DELETE "$mailpit/api/v1/messages" >/dev/null 2>&1 \
    && printf 'mailbox cleared\n' \
    || printf 'could not clear the mailbox at %s — continuing, but old codes remain\n' "$mailpit"

# ---------------------------------------------------------------------------------------------
# 4 · Start the application on the demo profile and let it seed.
# ---------------------------------------------------------------------------------------------
step "starting the backend (demo profile) and seeding"
mkdir -p "$(dirname "$log_file")"
: > "$log_file"

run_args=(-o spring-boot:run -Dspring-boot.run.profiles=demo)
$thin && run_args+=(-Dspring-boot.run.arguments=--thin)

( cd "$backend" && ./mvnw "${run_args[@]}" >"$log_file" 2>&1 ) &
maven_pid=$!
printf 'maven pid %s, log %s\n' "$maven_pid" "$log_file"

# Stopping is its own function because it has to happen on success, on failure and on interrupt, and
# because a kill is a claim like any other (IMPL-SOP-42): `taskkill` returning cleanly means the signal
# was sent, not that the tree died. Maven's forked JVM is not the process the wrapper owns, so the
# death is verified rather than assumed -- an orphan here is what makes the *next* run mysterious.
live_jvms() { ps -W 2>/dev/null | grep -c "hotspot/bin/java"; }

stop_backend() {
    [[ -n "${maven_pid:-}" ]] || return 0
    kill "$maven_pid" 2>/dev/null

    # **Kill the JVMs, not the wrapper — this function reported a false success once already.**
    #
    # The first version killed `$maven_pid` and then waited for `kill -0 $maven_pid` to fail. It did
    # fail, promptly, and the function printed "backend stopped after 1s" — while the forked Spring Boot
    # JVM ran on for eleven more minutes and made the next reseed's preflight refuse. `$maven_pid` is
    # the subshell running the wrapper; the application is its grandchild, and the wrapper dying tells
    # you nothing about the grandchild.
    #
    # So the population that gets killed and the population that gets verified are both the JVMs
    # themselves. `preflight` guarantees none were running before this script started, so every one of
    # them is ours.
    local pid
    for pid in $(ps -W 2>/dev/null | grep "hotspot/bin/java" | awk '{print $4}'); do
        MSYS_NO_PATHCONV=1 taskkill /PID "$pid" /T /F >/dev/null 2>&1
    done

    local waited=0
    while [[ "$waited" -lt 30 ]]; do
        if [[ "$(live_jvms)" -eq 0 ]]; then
            printf 'backend stopped after %ss (no JVM remains)\n' "$waited"
            maven_pid=""
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done
    printf 'WARNING: %s JVM(s) still alive after %ss. The next preflight will refuse, and it will be right.\n' \
        "$(live_jvms)" "$waited" >&2
    return 1
}
trap 'stop_backend' INT TERM

# Wait for one of four outcomes, never for the happy one alone. A watcher that greps only for success
# is silent through a refusal, a crash and a port clash alike -- and silence is indistinguishable from
# still working, which is the failure mode with no signal at all.
seeded_marker='Seeded .* people under'
refused_marker='already holds work, so the seed will not run'
crashed_marker='APPLICATION FAILED TO START|BUILD FAILURE|Web server failed to start|Port .* was already in use'

waited=0
outcome=""
while [[ "$waited" -lt "$seed_timeout" ]]; do
    if grep -qE "$seeded_marker" "$log_file" 2>/dev/null; then outcome="seeded"; break; fi
    if grep -qE "$refused_marker" "$log_file" 2>/dev/null; then outcome="refused"; break; fi
    if grep -qE "$crashed_marker" "$log_file" 2>/dev/null; then outcome="crashed"; break; fi
    # A Maven process that has exited without printing any of the three is its own outcome, and one
    # the markers cannot see.
    kill -0 "$maven_pid" 2>/dev/null || { outcome="exited"; break; }
    sleep 5
    waited=$((waited + 5))
done

case "$outcome" in
    seeded)
        printf '\n%s\n' "$(grep -E "$seeded_marker" "$log_file" | tail -1)"
        ;;
    refused)
        stop_backend
        fail "The seeder refused: the installation already holds work. The drop did not take effect."
        ;;
    crashed)
        printf '\n%s\n' "$(grep -nE "$crashed_marker" "$log_file" | head -3)" >&2
        stop_backend
        fail "The backend did not start. See $log_file."
        ;;
    exited)
        stop_backend
        fail "Maven exited before the seed reported anything. See $log_file."
        ;;
    *)
        stop_backend
        fail "The seed did not finish within ${seed_timeout}s. See $log_file."
        ;;
esac

# ---------------------------------------------------------------------------------------------
# 5 · Prove it. The gates own this question entirely; this file does not second-guess them.
# ---------------------------------------------------------------------------------------------
step "gates"
bash "$here/scripts/gates.sh"
gates_status=$?

if $keep_running; then
    # Deliberately not `taskkill //PID $maven_pid`: that is the wrapper, and killing it leaves the
    # application running while looking like it worked. The JVMs are what to stop.
    printf '\nLeaving the backend up on http://localhost:8081 (%s JVM(s)).\n' "$(live_jvms)"
    printf 'Stop it with:  bash scripts/stop-backend.sh\n'
else
    step "stopping the backend"
    stop_backend
fi

# The gates' verdict is this script's verdict. Exit 2 -- "could not tell" -- is passed through rather
# than folded into failure, because a reseed that worked and a gate run that could not connect is a
# different situation from a reseed that produced wrong data, and they need different next actions.
exit $gates_status
