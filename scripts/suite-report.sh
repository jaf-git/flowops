#!/usr/bin/env bash
# Read the backend suite's result out of target/surefire-reports, and refuse to answer when it cannot.
#
# **This is `IMPL-SOP-42`'s prescribed check, which until now existed only as prose in a table.** That
# row was reopened 🔴 after a session read `failures="0"` from a report a still-live build overwrote
# minutes later, and it names the mechanical form the row was missing. A rule written in a procedure
# file is a rule somebody has to remember at the moment they are least likely to; this is the same rule
# as an exit code.
#
# Three answers, never two:
#
#   0  the suite passed
#   1  the suite failed, and the failing classes are named
#   2  NO CONCLUSION — a build is live, or the reports are absent, or nothing was examined
#
# **Collapsing 2 into either of the others is the entire defect this guards.** "I could not tell" and
# "it passed" have been the same green on this project at least six times, by six different routes, and
# every one of them produced a false report to the owner.
#
# Usage:  bash scripts/suite-report.sh
#         bash scripts/suite-report.sh --quiet
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
reports="${FLOWOPS_SUREFIRE_DIR:-$here/app/backend/target/surefire-reports}"

quiet=false
[[ "${1:-}" == "--quiet" ]] && quiet=true
say() { $quiet || printf '%s\n' "$*"; }

# A live JVM means a build is still writing here, and a report read mid-flight is a claim about a
# directory that is still changing. Matched on the full path rather than the image name: `jusched.exe`
# and `jucheck.exe` are the Java Update scheduler, they are permanent residents, and counting them as
# "the build" wasted a diagnosis in this very session.
if ps -W 2>/dev/null | grep -q "hotspot/bin/java"; then
    printf 'NO CONCLUSION: a JVM is live, so the reports are still being written.\n' >&2
    exit 2
fi

if [[ ! -d "$reports" ]]; then
    printf 'NO CONCLUSION: %s does not exist. No run has written reports here.\n' "$reports" >&2
    exit 2
fi

shopt -s nullglob
files=("$reports"/TEST-*.xml)
shopt -u nullglob

# The population assertion, and it is the one that matters most. `mvn clean` removes this directory, so
# an empty one is the ordinary state after a build that never reached its tests -- a compile error, a
# formatting violation, a context that failed to start. Reporting "0 failures" over it would be true in
# the same words a clean run uses.
if [[ "${#files[@]}" -eq 0 ]]; then
    printf 'NO CONCLUSION: %s holds no TEST-*.xml. The run did not reach its tests.\n' "$reports" >&2
    exit 2
fi

# One awk pass over the suite elements. Attributes are read off the `<testsuite …>` element rather than
# by counting `<testcase>` children, because a class that failed in @BeforeAll reports its failure on
# the suite and has no cases at all -- counting children would silently drop exactly the worst failures.
summary=$(awk '
    /<testsuite / {
        name = ""; t = 0; f = 0; e = 0; s = 0
        if (match($0, /name="[^"]*"/))     { name = substr($0, RSTART+6,  RLENGTH-7) }
        if (match($0, /tests="[0-9]+"/))   { t = substr($0, RSTART+7,  RLENGTH-8) + 0 }
        if (match($0, /failures="[0-9]+"/)){ f = substr($0, RSTART+10, RLENGTH-11) + 0 }
        if (match($0, /errors="[0-9]+"/))  { e = substr($0, RSTART+8,  RLENGTH-9) + 0 }
        if (match($0, /skipped="[0-9]+"/)) { s = substr($0, RSTART+9,  RLENGTH-10) + 0 }
        classes++; tests += t; failures += f; errors += e; skipped += s
        if (f > 0 || e > 0) { bad[name] = f "f/" e "e" }
    }
    END {
        printf "%d %d %d %d %d\n", classes, tests, failures, errors, skipped
        for (n in bad) { printf "BAD %s %s\n", n, bad[n] }
    }
' "${files[@]}")

read -r classes tests failures errors skipped <<< "$(printf '%s\n' "$summary" | head -1)"

# awk counted the suite elements; the shell counted the files. They must agree, and when they do not the
# reports are not what this script thinks it read -- a truncated write, a file from another tool, a run
# that died mid-XML. That disagreement is a third state, so it exits 2 rather than guessing.
if [[ "$classes" -ne "${#files[@]}" ]]; then
    printf 'NO CONCLUSION: %s files but %s suite elements. The reports are not internally consistent.\n' \
        "${#files[@]}" "$classes" >&2
    exit 2
fi

say ""
say "$(printf '%-12s %s' 'classes'  "$classes")"
say "$(printf '%-12s %s' 'tests'    "$tests")"
say "$(printf '%-12s %s' 'failures' "$failures")"
say "$(printf '%-12s %s' 'errors'   "$errors")"
say "$(printf '%-12s %s' 'skipped'  "$skipped")"
say ""

if [[ "$((failures + errors))" -gt 0 ]]; then
    say "FAILED:"
    printf '%s\n' "$summary" | awk '/^BAD /{ printf "  %-70s %s\n", $2, $3 }'
    exit 1
fi

say "PASSED — $tests tests across $classes classes, read from $reports."
exit 0
