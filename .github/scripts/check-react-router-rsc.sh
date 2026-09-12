#!/usr/bin/env bash
#
# The revisit trigger for a dismissed advisory, made mechanical.
#
# Dependabot alert 1 — GHSA-qwww-vcr4-c8h2, high, "React Router: RSC Mode CSRF Bypass Allows Action
# Execution Before 400 Response" — was dismissed as `not_used` on 2026-08-05. The advisory's own
# note is the whole argument: "This only affects your application if you are using the unstable RSC
# APIs." This is a Vite single-page application on react-router-dom 7.18.2 and references no RSC
# entry point anywhere. The only patched version is 8.3.0, a major upgrade across every route, and
# there is no fix on the 7.x line.
#
# A dismissal is a claim about today. The claim has two halves and either can stop being true
# quietly: somebody adopts an RSC API, or react-router moves and nobody rechecks. A note in a
# decision record relies on the next person remembering to read it; this fails the build instead.
#
# It is deliberately narrow. It does not judge whether RSC is a good idea — it says that adopting
# one while the dependency is below the patched version re-opens an advisory somebody has already
# decided does not apply, and that decision must be made again rather than inherited.
#
# Usage: bash .github/scripts/check-react-router-rsc.sh
# Exits 1 if an RSC entry point is referenced while react-router is below the patched version.

set -euo pipefail

FRONTEND="${FRONTEND:-app/frontend}"
# Overridable so the version branch can be proven against a fixture rather than by editing the real
# lockfile. The retirement path — react-router at or above the patch — is otherwise unreachable
# until the day it fires, which is the day nobody wants to discover it was wrong.
LOCKFILE="${LOCKFILE:-${FRONTEND}/package-lock.json}"
PATCHED="8.3.0"
ADVISORY="GHSA-qwww-vcr4-c8h2"

# The entry points that put the application into RSC mode. Kept as a list rather than one pattern so
# a reader can see exactly what is being watched for, and add to it without unpicking a regex.
ENTRY_POINTS=(
  "react-router/rsc"
  "@react-router/server"
  "routeRSCServerRequest"
  "matchRSCServerRequest"
  "RSCHydratedRouter"
  "createCallServer"
  "unstable_RSC"
)

if [[ ! -f "${LOCKFILE}" ]]; then
  echo "::error::${LOCKFILE} not found — cannot establish the react-router version"
  exit 1
fi

# `python3` on the Linux agents, `python` on the Windows workstation this is also run from. Resolved
# rather than assumed: the first version of this script hardcoded python3 and could only be proven
# in CI, which is the one place a gate must never be proven for the first time.
PYTHON=""
for candidate in python3 python; do
  if "${candidate}" -c "import sys" >/dev/null 2>&1; then
    PYTHON="${candidate}"
    break
  fi
done
if [[ -z "${PYTHON}" ]]; then
  echo "::error::no working python found; this check cannot read ${LOCKFILE}"
  exit 1
fi

version=$("${PYTHON}" - "${LOCKFILE}" <<'PY'
import json, sys
lock = json.load(open(sys.argv[1], encoding="utf-8"))
packages = lock.get("packages", {})
entry = packages.get("node_modules/react-router") or {}
print(entry.get("version", ""))
PY
)

if [[ -z "${version}" ]]; then
  echo "::error::react-router is not resolved in ${LOCKFILE}; this check cannot answer its question"
  exit 1
fi

# Sort the resolved version against the patched one and see which comes first. `sort -V` is version
# ordering, so this is true only when the resolved version is genuinely below the patch.
below_patch=0
if [[ "${version}" != "${PATCHED}" ]] &&
   [[ "$(printf '%s\n%s\n' "${version}" "${PATCHED}" | sort -V | head -1)" == "${version}" ]]; then
  below_patch=1
fi

if [[ "${below_patch}" -eq 0 ]]; then
  echo "react-router ${version} is at or above ${PATCHED}; ${ADVISORY} no longer applies."
  echo "This check has served its purpose and can be retired with decision row 101."
  exit 0
fi

found=0
for pattern in "${ENTRY_POINTS[@]}"; do
  if hits=$(grep -rn --fixed-strings "${pattern}" "${FRONTEND}/src" 2>/dev/null); then
    echo "::error::${pattern} is referenced while react-router is ${version}, below ${PATCHED}"
    echo "${hits}" | sed 's/^/    /'
    found=1
  fi
done

if [[ "${found}" -eq 1 ]]; then
  cat <<MESSAGE

${ADVISORY} was dismissed as "vulnerable code is not actually used", and the code above is the
reason that is no longer true. Either upgrade react-router to ${PATCHED} or later, or re-open the
dismissal and decide again with the RSC path in front of you. Do not delete this check to get green.
MESSAGE
  exit 1
fi

echo "react-router ${version} is below ${PATCHED}, and no RSC entry point is referenced."
echo "${ADVISORY}'s dismissal still holds."
