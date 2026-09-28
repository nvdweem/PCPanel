#!/usr/bin/env bash
# Fail if any ELF file under a directory needs a newer glibc than the given version.
#
# The Linux artifacts are dynamically linked against the host's glibc, so every bundled binary has to
# run on the oldest distro we support. One stray file is enough to break that (the upstream kdotool
# prebuilt needed 2.39 while PCPanel itself needed 2.34), and nothing else notices: the app starts,
# only the feature behind that helper silently stops working. The AppImage catalog
# (appimage.github.io) runs the same check - every ELF counts, even one loaded only in rare cases.
#
# Usage:
#   packaging/linux/check-glibc.sh <dir> <max-version>     e.g. check-glibc.sh AppDir 2.35
set -euo pipefail

DIR="${1:?usage: check-glibc.sh <dir> <max-version>}"
MAX="${2:?usage: check-glibc.sh <dir> <max-version>}"

fail=0
while IFS= read -r -d '' f; do
    [ "$(od -An -tx1 -N4 "$f" 2>/dev/null)" == " 7f 45 4c 46" ] || continue # ELF magic
    need=$(objdump -T "$f" 2>/dev/null | grep '\*UND\*' | grep -oE 'GLIBC_[0-9]+(\.[0-9]+)+' \
        | sed 's/GLIBC_//' | sort -uV | tail -n 1 || true)
    [ -n "$need" ] || continue
    if [ "$(printf '%s\n%s\n' "$need" "$MAX" | sort -V | tail -n 1)" != "$MAX" ]; then
        echo "error: ${f#"$DIR"/} needs glibc $need (max $MAX)" >&2
        fail=1
    else
        echo ">> glibc $need  ${f#"$DIR"/}"
    fi
done < <(find "$DIR" -type f -print0)
exit $fail
