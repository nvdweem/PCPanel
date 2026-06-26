#!/usr/bin/env bash
# Boot-and-serve smoke test for the assembled native binary.
#
# Launches the binary, waits for the embedded HTTP server, and asserts a set of REST endpoints
# respond. Reaching them with HTTP 200 proves the native image boots, Quarkus starts, the server
# binds, and backend state round-trips through JSON — which is exactly where native-image bugs hide
# (the "compiles fine, 500s at runtime" class: missing List<DTO> array reflection, FileSerializer,
# JNA proxy/pointer registration). It costs a few seconds on top of the multi-minute native compile.
#
# Shared by the Linux and macOS CI jobs so they don't copy-paste the curl loop (#102). Windows keeps
# its own PowerShell step: its primary check (the overlay font list, which proves AWT/Java2D loaded
# through the exe-rename path) is shell-specific, and Git Bash process control for a native .exe is
# less reliable than pwsh — so on Windows the endpoint assertions are inlined there instead.
#
# Usage:
#   packaging/smoke-test.sh <binary> [--port N] [--require /ep ...] [--lenient /ep ...]
#                           [--require-hid] [--boot-timeout SECONDS]
#
#   --require /ep       Endpoint that MUST return 200 (repeatable). The first one is also the
#                       readiness probe polled until the server is up.
#   --lenient /ep       Endpoint that SHOULD return 200 but only warns on failure (repeatable) —
#                       e.g. /api/audio/* on a CI runner with no audio server, where Linux audio
#                       degrades to a no-op ISndCtrl by design.
#   --require-hid       Fail if the startup log reports "Failed to initialize HID services".
#   --boot-timeout N    Seconds to wait for the server (default 120).
#
# Defaults to --require /api/devices when no --require is given.
set -euo pipefail

bin=${1:?usage: smoke-test.sh <binary> [options]}
shift

port=7654
boot_timeout=120
require_hid=0
require=()
lenient=()

while [ $# -gt 0 ]; do
  case "$1" in
    --port)         port=$2; shift 2 ;;
    --require)      require+=("$2"); shift 2 ;;
    --lenient)      lenient+=("$2"); shift 2 ;;
    --require-hid)  require_hid=1; shift ;;
    --boot-timeout) boot_timeout=$2; shift 2 ;;
    *) echo "smoke-test.sh: unknown argument '$1'" >&2; exit 2 ;;
  esac
done

[ ${#require[@]} -gt 0 ] || require=(/api/devices)

base="http://127.0.0.1:${port}"
log=pcpanel-smoke.log

echo "Smoke test: booting $bin on port $port"
"$bin" skipfilecheck quiet "-Dquarkus.http.port=${port}" >"$log" 2>&1 &
pid=$!
trap 'kill -KILL "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT

dump_log() { echo "----- $log (last 60 lines) -----"; tail -n 60 "$log" 2>/dev/null || true; }

# Poll the first required endpoint until the server answers (or the process dies / we time out).
ready=0
probe=${require[0]}
deadline=$(( $(date +%s) + boot_timeout ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "FAIL: the app process exited during startup (a crash or, on macOS, a code-signature rejection)"
    dump_log
    exit 1
  fi
  if curl -fsS -o /dev/null "${base}${probe}" 2>/dev/null; then ready=1; break; fi
  sleep 2
done
if [ "$ready" != 1 ]; then
  echo "FAIL: app did not serve ${base}${probe} within ${boot_timeout}s"
  dump_log
  exit 1
fi

# Required endpoints: a non-200 fails the build (a 5xx here usually means a native-image reflection gap).
for ep in "${require[@]}"; do
  code=$(curl -s -o /tmp/smoke-resp.json -w '%{http_code}' "${base}${ep}")
  echo "GET $ep -> $code (required)"
  if [ "$code" != 200 ]; then
    echo "FAIL: $ep returned $code"
    head -c 2000 /tmp/smoke-resp.json || true; echo
    dump_log
    exit 1
  fi
done

# Lenient endpoints: warn but don't fail (the runner may lack the backing service).
for ep in "${lenient[@]}"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "${base}${ep}" || echo 000)
  echo "GET $ep -> $code (lenient)"
  if [ "$code" != 200 ]; then
    echo "::warning::smoke: $ep returned $code (lenient — expected on a CI runner without that backend)"
  fi
done

if [ "$require_hid" = 1 ]; then
  if grep -q "Failed to initialize HID services" "$log"; then
    echo "FAIL: HID services did not initialize — hidapi native arch/load problem"
    grep -A3 "Failed to initialize HID services" "$log" || true
    exit 1
  fi
  echo "HID services initialized OK."
fi

echo "Smoke test OK: the app booted and served its REST API."
