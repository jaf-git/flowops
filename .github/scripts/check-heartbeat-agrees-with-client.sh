#!/usr/bin/env bash
#
# The stream's heartbeat interval and the client's patience are one number in two places.
#
# The server writes a `beat` frame every `flowops.canvas.heartbeat-interval-ms` so that silence
# becomes measurable: a calm process emits no deltas for hours, and a connection wedged open by a
# proxy emits none either, so without a frame that arrives regardless the client cannot tell a quiet
# board from a dead one. `shared/realtime/RealtimeClient.ts` then treats silence longer than
# `DEFAULT_SILENCE_MS` as a stream to stop believing.
#
# **The two numbers are coupled and nothing but this script says so.** Raise the server's interval
# past the client's patience and every working board calls itself offline, on a schedule, for
# everybody — a failure with no error, no exception and no failing test, which announces itself only
# as users learning to ignore the badge. Lower the client's patience below two beats and one dropped
# frame does the same thing intermittently, which is worse because it looks like a network problem.
#
# **Why a script rather than a comment.** The comment exists, in `application.yaml` beside the
# property and in `RealtimeClient.ts` above the constant, and a comment is exactly what step 33 and
# step 38 of `work/IMPL_SOPS.md` already found insufficient twice: a rule that depends on somebody
# remembering it is a rule that fails on the day somebody is tuning a timeout under pressure. The
# same argument that turned step 33 into `check-disjunction-witnesses.sh` applies here.
#
# The rule: the client must wait at least TWO beats before disbelieving a stream, so that a single
# lost frame is survivable. Three is the shipped default and this floor is deliberately looser than
# the default, because the gate exists to catch drift into danger, not to freeze a tuning decision.
#
# Usage: bash .github/scripts/check-heartbeat-agrees-with-client.sh
# Exits 1 when the two numbers no longer agree, or when either can no longer be found.

set -uo pipefail

CONFIG="app/backend/src/main/resources/application.yaml"
CLIENT="app/frontend/src/shared/realtime/RealtimeClient.ts"
MINIMUM_BEATS=2

for file in "$CONFIG" "$CLIENT"; do
  if [ ! -f "$file" ]; then
    echo "check-heartbeat: $file does not exist."
    echo "One of the two numbers has moved house. Refusing to report a pass over a file that is gone."
    exit 1
  fi
done

# `heartbeat-interval-ms: ${FLOWOPS_CANVAS_HEARTBEAT_INTERVAL_MS:15000}` -> 15000
INTERVAL="$(grep -oE 'heartbeat-interval-ms:[^}]*:[0-9]+' "$CONFIG" | grep -oE '[0-9]+$')"

# `const DEFAULT_SILENCE_MS = 45_000;` -> 45000
SILENCE="$(grep -oE 'DEFAULT_SILENCE_MS[[:space:]]*=[[:space:]]*[0-9_]+' "$CLIENT" |
  grep -oE '[0-9_]+$' | tr -d '_')"

# IMPL-SOP-EVIDENCE step 9: a check over a population nobody found passes in exactly the words a
# clean one does. Both numbers are read back and printed, so a pass says what it compared.
if [ -z "$INTERVAL" ]; then
  echo "check-heartbeat: could not read heartbeat-interval-ms from $CONFIG."
  echo "Either the property was renamed or the pattern has drifted. Refusing to report a pass."
  exit 1
fi

if [ -z "$SILENCE" ]; then
  echo "check-heartbeat: could not read DEFAULT_SILENCE_MS from $CLIENT."
  echo "Either the constant was renamed or the pattern has drifted. Refusing to report a pass."
  exit 1
fi

BEATS_TOLERATED=$((SILENCE / INTERVAL))

if [ "$BEATS_TOLERATED" -lt "$MINIMUM_BEATS" ]; then
  echo "check-heartbeat: the client stops believing the stream after ${BEATS_TOLERATED} beat(s)."
  echo
  echo "  server beats every : ${INTERVAL}ms  ($CONFIG)"
  echo "  client waits       : ${SILENCE}ms  ($CLIENT)"
  echo "  beats tolerated    : ${BEATS_TOLERATED}, and at least ${MINIMUM_BEATS} are required"
  echo
  echo "A client that disbelieves the stream before two beats have been missed reports offline on a"
  echo "working board the first time one frame is lost. Raise DEFAULT_SILENCE_MS, or lower the"
  echo "server's interval, so that the two agree again."
  exit 1
fi

# The second half: the wire between the two numbers, added 2026-08-17 and corrected 2026-08-18.
#
# Comparing the two numbers proves they agree. It does not prove the server's number can reach a
# client at all — and it could not: `RealtimeClient.ts` accepted `silenceMs`, and nothing upstream
# passed one.
#
# **The first version of this check made that worse rather than better, and the correction is the
# lesson.** It grepped a single hop — `useRealtimeStream` forwarding to `RealtimeClient` — and on
# finding it printed *"a deployment can move both"*. The chain is three hops, and the third
# (`useLiveOperationsCanvas`, the only surface that actually subscribes) was still cut. So the gate
# went green while asserting a guarantee that did not hold, which is worse than the silence it
# replaced: an unchecked gap is found eventually, a gap with a green check over it is not looked for.
# Found by the delegated fresh-eyes pass on 2026-08-18, an hour after the check was written.
#
# Every hop is checked now, and the pass says only what it proved.
HOOK="app/frontend/src/shared/realtime/useRealtimeStream.ts"
CONSUMER="app/frontend/src/features/canvas/hooks/useLiveOperationsCanvas.ts"

for file in "$HOOK" "$CONSUMER"; do
  if [ ! -f "$file" ]; then
    echo "check-heartbeat: $file does not exist."
    echo "A hop on the only route from the server's interval to a client has moved house."
    echo "Refusing to report a pass over a file that is gone."
    exit 1
  fi
done

cut_at() {
  echo "check-heartbeat: the wire from the server's interval to the client is cut at $1."
  echo
  echo "  server beats every : ${INTERVAL}ms  ($CONFIG)"
  echo "  client waits       : ${SILENCE}ms  ($CLIENT)"
  echo
  echo "The two numbers above may agree perfectly and it makes no difference. A patience that cannot"
  echo "travel is a patience no deployment can set, so an installation beating slower than the"
  echo "client's own default tears down a working EventSource and reopens it on a fixed schedule, on"
  echo "every board, with nothing failing anywhere."
  exit 1
}

# Hop 2 of 3: the shared hook forwards what it was given.
grep -qE 'silenceMs:[[:space:]]*latest\.current\.silenceMs' "$HOOK" ||
  cut_at "$HOOK (it does not forward silenceMs to RealtimeClient)"

# Hop 3 of 3: the surface that actually subscribes both declares the option and passes it on. Both,
# because declaring it without passing it compiles and does nothing, which is how this was missed.
grep -qE '^[[:space:]]*silenceMs\?:' "$CONSUMER" ||
  cut_at "$CONSUMER (its options do not declare silenceMs)"
grep -qE 'silenceMs:[[:space:]]*options\.silenceMs' "$CONSUMER" ||
  cut_at "$CONSUMER (it declares silenceMs and never passes it to useRealtimeStream)"

echo "check-heartbeat: ok — server beats every ${INTERVAL}ms, client waits ${SILENCE}ms (${BEATS_TOLERATED} beats)."
echo "check-heartbeat: the prop chain is unbroken across all three hops."
echo "check-heartbeat: NOT proved — that any deployment mechanism supplies silenceMs. Nothing serves"
echo "check-heartbeat: flowops.canvas.heartbeat-interval-ms to the browser, so today every board uses"
echo "check-heartbeat: the client default and only a code change can move it. The seam is open, not wired."
exit 0
