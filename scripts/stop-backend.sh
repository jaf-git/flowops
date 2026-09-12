#!/usr/bin/env bash
# Stop every JVM this project started, and prove it stopped.
#
# **Why it is a script and why it verifies.** `reseed.sh --keep-running` leaves the application up, and
# the obvious way to stop it — killing the Maven wrapper's pid — kills the wrong process. The wrapper
# is a subshell; the application is its grandchild. That mistake has already produced a false
# "backend stopped" here, and the cost lands on the *next* command: a live JVM makes `preflight.sh`
# refuse, and the refusal names contention rather than the leftover that actually caused it.
#
# It matches on the full JDK path rather than the image name. `jusched.exe` and `jucheck.exe` are the
# Java Update scheduler and are permanent residents on this machine — counting them cost a diagnosis
# once, and a stopper that waits for them would never finish.
set -uo pipefail

live_jvms() { ps -W 2>/dev/null | grep -c "hotspot/bin/java"; }

before=$(live_jvms)
if [[ "$before" -eq 0 ]]; then
    printf 'Nothing to stop: no JVM is running.\n'
    exit 0
fi

printf 'Stopping %s JVM(s)…\n' "$before"
for pid in $(ps -W 2>/dev/null | grep "hotspot/bin/java" | awk '{print $4}'); do
    MSYS_NO_PATHCONV=1 taskkill /PID "$pid" /T /F >/dev/null 2>&1
done

waited=0
while [[ "$waited" -lt 30 ]]; do
    if [[ "$(live_jvms)" -eq 0 ]]; then
        printf 'Stopped. No JVM remains after %ss.\n' "$waited"
        exit 0
    fi
    sleep 1
    waited=$((waited + 1))
done

# Exit 1 rather than a cheerful message: the caller's next act is probably a suite, and it needs to
# know the tree is not clear. Saying so here is cheaper than preflight saying it confusingly later.
printf 'FAILED: %s JVM(s) still alive after %ss.\n' "$(live_jvms)" "$waited" >&2
exit 1
