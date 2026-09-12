#!/usr/bin/env bash
# Whether it is safe to start a test suite on this machine.
#
# Three incidents in one session came from starting work beside work already running: two Maven suites
# against each other, then vitest beside Maven, and the third took Docker Desktop down with it. Each
# time the procedure said not to and each time the new thing looked cheap in isolation. This is the
# check that replaces remembering.
#
# It is deliberately not clever. It answers one question — *is anything already running, and is Docker
# up* — and it answers it in under a second.
#
# Usage:  bash scripts/preflight.sh                 # contention only
#         bash scripts/preflight.sh --needs-docker  # also require a live engine
#         bash scripts/preflight.sh --start-docker  # ...and start it if it is down
#         bash scripts/preflight.sh --quiet         # exit code only
#
# **Docker is checked on request rather than always**, and the distinction matters more than it looks.
# The frontend suite runs in jsdom and needs no engine; refusing to run it because Docker is down would
# be a guard blocking work it has no business blocking — which is how a guard comes to be commented
# out. It is asked for by the caller that actually needs it.
#
# **Checking and starting are separate, and only the caller may ask for the second.** Without
# `--start-docker` this script reads the machine and changes nothing, which is the property that makes
# its answer worth having: a check that quietly repairs what it is checking can never report a problem
# twice, and stops being evidence. Remediation is opt-in, and `suite.sh` opts in because a suite that
# cannot start is not a finding, it is a wait.
set -uo pipefail

quiet=false
needs_docker=false
start_docker=false
for arg in "$@"; do
    case "$arg" in
        --quiet) quiet=true ;;
        --needs-docker) needs_docker=true ;;
        # Starting implies needing: asking for the engine to be started and not required is a
        # combination with no meaning, and inferring it here keeps every caller to one flag.
        --start-docker) start_docker=true; needs_docker=true ;;
    esac
done

say() { $quiet || printf '%s\n' "$*"; }

# One predicate, asked by the check, by the starter, and by the wait loop. Three spellings of "is it
# up" is three chances for them to disagree about what up means.
docker_engine_up() { docker version --format '{{.Server.Version}}' >/dev/null 2>&1; }

# Where Docker Desktop actually is, and the only place in this repository that claims to know.
#
# The 2026-08-22 session lost time to this: the engine was down purely because Desktop had never been
# started after a reboot, and it is installed under `%LOCALAPPDATA%\Programs\DockerDesktop` on this
# machine rather than in `Program Files`, so looking in the obvious place concluded "not installed".
# The install path is derived from the `docker` CLI on PATH before any hardcoded guess, because that
# one is true wherever Docker was put.
docker_desktop_exe() {
    local cli resolved candidate
    cli=$(command -v docker 2>/dev/null || true)
    if [[ -n "$cli" ]]; then
        # …/resources/bin/docker.exe → …/Docker Desktop.exe, two levels up from `resources`.
        resolved="$(cd "$(dirname "$cli")/../.." 2>/dev/null && pwd)/Docker Desktop.exe"
        [[ -f "$resolved" ]] && { printf '%s\n' "$resolved"; return 0; }
    fi
    for candidate in \
        "${LOCALAPPDATA:-$HOME/AppData/Local}/Programs/DockerDesktop/Docker Desktop.exe" \
        "${PROGRAMFILES:-/c/Program Files}/Docker/Docker/Docker Desktop.exe"; do
        [[ -f "$candidate" ]] && { printf '%s\n' "$candidate"; return 0; }
    done
    return 1
}

# Launch Desktop and wait for the engine to answer. Returns non-zero if it never does, so a caller
# that cannot start Docker still refuses to start a suite rather than proceeding into a hang — the
# failure this whole file exists to prevent.
start_docker_and_wait() {
    local exe waited=0
    if ! exe=$(docker_desktop_exe); then
        say "DOWN: the Docker engine is not answering, and Docker Desktop was not found to start."
        return 1
    fi
    say "DOWN: the Docker engine is not answering. Starting Docker Desktop and waiting for it."
    ( "$exe" >/dev/null 2>&1 & ) || true
    # Three minutes. A cold Desktop on this machine answers in well under one; a Desktop that has not
    # answered in three is wedged, and waiting longer only delays saying so.
    while [[ "$waited" -lt 180 ]]; do
        sleep 3
        waited=$((waited + 3))
        if docker_engine_up; then
            say "      engine up after ${waited}s."
            return 0
        fi
    done
    say "      engine still silent after ${waited}s. Docker Desktop may be wedged; a reboot clears WSL."
    return 1
}

problems=0

# A live JVM is a Maven suite, a surefire fork, or the demo backend. All three contend for the cores
# the suite needs, and an orphaned fork left by a killed parent looks exactly like a healthy one.
jvms=$(tasklist //FI "IMAGENAME eq java.exe" //FO CSV //NH 2>/dev/null | grep -c '^"java.exe"' || true)
if [[ "$jvms" -gt 0 ]]; then
    say "BUSY: $jvms java process(es) running — a suite, a surefire fork, or the demo backend."
    say "      Stop them before starting another run (IMPL-SOP-ONE-SUITE)."
    problems=$((problems + 1))
fi

# vitest spawns a worker per file and will happily take every core. It starves a Maven run rather than
# failing beside it, which produces failed *files* with no failed *tests* — a shape no real defect has.
# A live Vite matters for the same reason: the one invalid frontend run on record covered 116 of 120
# files beside a dev server and printed *passed*.
#
# **Ask what the process is, not how many there are.** This counted `node.exe` and called more than two
# a suite, because `tasklist` cannot see a command line. On 2026-08-23 that refused a reseed over three
# MCP servers — node processes belonging to the editor, not to this repository, which no amount of
# waiting would clear. A guard that fires on work it does not govern is one a reader learns to skip.
node_query="(Get-CimInstance Win32_Process -Filter \"Name='node.exe'\").CommandLine"
# The query's own exit status is tested, so a grep that matches nothing stays distinguishable from a
# query that never ran. Piping the two together would collapse both into "clear".
if node_cmdlines=$(powershell.exe -NoProfile -NonInteractive -Command "$node_query" 2>/dev/null); then
    runners=$(printf '%s\n' "$node_cmdlines" | grep -i -E '[\\/]vite([\\/.]|$| )|vitest' || true)
    if [[ -n "$runners" ]]; then
        say "BUSY: a vite or vitest process is running:"
        printf '%s\n' "$runners" | cut -c1-110 | sed 's/^/      /'
        problems=$((problems + 1))
    fi
else
    # The query itself failed — no PowerShell, or CIM refused. Falling through to "clear" here would
    # report the absence of an answer as the answer, so the blunt count comes back for this one run.
    nodes=$(tasklist //FI "IMAGENAME eq node.exe" //FO CSV //NH 2>/dev/null | grep -c '^"node.exe"' || true)
    say "UNKNOWN: could not read node command lines. $nodes node process(es) are running; check by hand."
    problems=$((problems + 1))
fi

# Testcontainers needs the daemon, and a suite started without it hangs rather than failing: the parent
# dies, the forks orphan, and the log simply stops. That is how fifty-three minutes went missing once.
if $needs_docker && ! docker_engine_up; then
    if $start_docker; then
        start_docker_and_wait || problems=$((problems + 1))
    else
        say "DOWN: the Docker engine is not answering. Testcontainers will hang rather than fail."
        # Naming the executable rather than saying "start Docker Desktop", because on this machine
        # the obvious location is the wrong one and a reader who checks it concludes it is not
        # installed. An instruction that can be pasted beats an instruction that must be interpreted.
        if exe=$(docker_desktop_exe); then
            say "      Start it:  \"$exe\""
            say "      Or re-run with --start-docker to have this script do it and wait."
        else
            say "      Docker Desktop was not found in any known location."
        fi
        problems=$((problems + 1))
    fi
fi

# **The check that would have saved an afternoon.** On 2026-08-22 `C:` reached zero bytes free and the
# machine failed in four places at once — WSL hung because it could not write its VM state, Docker
# could not reach WSL, a Maven run died mid-write leaving orphaned forks, and vitest reported *failed
# files with no failed tests*. That last shape was diagnosed three times as resource contention. It is
# equally the signature of a full disk, and one second of checking distinguishes them.
free_kb=$(df -k /c 2>/dev/null | awk 'NR==2 {print $4}')
if [[ -n "${free_kb:-}" ]] && [[ "$free_kb" -lt 5242880 ]]; then
    say "LOW: $((free_kb / 1024 / 1024)) GB free on C:. Below 5 GB, WSL, Docker and the build all fail"
    say "     in ways that look like anything but a full disk."
    problems=$((problems + 1))
fi

if [[ "$problems" -eq 0 ]]; then
    $needs_docker && say "clear: nothing else running, Docker up. Safe to start one run." \
        || say "clear: nothing else running. Safe to start one run."
    exit 0
fi
exit 1
