# Builds the Windows skate pack on Windows: the Skate 3 engine (SK8-ENGINE/skate-3-rust-engine at the pinned commit) with the host patch, packaged by
# the engine's own Build-Release.ps1 (with its first-run setup assistant), plus skate.bat, LEEME-skate.md, FUENTE.md and the patch; zipped and audited.
# Needs, once: git, Rust with the MSVC toolchain, LLVM (default location) and Python 3.13 (the engine's packaging uses it).
# Usage (PowerShell, in the repository): powershell -ExecutionPolicy Bypass -File tools\make_skate_pack.ps1 -Version 0.2.0
# The engine is cloned and built in a short folder (-WorkDir, C:\cjsk by default): built deeper, CMake and MSBuild make paths past Windows' limit of
# 260 characters (MSBuild ignores LongPathsEnabled) and the build of SDL3 fails.
param([Parameter(Mandatory = $true)][string]$Version, [string]$WorkDir = "$env:SystemDrive\cjsk")
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$commit = '2e16697797c8358d16f747a8259354ff2921041a'
$work = Join-Path $WorkDir 'src'
$out = Join-Path $root "build\release\cj-skate-$Version-windows"

if (-not (Test-Path (Join-Path $work '.git'))) {
    git clone https://github.com/SK8-ENGINE/skate-3-rust-engine $work
    if ($LASTEXITCODE -ne 0) { throw 'git clone failed' }
}
git -C $work checkout -q --force $commit
git -C $work clean -fdq
git -C $work apply (Join-Path $root 'skate-host\skate-engine-host.patch')
if ($LASTEXITCODE -ne 0) { throw 'the host patch did not apply' }

Push-Location $work
try {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\Build-Release.ps1
    if ($LASTEXITCODE -ne 0) { throw 'the engine did not build (Build-Release.ps1)' }
} finally { Pop-Location }

$engineZip = Join-Path $work 'target\skate3rust-windows-x64.zip'
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force (Join-Path $out 'motor') | Out-Null
$unpacked = Join-Path $WorkDir 'engine-release'
if (Test-Path $unpacked) { Remove-Item -Recurse -Force $unpacked }
Expand-Archive -LiteralPath $engineZip -DestinationPath $unpacked
$inner = Get-ChildItem $unpacked | Select-Object -First 1
$source = if ($inner -and $inner.PSIsContainer -and (Get-ChildItem $unpacked).Count -eq 1) { $inner.FullName } else { $unpacked }
Copy-Item -Recurse -Path (Join-Path $source '*') -Destination (Join-Path $out 'motor')
foreach ($name in 'skate.bat', 'LEEME-skate.md', 'FUENTE.md') { Copy-Item (Join-Path $root "tools\skate_pack\$name") $out }
Copy-Item (Join-Path $root 'skate-host\skate-engine-host.patch') $out

$zip = "$out.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -LiteralPath $out -DestinationPath $zip
python (Join-Path $root 'tools\skate_pack_audit.py') $zip
if ($LASTEXITCODE -ne 0) { Remove-Item -Force $zip; throw 'the pack had Skate 3 files in it; it was not made' }
Write-Host "pack: $zip"
