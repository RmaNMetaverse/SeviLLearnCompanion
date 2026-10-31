$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$toolsDir = Join-Path $projectRoot '.tools'
$sdkDir = Join-Path $toolsDir 'android-sdk'
New-Item -ItemType Directory -Force -Path "$toolsDir\downloads", "$sdkDir\cmdline-tools" | Out-Null
if (!$env:JAVA_HOME -or !(Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $candidates = @('C:\Program Files\Android\openjdk\jdk-21.0.8', 'C:\Program Files\Android\Android Studio\jbr')
    $jdkPath = $candidates | Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1
    if (!$jdkPath) {
        $archive = "$toolsDir\downloads\jdk.zip"
        Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $archive
        Expand-Archive -LiteralPath $archive -DestinationPath "$toolsDir\jdk" -Force
        $jdkPath = (Get-ChildItem -LiteralPath "$toolsDir\jdk" -Directory | Select-Object -First 1).FullName
    }
    $env:JAVA_HOME = $jdkPath
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = $sdkDir
if (!(Test-Path "$sdkDir\cmdline-tools\latest\bin\sdkmanager.bat")) {
    $archive = "$toolsDir\downloads\android-cli.zip"
    Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -OutFile $archive
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLower() -ne '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a') { throw 'Android tools checksum mismatch' }
    Expand-Archive -LiteralPath $archive -DestinationPath "$toolsDir\android-cli" -Force
    Move-Item -LiteralPath "$toolsDir\android-cli\cmdline-tools" -Destination "$sdkDir\cmdline-tools\latest"
}
1..100 | ForEach-Object { 'y' } | & "$sdkDir\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=$sdkDir --licenses | Out-Null
& "$sdkDir\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=$sdkDir 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK installation failed' }
Set-Content -LiteralPath 'apps\android\local.properties' -Value ('sdk.dir=' + $sdkDir.Replace('\','/'))
Write-Output "Android tools ready. JAVA_HOME=$env:JAVA_HOME"
