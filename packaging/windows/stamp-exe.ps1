<#
.SYNOPSIS
    Embeds the application icon and version information into an existing .exe.

.DESCRIPTION
    The GraalVM native image (PCPanel.exe) is produced WITHOUT an icon or a
    version resource. Without the icon, Explorer, the taskbar and any shortcut
    pointing at it show the generic default executable icon; without the version
    resource, the file's Properties → Details tab is empty and code signing has no
    product name to check (the SignPath artifact configurations under
    packaging/windows/signpath restrict signing to files whose product name is
    PCPanel). native-image offers no built-in way to set either, so we
    post-process the finished binary with rcedit (the same tool
    electron-builder/pkg use) to write the RT_GROUP_ICON and VS_VERSIONINFO
    resources into the PE.

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

.PARAMETER Version
    The app version (e.g. 2.5.0.83 or 2.5.0). Its leading numeric part becomes the
    file and product version; Windows keeps at most four numbers.

.PARAMETER RcEdit
    Path to rcedit(-x64).exe. If omitted, PATH is searched and, failing that, the
    script tries to install rcedit via Chocolatey/winget/scoop if one is present.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$ExePath,
    [Parameter(Mandatory)] [string]$IconPath,
    [Parameter(Mandatory)] [string]$Version,
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

if ($Version -notmatch '^\d+(\.\d+){0,3}') { throw "Version '$Version' does not start with a numeric version" }
$numericVersion = $Matches[0]

Write-Host "Embedding icon '$icon' and version $numericVersion into '$exe' (rcedit: $rcedit)"
& $rcedit $exe --set-icon $icon `
    --set-file-version $numericVersion `
    --set-product-version $numericVersion `
    --set-version-string ProductName 'PCPanel' `
    --set-version-string FileDescription 'PCPanel' `
    --set-version-string OriginalFilename 'PCPanel.exe' `
    --set-version-string InternalName 'PCPanel'
if ($LASTEXITCODE -ne 0) { throw "rcedit failed to set the icon and version (exit $LASTEXITCODE)" }
Write-Host "Icon and version embedded."
