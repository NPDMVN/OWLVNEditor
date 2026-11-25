param(
    [switch]$RebuildRust
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$AndroidDir = Join-Path $Root "android"
$OutputDir = Join-Path $Root "output"
$JdkHome = "C:\Users\Windows\.dmedit-tools\jdk17\jdk-17.0.21.1+2"
$GradleBat = "C:\Users\Windows\.dmedit-tools\gradle\gradle-8.10.2\bin\gradle.bat"

if ($RebuildRust) {
    Write-Host "==> Rebuilding Rust JNI + Root Helper for 4 ABIs..." -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot "build_rust_android.ps1")
}

if (-not (Test-Path $JdkHome)) {
    throw "JDK 17 not found at $JdkHome"
}
if (-not (Test-Path $GradleBat)) {
    throw "Gradle 8.10.2 not found at $GradleBat"
}

$env:JAVA_HOME = $JdkHome
$env:PATH = "$JdkHome\bin;$env:PATH"

Push-Location $AndroidDir
try {
    Write-Host "==> Building OWLEditor APK (Debug & Release)..." -ForegroundColor Cyan
    & $GradleBat assembleDebug assembleRelease --no-daemon --stacktrace
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$DebugApk = Join-Path $AndroidDir "app\build\outputs\apk\debug\app-debug.apk"
$ReleaseApk = Join-Path $AndroidDir "app\build\outputs\apk\release\app-release.apk"

if (Test-Path $DebugApk) {
    Copy-Item -Force $DebugApk (Join-Path $OutputDir "OWLVNEditor-v1.3.0-debug.apk")
}
if (Test-Path $ReleaseApk) {
    Copy-Item -Force $ReleaseApk (Join-Path $OutputDir "OWLVNEditor-v1.3.0-release.apk")
}

Write-Host "==> Build complete! Output APKs:" -ForegroundColor Green
Get-ChildItem $OutputDir -Filter "*.apk" | ForEach-Object {
    $mb = [math]::Round($_.Length / 1MB, 2)
    Write-Host ("  - {0} ({1} MB)" -f $_.FullName, $mb)
}
