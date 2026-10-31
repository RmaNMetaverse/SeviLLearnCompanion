$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if (!$env:JAVA_HOME) {
    $candidates = @('C:\Program Files\Android\openjdk\jdk-21.0.8','C:\Program Files\Android\Android Studio\jbr')
    $env:JAVA_HOME = $candidates | Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1
    if (!$env:JAVA_HOME -and (Test-Path '.tools\jdk')) { $env:JAVA_HOME = (Get-ChildItem '.tools\jdk' -Directory | Select-Object -First 1).FullName }
}
if (!$env:JAVA_HOME) { throw 'Run scripts\setup-android.ps1 first or set JAVA_HOME.' }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-cache'
& apps\android\gradlew.bat -p apps\android assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
New-Item -ItemType Directory -Force -Path artifacts | Out-Null
Copy-Item -LiteralPath 'apps\android\app\build\outputs\apk\debug\app-debug.apk' -Destination 'artifacts\SeviL-debug.apk'
Write-Output 'Android APK: artifacts\SeviL-debug.apk'
