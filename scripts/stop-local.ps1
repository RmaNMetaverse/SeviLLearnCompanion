$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$stateFile = Join-Path $projectRoot '.tools\run\processes.json'
if (Test-Path -LiteralPath $stateFile) {
    $owned = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    foreach ($ownedProcess in $owned) {
        $process = Get-Process -Id $ownedProcess.pid -ErrorAction SilentlyContinue
        if ($process -and $process.StartTime.ToUniversalTime().ToString('o') -eq $ownedProcess.started) { Stop-Process -Id $process.Id }
    }
    Remove-Item -LiteralPath $stateFile
}
Write-Output 'Stopped the local SeviL processes started by start-local.ps1.'
