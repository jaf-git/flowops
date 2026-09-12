#!/usr/bin/env bash
# The way to run a test suite on this machine.
#
# **Why a wrapper rather than a note in a runbook.** Three incidents in one session came from starting
# a suite beside one already running, and each was preceded by a written procedure saying not to. A
# control somebody must remember is the same class of control as the pom comment that failed twice the
# same day. This runs the check because running the suite runs the check.
#
# It deliberately cannot be bound inside Maven: `preflight.sh` refuses when a JVM is alive, and Maven
# is a JVM, so a check bound to a Maven phase would flag the build that invoked it. The guard has to
# run before the build starts, which is what this is.
#
# Usage:
#   bash scripts/suite.sh backend          # everything, the default profile
#   bash scripts/suite.sh backend fast     # the inner loop, round trips excluded
#   bash scripts/suite.sh frontend         # vitest, once
#   bash scripts/suite.sh both             # backend then frontend, never at once
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backend="$here/app/backend"
frontend="$here/app/frontend"

usage() {
    printf 'usage: bash scripts/suite.sh {backend [fast] | frontend | both}\n'
    exit 2
}

# The whole point of the wrapper. Failing here costs a second; not failing here has cost an afternoon.
#
# The Docker requirement is passed in rather than assumed: the frontend suite runs in jsdom and has no
# engine to wait for, and a guard that blocked it during a Docker outage would be a guard somebody
# reasonably switches off.
guard() {
    if ! bash "$here/scripts/preflight.sh" "$@"; then
        printf '\nRefusing to start. Clear the above, then run this again.\n'
        exit 1
    fi
}

run_backend() {
    local profile_args=()
    # `-P fast` is the deliberate opt-out. The default runs everything, so a forgotten flag costs time
    # rather than truth -- the failure direction this project chooses everywhere.
    if [[ "${1:-}" == "fast" ]]; then
        profile_args=(-P fast)
        printf '\n== backend, FAST (round trips excluded -- not a profile to make a claim from) ==\n'
    else
        printf '\n== backend, everything ==\n'
    fi
    ( cd "$backend" && ./mvnw -o "${profile_args[@]}" clean test )
}

run_frontend() {
    printf '\n== frontend ==\n'
    ( cd "$frontend" && npx vitest run --reporter=dot )
}

case "${1:-}" in
    backend)
        # `--start-docker`, not `--needs-docker`: a backend suite genuinely cannot run without the
        # engine, and on the one occasion it was down the cause was that Desktop had never been
        # started after a reboot. Reporting that back to a person who then types one command is a
        # round trip with no decision in it.
        guard --start-docker
        run_backend "${2:-}"
        ;;
    frontend)
        guard
        run_frontend
        ;;
    both)
        # Sequential, and the sequencing is the feature. Run in parallel these two starve each other:
        # vitest spawns a worker per file and Maven forks per test class, and the result is failed
        # *files* with no failed *tests* -- a shape no real defect has.
        # `--start-docker`, not `--needs-docker`: a backend suite genuinely cannot run without the
        # engine, and on the one occasion it was down the cause was that Desktop had never been
        # started after a reboot. Reporting that back to a person who then types one command is a
        # round trip with no decision in it.
        guard --start-docker
        run_backend "${2:-}" || exit $?
        printf '\n-- backend finished; starting the frontend only now --\n'
        run_frontend
        ;;
    *)
        usage
        ;;
esac
