$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$stateFile = Join-Path $projectRoot '.tools\run\processes.json'
if (Test-Path -LiteralPath $stateFile) {
    $owned = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $snapshot = Get-CimInstance Win32_Process | Select-Object ProcessId,ParentProcessId
    foreach ($ownedProcess in $owned) {
        $process = Get-Process -Id $ownedProcess.pid -ErrorAction SilentlyContinue
        if ($process -and $process.StartTime.ToUniversalTime().Ticks -eq ([DateTime]$ownedProcess.started).ToUniversalTime().Ticks) {
            $descendants = @()
            $frontier = @($process.Id)
            while ($frontier.Count -gt 0) {
                $children = @($snapshot | Where-Object { $_.ParentProcessId -in $frontier } | ForEach-Object { $_.ProcessId })
                $descendants += $children
                $frontier = $children
            }
            Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
            $process.WaitForExit(5000) | Out-Null
            foreach ($childId in $descendants) {
                $child = Get-Process -Id $childId -ErrorAction SilentlyContinue
                if ($child) { Stop-Process -Id $child.Id -ErrorAction SilentlyContinue; $child.WaitForExit(5000) | Out-Null }
            }
        }
    }
    Remove-Item -LiteralPath $stateFile
}
Write-Output 'Stopped the local SeviL processes started by start-local.ps1.'
