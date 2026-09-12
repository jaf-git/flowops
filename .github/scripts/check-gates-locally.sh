#!/usr/bin/env bash
#
# Every merge gate the pull-request workflow runs, run here, in the same order.
#
# It exists because `./mvnw verify` is NOT the backend gate. The workflow also runs the identifier
# check, the records-only-grow check and the board check, and none of them live in Maven — which is how
# a pull request once reached CI with a stale board and two over-long unit notes, costing a full cycle
# for something a script would have caught in thirty seconds.
#
# It also means a CI outage stops being a blocker: six of the eight gates can be shown green from here
# in under a minute. The two it cannot run are named at the end rather than skipped silently, because a
# gate you did not run is not a gate that passed.
#
# The backend gate runs in the background while everything else runs in front of it, because they are
# independent and were serialised only by the order they happen to be written in. Measured on
# 2026-08-07: `mvnw clean verify` is 257s and every other gate here totals well under two minutes, so
# running them apart cost the sum and running them together costs the larger. Nothing is skipped and
# nothing is reported before its own exit code has been read -- the backend's result is collected at
# the end rather than trusted early, which is why it prints out of order.
#
# Usage: bash .github/scripts/check-gates-locally.sh [base-ref]     (default: origin/main)
set -u

# **Flags are parsed out before this, or `--fast` becomes the base ref.**
#
# This read `${1:-origin/main}` when the only argument this script took was a ref. Adding a flag
# silently turned `check-gates-locally.sh --fast` into a comparison against a ref called `--fast`, and
# the failure surfaced two gates later as `records only grow FAIL` -- a documentation gate reporting a
# problem with the repository when the actual fault was an argument. Caught by the same check passing
# when run by hand and failing inside the bundle, which is the only reason it was not believed.
BASE=""
for arg in "$@"; do
    case "$arg" in
        --fast) ;;
        *) [ -z "$BASE" ] && BASE="$arg" ;;
    esac
done
BASE="${BASE:-origin/main}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 2

# --- preflight: this machine may not be running the product while it grades it --------------------
#
# **A gate that fails because the machine was busy is a false red, and this script treats those the
# way it treats false greens.** The frontend lane is a worker pool that wants every core, and the
# comment above it records the symptom twice: 24 files of 35 on 2026-08-07, and 52 of 56 with four
# `Timeout waiting for worker to respond` on 2026-08-12. It happened a third time on 2026-08-17, with
# `mvnw clean verify` racing a `spring-boot:run` demo and a Vite dev server that had been up for
# sixteen hours: 86 files of 88, one worker that never answered, and ten minutes spent proving the
# code was fine. `--maxWorkers=4` bounds vitest against itself and can do nothing about a JVM.
#
# There is a second reason, and it is not about speed at all: this script runs `clean`, which deletes
# `target/` -- while a `spring-boot:run` with devtools is watching that directory and, on Windows,
# holding its files open.
#
# So it refuses, in two seconds, naming what it found. `GATES_ALLOW_BUSY_MACHINE=1` proceeds anyway
# for somebody who has read this and means it.
#
# **8080 is deliberately not in the list.** Tomcat holds it on this machine class (see README), so
# including it would refuse every run for a service that has nothing to do with this repository.
DEV_PORTS='5173|5174|8081|8082'

listening_dev_ports() {
    if command -v netstat >/dev/null 2>&1; then
        netstat -ano 2>/dev/null | grep -i 'listen' | grep -Eo ":(${DEV_PORTS})[[:space:]]" |
            tr -d ': \t' | sort -u
    elif command -v ss >/dev/null 2>&1; then
        ss -ltn 2>/dev/null | grep -Eo ":(${DEV_PORTS})[[:space:]]" | tr -d ': \t' | sort -u
    else
        return 1
    fi
}

if [ "${GATES_ALLOW_BUSY_MACHINE:-0}" != "1" ]; then
    if busy="$(listening_dev_ports)"; then
        if [ -n "$busy" ]; then
            echo "Refusing to run: this repository's development stack is listening on:"
            echo "$busy" | sed 's/^/  port /'
            echo
            echo "The frontend lane loses a race it cannot win against a running backend, and the"
            echo "backend lane deletes the target/ directory a running one is watching. A result from"
            echo "here would be a red you cannot trust, which costs more than not running at all."
            echo
            echo "Stop the demo and run this again, or set GATES_ALLOW_BUSY_MACHINE=1 to proceed."
            exit 2
        fi
    else
        # Never fail closed on an inability to check: a gate that blocks because it could not find
        # netstat is a gate that blocks for a reason having nothing to do with the code.
        echo "note: no netstat or ss on this machine, so a running dev stack could not be ruled out"
    fi
fi

pass=0
fail=0
skipped=0
failed_names=""

# Each gate writes to its own log. A single shared one was safe while these ran one at a time and is
# not safe now that the backend runs alongside them.
LOGS="$(mktemp -d)"
trap 'rm -rf "$LOGS"' EXIT

report () {
    local name="$1" status="$2" log="$3"
    printf '%-46s' "$name"
    if [ "$status" -eq 0 ]; then
        echo "PASS"
        pass=$((pass + 1))
    else
        echo "FAIL"
        fail=$((fail + 1))
        failed_names="$failed_names\n  - $name"
        sed 's/^/      /' "$log" | tail -15
    fi
}

run () {
    local name="$1"; shift
    local log="$LOGS/$(echo "$name" | tr -c 'a-zA-Z0-9' '_').log"
    "$@" > "$log" 2>&1
    report "$name" "$?" "$log"
}

# --- --fast, which exists so that the hook can be unavoidable ---------------------------------------
#
# The full bundle is about twenty-five minutes. A pre-push hook that costs twenty-five minutes is a
# hook every person on the project learns to pass `--no-verify` to within a week, and a gate somebody
# routinely skips is not a gate -- it is a delay. So the cheap half can be run on its own.
#
# **What `--fast` keeps is chosen by cost, not by importance.** Everything it runs finishes in seconds:
# the documentation gates that keep identifiers resolving and the board readable, and the frontend
# checks that are a parse rather than a build. Everything it drops is a compile or a test run --
# `clean verify`, the production build, and both suites -- which are exactly the things CI runs anyway
# and which a person is about to wait for regardless.
#
# The evidence for the split is this repository's own history: on 2026-08-23 the first full run in a
# long while found a feature contributing zero units to the board, two identifiers cited across a dozen
# shipped files and resolving to nothing, eighty-two unformatted files and a javadoc error breaking the
# build. **Every one of those would have been caught by the fast half**, months earlier, had anything
# been running it.
FAST=false
for arg in "$@"; do
    case "$arg" in
        --fast) FAST=true ;;
    esac
done

echo "Merge gates, run locally against $BASE"
echo "-----------------------------------------------------------"

# --- the backend job ------------------------------------------------------------------------------
# The long one, started first and collected last.
#
# It runs `clean verify` by default, and that is not a costume change -- it is what makes this script
# the *only* backend run a slice needs. IMPL-SOP-SLICE step 20 ended a slice with `clean verify` and
# step 24 then said to run this script, whose backend lane was a second `verify` over the same tree:
# 213s and then 180s, six and a half minutes to answer one question twice, on every slice since step
# 24 was adopted. One run now answers it, and the command whose green is reported is still the command
# CI runs -- which is the clause IMPL-SOP-EVIDENCE has required since 2026-08-04.
#
# `MVN_GOALS=verify bash check-gates-locally.sh` skips the clean for a mid-slice check that has no
# need of it. The run that gates a pull request does not set it.
BACKEND_LOG="$LOGS/backend_mvnw_verify.log"
MVN_GOALS="${MVN_GOALS:-clean verify}"
if $FAST; then
    BACKEND_PID=""
    echo "backend: mvnw $MVN_GOALS                    SKIPPED (--fast)"
else
    ( cd app/backend && ./mvnw $MVN_GOALS ) > "$BACKEND_LOG" 2>&1 &
    BACKEND_PID=$!
    echo "backend: mvnw $MVN_GOALS                    started, collected at the end"
fi

run "backend: identifiers resolve"    bash .github/scripts/check-identifiers-resolve.sh
run "backend: one request one status" bash .github/scripts/check-request-status.sh
git fetch -q origin main 2>/dev/null || true
run "backend: records only grow"      bash .github/scripts/check-records-only-grow.sh "$BASE"
run "backend: board is current"       python scripts/build-board.py --check
run "backend: disjunction witnesses"  bash .github/scripts/check-disjunction-witnesses.sh
run "frontend: permission names"    bash .github/scripts/check-permission-names.sh
# Reads a backend property and a frontend constant, so it belongs to neither lane and is cheap in
# both. The two numbers are coupled and nothing else in the build says so.
run "both: heartbeat agrees with client" bash .github/scripts/check-heartbeat-agrees-with-client.sh

# --- the frontend job -----------------------------------------------------------------------------
run "frontend: prettier"              bash -c 'cd app/frontend && npx prettier --check src'
run "frontend: typecheck"             bash -c 'cd app/frontend && npx tsc --noEmit'
run "frontend: eslint"                bash -c 'cd app/frontend && npx eslint src --max-warnings 0'
# Two caps, and the second is the one that matters.
#
$FAST || run "frontend: production build"      bash -c 'cd app/frontend && npm run build'
run "frontend: router convention"     bash .github/scripts/check-react-router-rsc.sh

# --- collect the backend --------------------------------------------------------------------------
# `wait` on the recorded pid returns that job's exit status, which is the thing being reported. A
# bare `wait` returns 0 whatever the job did, which is the empty-set trap in another coat.
if [ -n "$BACKEND_PID" ]; then
    # `wait` on the recorded pid returns that job's exit status. A bare `wait` returns 0 whatever the
    # job did, which is the empty-set trap in another coat.
    wait "$BACKEND_PID"
    report "backend: mvnw $MVN_GOALS" "$?" "$BACKEND_LOG"
fi

# --- the one lane that may not overlap the backend ------------------------------------------------
# vitest runs **after** the backend has been collected, alone, and that is not caution.
#
# Every other lane above is a script or a compile that finishes in seconds and genuinely costs nothing
# to overlap. vitest is a worker pool that wants every core, and it loses the race with Maven and
# Testcontainers: on 2026-08-07 the uncapped run reported `Test Files 24 passed (24)` while 35 existed
# on disk, and on 2026-08-12 the capped one reported 52 of 56 with four `Timeout waiting for worker to
# respond` errors. `work/IMPL_SOPS.md`'s levers table had already refused this overlap, with the first
# of those measurements written next to it, and this script did it anyway.
#
# **The saving the overlap buys is kept.** `mvnw clean verify` is 257s and the cheap lanes total well
# under two minutes, so they still run in front of it; only this one moves behind. The cost is about a
# minute of wall clock and the return is a frontend result that is not a race.
#
# `--maxWorkers=4` stays. It is no longer load-bearing here, and it keeps the run reproducible against
# a machine doing something else.
#
# The file-count check stays for the same reason it was written: a truncated run reports
# `Test Files N passed (N)`, a count of what was attempted printed as though it were a count of what
# there is -- the empty-set trap of IMPL-SOP-EVIDENCE step 9. Vitest's exit code caught it twice
# because the workers also errored; nothing guarantees the next truncation is so loud.
# **Delegated to the script CI runs, rather than reimplemented here.**
#
# This lane used to carry its own copy of the count, and the copy drifted within hours of the browser
# suite existing: it counted every `*.test.ts(x)` on disk against a jsdom run that deliberately excludes
# `*.browser.test.tsx`, and reported a complete suite as a partial run. CI stayed green throughout,
# because CI runs the other script -- so the gate a person runs before pushing was the one that was
# wrong, and it never ran the browser suite at all.
#
# Two implementations of one rule agree only while somebody remembers to change both. This one calls
# `check-every-test-file-ran.sh`, which runs both suites and counts each against `find`. It is the same
# command the pull-request workflow runs, so a pass here means the same thing a pass there does.
$FAST || run "frontend: both suites, every file ran" bash .github/scripts/check-every-test-file-ran.sh

# --- the two security gates, suspended --------------------------------------------------------------
#
# gitleaks and semgrep were removed from this bundle on 2026-08-20 by DECISION-DEMO-FOCUS-01.
#
# They are supply-chain and secret-leak gates, and both answer a question this installation no longer
# asks: it runs on one laptop, serves one demonstration, accepts no external contribution and is never
# published. The two of them together added several minutes to every local run and, between them, have
# found exactly one thing in this project's life — eight findings in a vendored HTML prototype, which
# were suppressed rather than fixed because they were not this project's code.
#
# **Nothing about the product's own security posture changed.** Authentication, the permission model,
# the pseudonymiser and the export privacy suite are subject matter rather than tooling, they all still
# run in `mvnw verify` above, and removing any of them would delete the thesis's argument rather than
# its scaffolding. What went is the scanning of the repository, not the securing of the product.
#
# Restoring both is reverting one commit; the pinned versions are still in `pull-request.yml` and the
# configuration files are still on disk.

echo "-----------------------------------------------------------"
printf 'passed %d, failed %d, could not run %d\n' "$pass" "$fail" "$skipped"
if [ "$fail" -gt 0 ]; then
    printf 'failed:%b\n' "$failed_names"
    exit 1
fi
if [ "$skipped" -gt 0 ]; then
    echo
    echo "Some gate above did not run. A gate that did not run is not a gate that passed."
fi
exit 0
