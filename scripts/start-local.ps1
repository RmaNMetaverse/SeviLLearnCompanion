$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if (!(Test-Path '.venv\Scripts\python.exe') -or !(Test-Path 'apps\web\node_modules\next')) { throw 'Run scripts\setup.ps1 first.' }
if (!(Test-Path '.env')) { Copy-Item -LiteralPath '.env.example' -Destination '.env' }
$runDir = Join-Path $projectRoot '.tools\run'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$owned = @()
foreach ($port in @(8000,3000)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { throw "Port $port is already in use. Reuse the running app or stop it first." }
}
try {
    $api = Start-Process -FilePath "$projectRoot\.venv\Scripts\python.exe" -ArgumentList @('-m','uvicorn','app.main:app','--app-dir','services/api','--host','127.0.0.1','--port','8000') -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runDir\api.log" -RedirectStandardError "$runDir\api-error.log"
    $owned += @{pid=$api.Id;started=$api.StartTime.ToUniversalTime().ToString('o');kind='api'}
    $nodePath = (Get-Command node.exe).Source
    $web = Start-Process -FilePath $nodePath -ArgumentList @('node_modules/next/dist/bin/next','dev','--hostname','127.0.0.1','--port','3000') -WorkingDirectory "$projectRoot\apps\web" -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runDir\web.log" -RedirectStandardError "$runDir\web-error.log"
    $owned += @{pid=$web.Id;started=$web.StartTime.ToUniversalTime().ToString('o');kind='web'}
    $owned | ConvertTo-Json | Set-Content -LiteralPath "$runDir\processes.json"
    Write-Output 'SeviL is starting: http://localhost:3000'
    Write-Output 'API documentation: http://localhost:8000/docs'
    Write-Output 'Logs: .tools\run. Stop: .\scripts\stop-local.ps1'
} catch { foreach ($ownedProcess in $owned) { Stop-Process -Id $ownedProcess.pid -ErrorAction SilentlyContinue }; throw }
