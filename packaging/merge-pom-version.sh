#!/usr/bin/env bash
# Merge driver for pom.xml, used by the forward merge from a maintenance line into main.
#
# <project.baseversion> names the version a branch is working towards, so it differs per line by
# design (releases/2.0 -> 2.0.x, main -> 2.1) and each line moves it away from their common ancestor
# after every release. That makes it a guaranteed conflict on every forward merge, always with the
# same correct answer: main is the newer line, so main's value stays.
#
# Resolving the whole file in main's favour would be wrong — it would silently drop a dependency
# bump or plugin change made on the maintenance line. So this neutralises only that one line: it is
# rewritten to main's value in both the merge base and the incoming side, so git sees the line as
# unchanged on their side and keeps ours. Every other difference in pom.xml merges — or conflicts —
# exactly as it would have.
#
# Git invokes this as: merge-pom-version.sh %O %A %B %L
#   %O merge base   %A ours (main; the merged result must be written here)   %B theirs   %L marker size
# Exit 0 means cleanly merged; non-zero leaves conflict markers for a human, which is what should
# happen for any conflict that is not the version line.
#
# Configured by .github/workflows/merge-forward.yml and selected by .gitattributes. Without that
# config git falls back to its default merge and the version line conflicts as before — a developer
# merging by hand is not broken by this file, just unaided.
set -uo pipefail

ancestor=$1
ours=$2
theirs=$3
markers=${4:-7}

VERSION_RE='<project\.baseversion>[^<]*</project\.baseversion>'
our_version=$(grep -oE "$VERSION_RE" "$ours" | head -1)

aligned_ancestor=$ancestor
aligned_theirs=$theirs
if [ -n "$our_version" ]; then
    aligned_ancestor=$(mktemp)
    aligned_theirs=$(mktemp)
    trap 'rm -f "$aligned_ancestor" "$aligned_theirs"' EXIT
    sed -E "s|$VERSION_RE|$our_version|" "$ancestor" > "$aligned_ancestor"
    sed -E "s|$VERSION_RE|$our_version|" "$theirs" > "$aligned_theirs"
fi

git merge-file --marker-size="$markers" \
    -L 'main' -L 'merge base' -L 'maintenance line' \
    "$ours" "$aligned_ancestor" "$aligned_theirs"
