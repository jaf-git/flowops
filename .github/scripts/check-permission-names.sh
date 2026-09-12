#!/usr/bin/env bash
#
# Every permission the frontend gates a control on must be one the backend actually grants.
#
# This exists because of a defect the whole test suite agreed with. `TemplateScreen` gated its
# authoring control on `PROCESS_AUTHOR`; the permission is called `PROCESS_TEMPLATE_AUTHOR`, and
# `PROCESS_AUTHOR` is granted to nobody, declared nowhere, and issued by nothing. So the control was
# unreachable for **every user including the owner** — the one screen that authors a process could
# not author a process — and the feature's own empty state sat there inviting somebody to press a
# button that was not rendered.
#
# **Why nothing caught it.** Nine tests passed `permissions={['PROCESS_AUTHOR']}` and asserted the
# control appeared. The fixture and the code agreed, and both were wrong: that is `IMPL-SOP-SLICE`
# step 7b — *the fixture is the thing to check* — in its purest form. The mutation reddened too,
# because deleting the guard did change the output; a mutation proves a guard is load-bearing, never
# that it names the right thing. `tsc` cannot help: both sides are string literals. It was found by
# opening the product and looking at the screen, which is the whole argument for the demo walk.
#
# The check is one join: the set of literals reaching `permissions.includes('X')` in the frontend,
# against the permissions declared in the migrations. A name on the left and not on the right gates
# a control nobody can ever reach.
#
# Usage: bash .github/scripts/check-permission-names.sh
# Exits 1 naming every permission the frontend invents.

set -uo pipefail

FRONTEND="app/frontend/src"
MIGRATIONS="app/backend/src/main/resources/db/migration"
MISSING=0
CHECKED=0

GATED="$(grep -rhoE "permissions\.includes\('[A-Z_]+'\)" "$FRONTEND" 2>/dev/null |
  sed "s/.*('\(.*\)').*/\1/" | sort -u)"

if [ -z "$GATED" ]; then
  # IMPL-SOP-EVIDENCE step 9: an empty population passes in exactly the words a clean one does.
  echo "check-permission-names: no permission gate found in the frontend at all."
  echo "Either the product gates nothing, or the pattern has drifted. Refusing to report a pass"
  echo "over an empty set."
  exit 1
fi

while IFS= read -r permission; do
  [ -z "$permission" ] && continue
  CHECKED=$((CHECKED + 1))
  if grep -rqE "\('${permission}'" "$MIGRATIONS"; then
    printf '  . %s\n' "$permission"
  else
    printf '  x %s — declared in no migration, so it is granted to nobody.\n' "$permission"
    printf '      Every control gated on it is unreachable for every user, owner included.\n'
    printf '      Where the frontend uses it:\n'
    grep -rn "permissions.includes('${permission}')" "$FRONTEND" | sed 's/^/        /'
    MISSING=$((MISSING + 1))
  fi
done <<< "$GATED"

echo
if [ "$MISSING" -gt 0 ]; then
  echo "check-permission-names: ${MISSING} of ${CHECKED} gate(s) name a permission that does not exist."
  echo "A test that passes the same invented name agrees with the bug rather than catching it."
  exit 1
fi

echo "check-permission-names: ${CHECKED} permission gate(s), every one of them a permission the backend grants."
