# Installer end-to-end tests (Windows, manual)

Real-installer checks for `pcpanel.iss`, run on a developer machine. They compile an **isolated**
variant of the script (`PCPanelFixTest`: its own app name, AppId, install dir, `HKCU\Run` value,
scheduled-task name and tray window class) so nothing touches a real PCPanel install on the same PC.

Needs Inno Setup 6 (`ISCC.exe`), Git Bash and `pwsh`.

```powershell
# 1. isolated script + a stand-in exe (anything that exits on its own will do)
bash packaging/windows/installer-test/make-test-iss.sh packaging/windows/pcpanel.iss $env:TEMP\fixtest\test.iss
New-Item -ItemType Directory -Force $env:TEMP\fixtest\dist | Out-Null
Copy-Item C:\Windows\System32\hostname.exe $env:TEMP\fixtest\dist\PCPanelFixTest.exe

# 2. compile (run ISCC from PowerShell: Git Bash rewrites the /D switches as paths)
& "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe" /Q /DMyAppVersion=9.9.9 "/DSourceDir=$env:TEMP\fixtest\dist" `
    "/O$env:TEMP\fixtest" /Ftest-setup $env:TEMP\fixtest\test.iss

# 3. silent-install scenarios (what the in-app auto-updater does), exit code = number of failures
pwsh -File packaging/windows/installer-test/autostart-scenarios.ps1 -Setup $env:TEMP\fixtest\test-setup.exe
```

`autostart-scenarios.ps1` covers the startup registration across upgrades: a `/VERYSILENT /UPDATE=1`
run keeps an existing `HKCU\Run` value even when Inno's remembered task selection says "startup
deselected" (and re-records it as selected), leaves autostart absent when the machine has none,
lets an explicit `/TASKS` win, and a fresh install registers autostart by default. The
"run as administrator" variant is left out on purpose: creating that scheduled task goes through a
UAC prompt, which cannot be scripted.

To check the wizard itself (the two startup boxes toggle independently), run the test installer
interactively with `/CURRENTUSER`, go to *Select Additional Tasks* and toggle the boxes with Space.
Cancel before *Install*; nothing is written until then.
