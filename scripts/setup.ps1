param([switch]$Android)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if (!(Test-Path '.venv\Scripts\python.exe')) { python -m venv .venv }
& .\.venv\Scripts\python.exe -m pip install -r services\api\requirements.txt
if ($LASTEXITCODE -ne 0) { throw 'Python dependency installation failed' }
& .\.venv\Scripts\python.exe scripts\sync-locales.py
Push-Location apps\web
try { npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'Web dependency installation failed' } } finally { Pop-Location }
if (!(Test-Path '.env')) { Copy-Item -LiteralPath '.env.example' -Destination '.env' }
if ($Android) { & "$PSScriptRoot\setup-android.ps1" }
Write-Output 'Ready. Run .\scripts\start-local.ps1 and open http://localhost:3000'
