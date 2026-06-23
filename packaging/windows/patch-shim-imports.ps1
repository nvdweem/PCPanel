<#
.SYNOPSIS
    Rewrites the native-image runner name baked into GraalVM's java.dll/jvm.dll shim imports so it
    matches the renamed, shipped executable.

.DESCRIPTION
    On Windows, GraalVM bundles the JDK's awt.dll/fontmanager.dll and generates tiny java.dll/jvm.dll
    "shim" libraries that re-export the JVM/JNI symbols those libraries import. Crucially, the shims
    resolve those symbols FROM THE MAIN EXECUTABLE, referenced by its build-time file name
    (e.g. pcpanel-2.0-SNAPSHOT-runner.exe) hard-coded into the shims' PE import tables.

    The installer ships the executable under a friendly name (PCPanel.exe), so at runtime the shims
    look for an executable that no longer exists, fail to load, and awt.dll fails with
    "UnsatisfiedLinkError: Can't load library: awt" — silently disabling the overlay and font picker.
    The native integration test does not catch this because it runs the un-renamed runner.

    Since the packaging already post-processes the binary (rcedit sets the icon), this rewrites the
    import name in place. The replacement name must be no longer than the original, so the string can
    be overwritten and null-terminated without moving anything — PE RVAs and section sizes are
    unchanged. Only the shim DLLs reference the exe, and each references it exactly once.

.PARAMETER DistDir
    The assembled distribution folder containing the companion DLLs (and the renamed exe).

.PARAMETER OldName
    The native-image runner file name the shims currently import (e.g. pcpanel-2.0-SNAPSHOT-runner.exe).

.PARAMETER NewName
    The shipped executable file name to import instead (e.g. PCPanel.exe). Must be <= OldName length.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$DistDir,
    [Parameter(Mandatory)] [string]$OldName,
    [Parameter(Mandatory)] [string]$NewName
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$old = [System.Text.Encoding]::ASCII.GetBytes($OldName)
$new = [System.Text.Encoding]::ASCII.GetBytes($NewName)
if ($new.Length -gt $old.Length) {
    throw "New exe name '$NewName' is longer than the original '$OldName'; an in-place import patch would overflow."
}

$total = 0
foreach ($dll in Get-ChildItem (Join-Path $DistDir '*.dll')) {
    $bytes = [System.IO.File]::ReadAllBytes($dll.FullName)
    $patched = 0
    for ($i = 0; $i -le $bytes.Length - $old.Length; $i++) {
        $match = $true
        for ($j = 0; $j -lt $old.Length; $j++) { if ($bytes[$i + $j] -ne $old[$j]) { $match = $false; break } }
        if ($match) {
            for ($j = 0; $j -lt $old.Length; $j++) { $bytes[$i + $j] = if ($j -lt $new.Length) { $new[$j] } else { 0 } }
            $patched++
            $i += $old.Length - 1
        }
    }
    if ($patched -gt 0) {
        [System.IO.File]::WriteAllBytes($dll.FullName, $bytes)
        Write-Host "  $($dll.Name): rewrote $patched shim import '$OldName' -> '$NewName'"
        $total += $patched
    }
}

if ($total -eq 0) {
    Write-Warning "No '$OldName' import found under $DistDir - nothing patched. Did the runner name change, or is this not a GraalVM AWT build?"
} else {
    Write-Host "Patched $total shim import(s) to '$NewName'."
}
