# Silent-install scenarios for the startup registration of the (isolated) PCPanelFixTest installer.
# They cover what the in-app auto-updater does (a /VERYSILENT /UPDATE=1 run on top of an existing
# install) with every combination of "what is registered on the machine" and "what Inno remembers".
# Usage: pwsh -File autostart-scenarios.ps1 -Setup <path\to\PCPanelFixTest setup.exe>
param([Parameter(Mandatory)][string]$Setup)
$ErrorActionPreference = 'Stop'
$RunKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
$Name = 'PCPanelFixTest'
$Unins = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{5b7e0c11-aaaa-4bbb-8ccc-0123456789ab}_is1'
$AppDir = Join-Path $env:LOCALAPPDATA "Programs\$Name"
$script:fail = 0

function RunValue { (Get-ItemProperty $RunKey -ErrorAction SilentlyContinue).$Name }
function Memory {
  $p = Get-ItemProperty $Unins -ErrorAction SilentlyContinue
  if (-not $p) { return '<no uninstall key>' }
  "selected=[{0}] deselected=[{1}]" -f $p.'Inno Setup: Selected Tasks', $p.'Inno Setup: Deselected Tasks'
}
function SetMemory([string]$selected, [string]$deselected) {
  Set-ItemProperty $Unins 'Inno Setup: Selected Tasks' $selected
  Set-ItemProperty $Unins 'Inno Setup: Deselected Tasks' $deselected
}
function Install([string[]]$extra) {
  $args = @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART') + $extra
  $p = Start-Process -FilePath $Setup -ArgumentList $args -Wait -PassThru
  if ($p.ExitCode -ne 0) { throw "installer exit code $($p.ExitCode)" }
  Start-Sleep -Milliseconds 300
}
function Uninstall {
  $u = Join-Path $AppDir 'unins000.exe'
  if (Test-Path $u) { Start-Process -FilePath $u -ArgumentList '/VERYSILENT','/SUPPRESSMSGBOXES' -Wait | Out-Null }
  Start-Sleep -Milliseconds 500
  Remove-ItemProperty $RunKey $Name -ErrorAction SilentlyContinue
  if (Test-Path $Unins) { Remove-Item $Unins -Recurse -Force }
  if (Test-Path $AppDir) { Remove-Item $AppDir -Recurse -Force -ErrorAction SilentlyContinue }
}
function Check([string]$what, [bool]$cond, [string]$detail) {
  if ($cond) { Write-Host "  PASS  $what  ($detail)" } else { Write-Host "  FAIL  $what  ($detail)"; $script:fail++ }
}

Uninstall
if (RunValue) { throw 'Run value present before start' }

Write-Host "`n== silent update with a stale 'startup deselected' memory keeps autostart and corrects the memory"
Install @('/TASKS=startup')
Check '/TASKS=startup on its own writes the Run value' ([bool](RunValue)) "value='$(RunValue)'"
SetMemory '' 'startup,startup\admin,desktopicon'
Install @('/UPDATE=1')
Check 'silent /UPDATE=1 keeps the Run value' ([bool](RunValue)) "value='$(RunValue)'"
$m = Memory
Check 'memory now records startup as selected' ($m -match '(^|\s)selected=\[(startup|startup,)' -and $m -notmatch 'deselected=\[(startup,|startup\])') $m
Install @('/UPDATE=1')
Check 'a second silent update still keeps the Run value' ([bool](RunValue)) "value='$(RunValue)'"

Write-Host "`n== nothing registered on the machine, memory says selected -> update leaves it absent"
Remove-ItemProperty $RunKey $Name -ErrorAction SilentlyContinue
SetMemory 'startup' 'startup\admin,desktopicon'
Install @('/UPDATE=1')
Check 'silent update does not add a Run value the machine did not have' (-not (RunValue)) "value='$(RunValue)' memory: $(Memory)"

Write-Host "`n== explicit /TASKS on an upgrade wins over machine state"
New-ItemProperty $RunKey $Name -Value "`"$AppDir\$Name.exe`" quiet" -Force | Out-Null
Install @('/TASKS=!startup')
Check 'explicit /TASKS=!startup removes the Run value' (-not (RunValue)) "value='$(RunValue)'"
Install @('/TASKS=startup')
Check 'explicit /TASKS=startup adds it back' ([bool](RunValue)) "value='$(RunValue)'"
Uninstall

Write-Host "`n== fresh silent install with no /TASKS"
Install @()
Check 'fresh install writes the Run value by default (startup is default-checked)' ([bool](RunValue)) "value='$(RunValue)' memory: $(Memory)"
Uninstall

Write-Host "`n== cleanup"
Check 'no Run value left' (-not (RunValue)) ''
Check 'no uninstall key left' (-not (Test-Path $Unins)) ''
Check 'no install dir left' (-not (Test-Path $AppDir)) ''
Write-Host "`nFAILURES: $script:fail"
exit $script:fail
