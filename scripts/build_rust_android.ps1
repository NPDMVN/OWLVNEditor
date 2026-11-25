$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$rustDir = Join-Path $root "rust"
$jniOut = Join-Path $root "android\app\src\main\jniLibs"
$binOut = Join-Path $root "android\app\src\main\assets\bin"
New-Item -ItemType Directory -Force -Path $jniOut, $binOut | Out-Null

# Pick stable-gnu toolchain (has all 4 Android targets and self-contained Windows host linker for proc-macros)
$toolchain = "+stable-x86_64-pc-windows-gnu"

$targets = @(
    @{ Triple = "aarch64-linux-android";   Abi = "arm64-v8a";   Helper = "owledit-helper-aarch64" },
    @{ Triple = "armv7-linux-androideabi"; Abi = "armeabi-v7a"; Helper = "owledit-helper-armv7"   },
    @{ Triple = "x86_64-linux-android";    Abi = "x86_64";      Helper = "owledit-helper-x86_64"  },
    @{ Triple = "i686-linux-android";      Abi = "x86";         Helper = "owledit-helper-x86"     }
)

Push-Location $rustDir
try {
    foreach ($t in $targets) {
        Write-Host "=== Building $($t.Triple) ($($t.Abi)) ===" -ForegroundColor Cyan
        & cargo $toolchain build --release --target $t.Triple -p owledit-ffi -p owledit-helper
        if ($LASTEXITCODE -ne 0) {
            throw "Cargo build failed for $($t.Triple)"
        }

        $abiDir = Join-Path $jniOut $t.Abi
        New-Item -ItemType Directory -Force -Path $abiDir | Out-Null

        $soSrc = Join-Path $rustDir "target\$($t.Triple)\release\libowledit_core.so"
        $soDst = Join-Path $abiDir "libowledit_core.so"
        Copy-Item -Force $soSrc $soDst

        $helperSrc = Join-Path $rustDir "target\$($t.Triple)\release\owledit-helper"
        $helperDst = Join-Path $binOut $t.Helper
        Copy-Item -Force $helperSrc $helperDst

        Write-Host "  -> $soDst ($([math]::Round((Get-Item $soDst).Length / 1KB, 1)) KB)" -ForegroundColor Green
        Write-Host "  -> $helperDst ($([math]::Round((Get-Item $helperDst).Length / 1KB, 1)) KB)" -ForegroundColor Green
    }
} finally {
    Pop-Location
}

Write-Host "`nAll 4 Android ABIs built successfully!" -ForegroundColor Green
