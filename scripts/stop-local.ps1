$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$stateFile = Join-Path $projectRoot '.tools\run\processes.json'
if (Test-Path -LiteralPath $stateFile) {
    $owned = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $snapshot = Get-CimInstance Win32_Process | Select-Object ProcessId,ParentProcessId
    foreach ($ownedProcess in $owned) {
        $process = Get-Process -Id $ownedProcess.pid -ErrorAction SilentlyContinue
        if ($process -and $process.StartTime.ToUniversalTime().ToString('o') -eq $ownedProcess.started) {
            $descendants = @()
            $frontier = @($process.Id)
            while ($frontier.Count -gt 0) {
                $children = @($snapshot | Where-Object { $_.ParentProcessId -in $frontier } | ForEach-Object { $_.ProcessId })
                $descendants += $children
                $frontier = $children
            }
            Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
            foreach ($childId in $descendants) { Stop-Process -Id $childId -ErrorAction SilentlyContinue }
        }
    }
    Remove-Item -LiteralPath $stateFile
}
Write-Output 'Stopped the local SeviL processes started by start-local.ps1.'
