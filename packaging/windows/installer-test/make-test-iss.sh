#!/usr/bin/env bash
# Produces an isolated variant of pcpanel.iss for end-to-end installer tests on a developer machine.
# Everything that could collide with a real PCPanel install is renamed: app name (=> install dir,
# HKCU\Run value name, Start-menu group), AppId (=> uninstall key), scheduled-task name, tray window
# class and exe name. The rewrite is checked, so a rename that silently stops matching fails the run.
#   usage: make-test-iss.sh <source.iss> <out.iss>
set -euo pipefail
src=$1; out=$2
TEST_GUID='5b7e0c11-aaaa-4bbb-8ccc-0123456789ab'
sed \
  -e 's/#define MyAppName "PCPanel"/#define MyAppName "PCPanelFixTest"/' \
  -e 's/#define MyAppExeName "PCPanel.exe"/#define MyAppExeName "PCPanelFixTest.exe"/' \
  -e "s/9421bff0-3840-414c-8563-407fbcd1d04d/$TEST_GUID/g" \
  -e "s/StartupTaskName = 'PCPanel';/StartupTaskName = 'PCPanelFixTest';/" \
  -e "s/TrayWindowClass = 'PCPanelTrayWindow';/TrayWindowClass = 'PCPanelFixTestTrayWindow';/" \
  -e '/^SetupIconFile=/d' \
  "$src" > "$out"
for must in 'MyAppName "PCPanelFixTest"' 'MyAppExeName "PCPanelFixTest.exe"' "AppId={{$TEST_GUID}" \
            "StartupTaskName = 'PCPanelFixTest'" "TrayWindowClass = 'PCPanelFixTestTrayWindow'"; do
  grep -qF -- "$must" "$out" || { echo "rename failed: $must" >&2; exit 1; }
done
if grep -q '9421bff0' "$out" || grep -q "'PCPanelTrayWindow'" "$out" || grep -q 'MyAppName "PCPanel"' "$out" || grep -q SetupIconFile "$out"; then
  echo "test iss still references the real install or the icon" >&2; exit 1
fi
echo "wrote $out"
