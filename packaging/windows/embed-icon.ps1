<#
.SYNOPSIS
    Embeds a Windows application icon into an existing .exe.

.DESCRIPTION
    The GraalVM native image (PCPanel.exe) is produced WITHOUT an embedded icon
    resource, so Explorer, the taskbar and any shortcut pointing at it show the
    generic default executable icon. native-image offers no built-in way to set
    one, so we post-process the finished binary with rcedit (the same tool
    electron-builder/pkg use) to write an RT_GROUP_ICON resource into the PE.

    This is invoked both by the "Assemble distribution" step in
    .github/workflows/build-and-release.yml and by the local build-installer.ps1,
    so the shipped exe (and therefore the Start-menu/desktop shortcuts that
    reference it) always carry the app icon.

    rcedit is obtained from a trusted package manager (Chocolatey/winget/scoop),
    mirroring how the build already installs Inno Setup. We never download a raw
    binary off the internet ourselves.

.PARAMETER ExePath
    The executable to modify in place (e.g. target\windows-dist\PCPanel.exe).

.PARAMETER IconPath
    The .ico to embed. Should be a multi-resolution icon (16..256px) so the icon
    stays sharp at every size Windows asks for.

.PARAMETER RcEdit
    Path to rcedit(-x64).exe. If omitted, PATH is searched and, failing that, the
    script tries to install rcedit via Chocolatey/winget/scoop if one is present.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$ExePath,
    [Parameter(Mandatory)] [string]$IconPath,
    [string]$RcEdit
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not (Test-Path $ExePath)) { throw "Executable not found: $ExePath" }
if (-not (Test-Path $IconPath)) { throw "Icon not found: $IconPath" }

function Find-RcEdit {
    $cmd = Get-Command rcedit-x64.exe, rcedit.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cmd) { return $cmd.Source }
    return $null
}

function Resolve-RcEdit {
    if ($RcEdit) {
        if (-not (Test-Path $RcEdit)) { throw "rcedit not found at -RcEdit path: $RcEdit" }
        return (Resolve-Path $RcEdit).Path
    }

    $found = Find-RcEdit
    if ($found) { return $found }

    # Not on PATH: install via whichever trusted package manager is available.
    # Each does its own package-integrity verification, so we never fetch a raw
    # binary ourselves.
    if (Get-Command choco -ErrorAction SilentlyContinue) {
        Write-Host "rcedit not found; installing via Chocolatey..."
        & choco install rcedit --no-progress -y
    } elseif (Get-Command winget -ErrorAction SilentlyContinue) {
        Write-Host "rcedit not found; installing via winget..."
        & winget install --id ElectronRcEdit.RcEdit --accept-source-agreements --accept-package-agreements -e
    } elseif (Get-Command scoop -ErrorAction SilentlyContinue) {
        Write-Host "rcedit not found; installing via scoop..."
        & scoop install rcedit
    } else {
        throw @"
rcedit (rcedit-x64.exe) not found and no package manager available to install it.
Install it with one of:
  choco install rcedit -y
  winget install --id ElectronRcEdit.RcEdit -e
  scoop install rcedit
Or pass -RcEdit 'C:\path\to\rcedit-x64.exe'.
"@
    }

    # Re-resolve after install; some installers add to PATH only for new sessions,
    # so refresh this session's PATH from the machine/user environment first.
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' +
                [Environment]::GetEnvironmentVariable('Path', 'User')
    $found = Find-RcEdit
    if (-not $found) { throw "rcedit was installed but could not be located on PATH." }
    return $found
}

$rcedit = Resolve-RcEdit
$exe = (Resolve-Path $ExePath).Path
$icon = (Resolve-Path $IconPath).Path

Write-Host "Embedding icon '$icon' into '$exe' (rcedit: $rcedit)"
& $rcedit $exe --set-icon $icon
if ($LASTEXITCODE -ne 0) { throw "rcedit failed to set the icon (exit $LASTEXITCODE)" }
Write-Host "Icon embedded."
