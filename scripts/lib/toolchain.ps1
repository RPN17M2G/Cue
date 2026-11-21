# Shared tool resolution for the scripts in this directory. Nothing here
# assumes a particular user name or install path: the JDK comes from JAVA_HOME
# (or a locally unpacked toolchain), the SDK from the usual environment
# variables or the per-user location, and adb from PATH or that SDK.

function Resolve-CueJdk {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return $env:JAVA_HOME
    }
    $toolchain = Join-Path $HOME ".cue-toolchain"
    if (Test-Path -LiteralPath $toolchain) {
        $java = Get-ChildItem -LiteralPath $toolchain -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($java) {
            return (Split-Path -Parent (Split-Path -Parent $java.FullName))
        }
    }
    throw "JDK 17+ not found. Set JAVA_HOME (the project builds with 21)."
}

function Resolve-CueAndroidSdk {
    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate }
    }
    $perUser = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path -LiteralPath $perUser) { return $perUser }
    throw "Android SDK not found. Set ANDROID_HOME or local.properties sdk.dir."
}

function Resolve-CueAdb {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA "Android\Sdk")) |
        Where-Object { $_ }
    foreach ($sdk in $candidates) {
        $adb = Join-Path $sdk "platform-tools\adb.exe"
        if (Test-Path -LiteralPath $adb) { return $adb }
    }
    throw "adb not found. Put platform-tools on PATH or set ANDROID_HOME."
}
