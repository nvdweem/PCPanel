<#
.SYNOPSIS
    Build SndCtrl.dll on Windows with MSVC, without needing the Visual Studio IDE.

.DESCRIPTION
    Three steps, the first two optional:
      1. (optional) install CMake + the Visual Studio C++ Build Tools via winget
      2. (optional) install a JDK if none is found (only needed for the JNI headers)
      3. configure + build SndCtrl.dll, copying it to src/main/resources

    Run with -InstallTools the first time to set everything up; afterwards just
    run it with no arguments to rebuild.

.PARAMETER InstallTools
    Download and install the build prerequisites (CMake, VS C++ Build Tools, and
    a JDK if none is present) via winget before building.

.PARAMETER JdkHome
    Path to a Windows JDK (must contain include\win32\jni_md.h). Defaults to
    $env:JAVA_HOME; auto-detected / installed if missing.

.PARAMETER Config
    CMake build configuration. Default: Release.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File build-windows.ps1 -InstallTools
        First-time setup + build.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File build-windows.ps1
        Rebuild once the tools are installed.
#>
[CmdletBinding()]
param(
    [switch]$InstallTools,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$Generator = "Visual Studio 17 2022",
    [string]$Config = "Release",
    [string]$BuildDir
)

$ErrorActionPreference = "Stop"
$cppDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $BuildDir) { $BuildDir = Join-Path $cppDir "build" }

function Test-Cmd($name) { [bool](Get-Command $name -ErrorAction SilentlyContinue) }

function Update-PathFromRegistry {
    # winget updates the persisted PATH, but not this process's environment.
    $machine = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $user    = [Environment]::GetEnvironmentVariable("Path", "User")
    $env:Path = (@($machine, $user) | Where-Object { $_ }) -join ";"
}

function Find-WindowsJdk([string]$hint) {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($hint)          { $candidates.Add($hint) }
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
    foreach ($root in @("$env:ProgramFiles\Microsoft",
                        "$env:ProgramFiles\Eclipse Adoptium",
                        "$env:ProgramFiles\Java",
                        "$env:ProgramFiles\BellSoft")) {
        if (Test-Path $root) {
            Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | ForEach-Object { $candidates.Add($_.FullName) }
        }
    }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path (Join-Path $c "include\win32\jni_md.h"))) { return $c }
    }
    return $null
}

# --- Step 1 & 2: install prerequisites (optional) --------------------------
if ($InstallTools) {
    if (-not (Test-Cmd winget)) {
        throw "winget not found. Install 'App Installer' from the Microsoft Store, then re-run; " +
              "or install the tools manually (see README.md)."
    }

    Write-Host "==> Installing CMake..." -ForegroundColor Cyan
    winget install --id Kitware.CMake -e --accept-source-agreements --accept-package-agreements

    Write-Host "==> Installing Visual Studio C++ Build Tools (no IDE)..." -ForegroundColor Cyan
    winget install --id Microsoft.VisualStudio.2022.BuildTools -e `
        --accept-source-agreements --accept-package-agreements `
        --override "--quiet --wait --norestart --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"

    Update-PathFromRegistry

    if (-not (Find-WindowsJdk $JdkHome)) {
        Write-Host "==> No JDK found; installing Microsoft OpenJDK 21 (for the JNI headers)..." -ForegroundColor Cyan
        winget install --id Microsoft.OpenJDK.21 -e --accept-source-agreements --accept-package-agreements
        Update-PathFromRegistry
    }
}

# --- Locate the Windows JDK headers ----------------------------------------
$jdk = Find-WindowsJdk $JdkHome
if (-not $jdk) {
    throw "Could not find a Windows JDK (need <jdk>\include\win32\jni_md.h). " +
          "Set -JdkHome <path> or `$env:JAVA_HOME, or re-run with -InstallTools."
}
Write-Host "==> Using JDK headers: $jdk"

# --- Ensure cmake is reachable ---------------------------------------------
if (-not (Test-Cmd cmake)) {
    $cmakeBin = Join-Path $env:ProgramFiles "CMake\bin"
    if (Test-Path (Join-Path $cmakeBin "cmake.exe")) { $env:Path = "$cmakeBin;$env:Path" }
}
if (-not (Test-Cmd cmake)) {
    throw "cmake not found on PATH. Re-run with -InstallTools, or install CMake (see README.md)."
}

# --- Step 3: configure + build ---------------------------------------------
Write-Host "==> Configuring ($Generator, x64)..." -ForegroundColor Cyan
cmake -S $cppDir -B $BuildDir -G $Generator -A x64 "-DWIN_JDK_HOME=$jdk"
if ($LASTEXITCODE -ne 0) { throw "CMake configure failed (exit $LASTEXITCODE)." }

Write-Host "==> Building ($Config)..." -ForegroundColor Cyan
cmake --build $BuildDir --config $Config
if ($LASTEXITCODE -ne 0) { throw "CMake build failed (exit $LASTEXITCODE)." }

$dll = Resolve-Path (Join-Path $cppDir "..\resources\SndCtrl.dll") -ErrorAction SilentlyContinue
Write-Host ""
Write-Host "==> Done. SndCtrl.dll built and copied to $dll" -ForegroundColor Green
