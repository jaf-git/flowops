#!/usr/bin/env bash
#
# The frontend suite ran every test file that exists, and did not merely pass.
#
# Vitest reports a total it computed about itself. On this machine class it has twice collected fewer
# files than exist and printed a passing summary anyway — once `Test Files 24 passed (24)` against 35
# on disk, and once `72 passed (73)` against 77 — because a worker died and the run continued around
# it. Both times the word next to the number was `passed`, and both times the exit code was the only
# other signal, which the second instance did not set.
#
# So this compares the run's own count against a count from `find`, which is a source that can
# disagree with it. That is the whole point: a total a tool computes about itself cannot check that
# tool, and every instance of this failure on the project has been caught by counting from outside.
#
# It also asserts the population is non-empty first. A search that matches nothing reports zero
# missing files in the same words a complete run uses.

#
# There are two suites, and each is counted separately. The jsdom run deliberately excludes
# `*.browser.test.tsx`, so a single total over every file on disk would now be wrong in the one
# direction this gate exists to catch — it would read a correctly-skipped file as a missing one, and
# a gate that cries wolf is a gate somebody switches off.

set -euo pipefail

cd "$(dirname "$0")/../../app/frontend"

# $1 human name, $2 expected count, $3 command
check_suite() {
  local name="$1" expected="$2" command="$3" output summary collected

  if [ "$expected" -eq 0 ]; then
    echo "No $name test files matched. The search is wrong, not the suite — a sweep over zero files"
    echo "reports zero problems in the same words a clean one uses."
    exit 1
  fi

  echo "$name test files on disk: $expected"

  output=$(eval "$command" 2>&1) || {
    echo "$output"
    echo
    echo "The $name suite did not pass. That is the failure; the count below is not the point."
    exit 1
  }

  echo "$output"

  # Colour stripped before anything is read from it, and the grep is not allowed to kill the script.
  #
  # Vitest colourises in CI even when its output is piped, so the summary line begins with an escape
  # sequence rather than with whitespace. `^[[:space:]]*Test Files` therefore matched locally and
  # missed on a runner — and under `set -e` with `pipefail`, a grep that matches nothing fails the
  # assignment and ends the script *there*, before the "no summary" branch below could report it. The
  # job failed with the suite green, no message, and exit 1: the gate against silent partial runs,
  # failing silently.
  summary=$(echo "$output" | sed -E 's/\x1b\[[0-9;]*[A-Za-z]//g' \
    | grep -E '^[[:space:]]*Test Files' | tail -1 || true)

  if [ -z "$summary" ]; then
    echo
    echo "Vitest printed no 'Test Files' summary for $name, so there is nothing to check the run"
    echo "against. Treating that as a failure rather than as a pass: an absent number is not a good one."
    exit 1
  fi

  collected=$(echo "$summary" | sed -E 's/.*\(([0-9]+)\).*/\1/')

  if ! [[ "$collected" =~ ^[0-9]+$ ]]; then
    echo
    echo "Could not read a file count from: $summary"
    exit 1
  fi

  echo
  echo "$name test files collected by the run: $collected"

  if [ "$collected" -ne "$expected" ]; then
    echo
    echo "The $name suite passed, and it did not run everything."
    echo "  on disk:    $expected"
    echo "  collected:  $collected"
    echo
    echo "$((expected - collected)) file(s) never executed. A green summary over a partial run is the"
    echo "failure this gate exists for — re-running until it looks right is not a fix."
    exit 1
  fi

  echo "Every $name test file on disk was collected and the suite passed."
  echo
}

# `-type f` is load-bearing. A failing browser test writes its screenshot into a directory *named*
# after the test file, so `src/shared/ui/__screenshots__/Dialog.browser.test.tsx/` matches the pattern
# and the count reads one file too many — which this gate then reports as a test that never ran. It
# found that on its first run, against itself.
jsdom_expected=$(find src -type f \( -name '*.test.ts' -o -name '*.test.tsx' \) \
  -not -name '*.browser.test.tsx' | wc -l | tr -d '[:space:]')
browser_expected=$(find src -type f -name '*.browser.test.tsx' | wc -l | tr -d '[:space:]')

check_suite "jsdom" "$jsdom_expected" "npm test"
check_suite "browser" "$browser_expected" "npm run test:browser"
