$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if (!(Test-Path 'apps\web\node_modules\next')) { throw 'Run scripts\setup.ps1 first.' }
$runDir = Join-Path $projectRoot '.tools\run'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$owned = @()
foreach ($port in @(3000)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { throw "Port $port is already in use. Reuse the running app or stop it first." }
}
try {
    $nodePath = (Get-Command node.exe).Source
    $web = Start-Process -FilePath $nodePath -ArgumentList @('node_modules/next/dist/bin/next','dev','--hostname','127.0.0.1','--port','3000') -WorkingDirectory "$projectRoot\apps\web" -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runDir\web.log" -RedirectStandardError "$runDir\web-error.log"
    $owned += @{pid=$web.Id;started=$web.StartTime.ToUniversalTime().ToString('o');kind='web'}
    $owned | ConvertTo-Json | Set-Content -LiteralPath "$runDir\processes.json"
    Write-Output 'SeviL is starting: http://localhost:3000'
    Write-Output 'Logs: .tools\run. Stop: .\scripts\stop-local.ps1'
} catch { foreach ($ownedProcess in $owned) { Stop-Process -Id $ownedProcess.pid -ErrorAction SilentlyContinue }; throw }
