#!/usr/bin/env bash
#
# Every `@PreAuthorize` written as a disjunction must have a test that reaches its second term.
#
# This exists because the same defect has now happened three times on this project, and every time it
# was found by mutation or by fresh eyes rather than by reading:
#
#   TASK phase 3   `TaskReviewSupport.reaches` — every successful approval in the suite was Maria's,
#                  and Maria is the owner, so `TASK_VIEW_ANY` answered on an earlier clause and the
#                  subtree term was never consulted. Deletable with 96 tests green.
#   TASK phase 4   the review queue's creator term, identical cause, one clause along.
#   PROCESS        `hasAuthority('PROCESS_ASSIGN_STEP') or @processOwnership.ownsInstance(#id)` —
#                  every fixture steered with Ioana, whom `CompanyScenarioTest` seeds MANAGER and
#                  V26 grants the permission workspace-wide. The ownership half, which is the entire
#                  answer to PROCESS_REQ_BLOCKER_01, was deletable with the suite green — including
#                  the test whose *name* was the guarantee.
#
# **Why reading cannot catch it and coverage cannot either.** A disjunction short-circuits: the
# second term is *executed* on every request the first term already admitted, so a coverage report
# shows the line green and the rule unproven. And the four review roles read a diff in which a
# fixture reaching the wrong clause looks entirely correct.
#
# The check, stated so it is mechanical: for each endpoint whose `@PreAuthorize` contains ` or `,
# take the authority named in the left-hand `hasAuthority(...)`, find which roles the migrations
# grant it to, and require that some test in the repository drives that endpoint as somebody holding
# **none** of those roles. Anything else proves only the first term.
#
# It is deliberately advisory about *which* test: it asks whether a fixture exists that could reach
# the second term, not whether it does. A gate that tried to decide that would be a test runner.
#
# Usage: bash .github/scripts/check-disjunction-witnesses.sh
# Exits 1 naming every disjunction with no possible witness.

set -uo pipefail

BACKEND="app/backend/src/main/java"
TESTS="app/backend/src/test/java"
MIGRATIONS="app/backend/src/main/resources/db/migration"
FAILURES=0
CHECKED=0

# The roles a seeded person can hold. A witness is a fixture seeded with a role that is NOT granted
# the left-hand authority — those are the callers for whom the second term is the only way in.
ALL_ROLES=(OWNER MANAGER EMPLOYEE)

while IFS= read -r occurrence; do
  file="${occurrence%%:*}"
  line="${occurrence#*:}"

  # Only disjunctions. A single-term annotation has nothing to under-prove.
  case "$line" in
    *" or "*) ;;
    *) continue ;;
  esac

  # The **left-hand** authority: the term that short-circuits, and therefore the one a witness must
  # fail. Taken with grep -o and head, because `sed 's/.*hasAuthority(...)/'` is greedy and silently
  # returns the last authority on the line — which on a two-authority disjunction is the wrong one,
  # and the wrong one resolves to a different set of roles.
  authority="$(printf '%s' "$line" | grep -oE "hasAuthority\('[A-Z_]+'\)" | head -1 |
    sed "s/hasAuthority('\(.*\)')/\1/")"
  if [ -z "$authority" ]; then
    # A disjunction of two non-authority expressions — nothing to resolve against the role map, and
    # saying so is better than passing silently over it.
    printf '  ? %s\n      %s\n      no hasAuthority(...) to resolve; check this one by hand\n' \
      "$file" "$(printf '%s' "$line" | sed 's/^[[:space:]]*//')"
    continue
  fi

  CHECKED=$((CHECKED + 1))

  # Which roles the migrations grant it. A permission granted to every role has no witness at all.
  granted=""
  for role in "${ALL_ROLES[@]}"; do
    if grep -rqE "\('${role}',[[:space:]]*'${authority}'\)" "$MIGRATIONS"; then
      granted="${granted} ${role}"
    fi
  done

  ungranted=""
  for role in "${ALL_ROLES[@]}"; do
    case " ${granted} " in
      *" ${role} "*) ;;
      *) ungranted="${ungranted} ${role}" ;;
    esac
  done

  short="$(printf '%s' "$line" | sed 's/^[[:space:]]*//' | cut -c1-96)"

  if [ -z "${ungranted// /}" ]; then
    printf '  x %s\n      %s\n      %s is granted to every role, so no fixture can reach the second term.\n      The disjunction is unprovable as written.\n' \
      "$file" "$short" "$authority"
    FAILURES=$((FAILURES + 1))
    continue
  fi

  # Is there any test fixture seeded with one of the ungranted roles? `CompanyScenarioTest` seeds by
  # role name, so the role literal appearing in a test is the cheapest honest signal that a caller
  # who cannot hold this authority exists to be driven.
  witness=""
  for role in $ungranted; do
    if grep -rqE "\"${role}\"" "$TESTS"; then
      witness="${witness} ${role}"
    fi
  done

  if [ -z "${witness// /}" ]; then
    printf '  x %s\n      %s\n      %s is granted to:%s\n      no test seeds anybody holding none of those, so the second term has no possible witness\n' \
      "$file" "$short" "$authority" "${granted:- (nobody)}"
    FAILURES=$((FAILURES + 1))
  else
    printf '  . %s\n      %s\n      %s granted to:%s — witnesses available:%s\n' \
      "$file" "$short" "$authority" "${granted:- (nobody)}" "$witness"
  fi
done < <(grep -rn "@PreAuthorize" "$BACKEND" 2>/dev/null)

echo
if [ "$CHECKED" -eq 0 ]; then
  # IMPL-SOP-EVIDENCE step 9: a check of the form "nothing anywhere does X" passes over an empty set
  # in exactly the words it passes over a clean one.
  echo "check-disjunction-witnesses: no disjunctive @PreAuthorize found at all."
  echo "That is either true or the pattern has drifted. Refusing to report a pass over an empty set."
  exit 1
fi

if [ "$FAILURES" -gt 0 ]; then
  echo "check-disjunction-witnesses: ${FAILURES} of ${CHECKED} disjunction(s) have no possible witness."
  echo "A disjunction short-circuits: without a caller who fails the first term, the second is"
  echo "executed on every path and decides none of them. Seed a fixture that holds neither."
  exit 1
fi

echo "check-disjunction-witnesses: ${CHECKED} disjunction(s), each with a fixture that could reach its second term."
