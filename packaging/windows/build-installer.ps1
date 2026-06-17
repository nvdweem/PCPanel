<#
.SYNOPSIS
    Builds the Windows PCPanel installer locally, producing the same artifact as the
    "Windows installer" job in .github/workflows/build-and-release.yml.

.DESCRIPTION
    Mirrors the CI steps exactly:
      1. Compute the app version (<project.baseversion>.<build>, e.g. 2.0.0).
      2. Build the GraalVM native image:  mvnw.cmd -B package -Pnative
      3. Assemble target\windows-dist  (PCPanel.exe + the companion *.dll files
         native-image emits next to the runner; the binary loads them at runtime).
      4. Run Inno Setup's ISCC.exe on packaging\windows\pcpanel.iss.

    The result is target\PCPanel-<version>-setup.exe — byte-for-byte the same recipe
    CI uses (only the build number differs, since that comes from GITHUB_RUN_NUMBER on CI).

.PARAMETER Version
    Full version string to stamp (e.g. 2.0.123). Overrides -Build. Defaults to
    "<project.baseversion>.<Build>".

.PARAMETER Build
    Build number appended to the base version. Stands in for GITHUB_RUN_NUMBER. Default: 0.

.PARAMETER SkipBuild
    Skip the Maven native build and reuse whatever *-runner.exe + *.dll already sit in target\.
    Useful for iterating on the installer packaging without a ~10-minute native rebuild.

.PARAMETER SkipTests
    Pass -DskipTests to Maven. CI runs the tests; this lets a local build skip them.

.PARAMETER JavaHome
    GraalVM CE 25 home (must contain bin\native-image.cmd). Defaults to $env:JAVA_HOME,
    then auto-detection under %USERPROFILE%\.jdks and scoop.

.PARAMETER InnoSetup
    Path to ISCC.exe. Defaults to PATH + the standard install locations.

.EXAMPLE
    pwsh packaging\windows\build-installer.ps1 -Build 123

.EXAMPLE
    pwsh packaging\windows\build-installer.ps1 -SkipBuild -Version 2.0.0
#>
[CmdletBinding()]
param(
    [string]$Version,
    [string]$Build = '0',
    [switch]$SkipBuild,
    [switch]$SkipTests,
    [string]$JavaHome,
    [string]$InnoSetup
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Repo root = two levels up from this script (packaging\windows\).
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$target = Join-Path $repoRoot 'target'
$dist = Join-Path $target 'windows-dist'

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

# ── 1. Version ───────────────────────────────────────────────────────────────
if (-not $Version) {
    $pom = Get-Content (Join-Path $repoRoot 'pom.xml') -Raw
    if ($pom -notmatch '<project\.baseversion>(.*?)</project\.baseversion>') {
        throw "Could not read <project.baseversion> from pom.xml"
    }
    $Version = "$($Matches[1]).$Build"
}
Write-Step "App version: $Version"

# ── 2. Native build ──────────────────────────────────────────────────────────
if (-not $SkipBuild) {
    # Resolve a GraalVM that can run native-image. CI gets this from setup-graalvm.
    if (-not $JavaHome) { $JavaHome = $env:JAVA_HOME }
    function Test-Graal($graalHome) {
        $graalHome -and (Test-Path (Join-Path $graalHome 'bin\native-image.cmd'))
    }
    if (-not (Test-Graal $JavaHome)) {
        $candidates = @(
            Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match 'graal' } | Sort-Object Name -Descending | ForEach-Object FullName
            Get-ChildItem (Join-Path $env:USERPROFILE 'scoop\apps') -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match 'graal' } | ForEach-Object { Join-Path $_.FullName 'current' }
        )
        $JavaHome = $candidates | Where-Object { Test-Graal $_ } | Select-Object -First 1
    }
    if (-not (Test-Graal $JavaHome)) {
        throw "No GraalVM with native-image found. Set -JavaHome or `$env:JAVA_HOME to a GraalVM CE 25 home (must contain bin\native-image.cmd)."
    }
    $env:JAVA_HOME = $JavaHome
    Write-Step "Building native image with JAVA_HOME=$JavaHome"

    $mvnArgs = @('-B', 'package', '-Pnative', '--file', (Join-Path $repoRoot 'pom.xml'))
    if ($SkipTests) { $mvnArgs += '-DskipTests' }
    & (Join-Path $repoRoot 'mvnw.cmd') @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven native build failed (exit $LASTEXITCODE)" }
} else {
    Write-Step "Skipping native build (-SkipBuild); reusing existing target\*-runner.exe"
}

# ── 3. Assemble distribution (exe + companion DLLs) ──────────────────────────
Write-Step "Assembling distribution at $dist"
$runner = Get-ChildItem (Join-Path $target '*-runner.exe') -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $runner) {
    throw "No *-runner.exe found in target\. Run a native build first (omit -SkipBuild)."
}
if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Copy-Item $runner.FullName (Join-Path $dist 'PCPanel.exe')
# native-image drops the companion DLLs next to the runner in target\.
$dlls = Get-ChildItem (Join-Path $target '*.dll') -ErrorAction SilentlyContinue
$dlls | ForEach-Object { Copy-Item $_.FullName $dist }
Write-Host "Distribution contents:"
Get-ChildItem $dist | ForEach-Object { Write-Host "  $($_.Name)" }
if (-not $dlls) {
    Write-Warning "No companion DLLs found next to the executable - the installer may be incomplete."
}

# ── 4. Inno Setup ────────────────────────────────────────────────────────────
if (-not $InnoSetup) {
    $isccCmd = Get-Command iscc.exe -ErrorAction SilentlyContinue
    if ($isccCmd) { $InnoSetup = $isccCmd.Source }
}
if (-not $InnoSetup) {
    $InnoSetup = @(
        "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
        "$env:ProgramFiles\Inno Setup 6\ISCC.exe",
        "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",   # winget per-user default
        (Join-Path $env:USERPROFILE 'scoop\apps\inno-setup\current\ISCC.exe')
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $InnoSetup) {
    throw @"
Inno Setup (ISCC.exe) not found. Install it, then re-run:
  winget install --id JRSoftware.InnoSetup
  scoop install inno-setup
  choco install innosetup -y
Or pass -InnoSetup 'C:\path\to\ISCC.exe'.
"@
}
Write-Step "Building installer with $InnoSetup"

& $InnoSetup `
    "/DMyAppVersion=$Version" `
    "/DSourceDir=$dist" `
    "/DOutputDir=$target" `
    (Join-Path $repoRoot 'packaging\windows\pcpanel.iss')
if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed (exit $LASTEXITCODE)" }

# ── Done ─────────────────────────────────────────────────────────────────────
$installer = Join-Path $target "PCPanel-$Version-setup.exe"
if (-not (Test-Path $installer)) { throw "Expected installer not found: $installer" }
Write-Step "Installer ready"
Write-Host (Resolve-Path $installer).Path -ForegroundColor Green
