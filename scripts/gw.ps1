# Gradle wrapper for Cue, run with the JDK the build needs.
# Usage: .\scripts\gw.ps1 assembleDebug
#
# The JDK comes from JAVA_HOME, or from a locally unpacked toolchain under
# $HOME\.cue-toolchain when JAVA_HOME is unset; the Android SDK is found through
# ANDROID_HOME/ANDROID_SDK_ROOT, the per-user SDK location, or an existing
# local.properties.
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib\toolchain.ps1")

$jdk = Resolve-CueJdk
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

$repoRoot = Split-Path -Parent $PSScriptRoot
$localProps = Join-Path $repoRoot "local.properties"
if (-not (Test-Path -LiteralPath $localProps)) {
    $sdk = Resolve-CueAndroidSdk
    Set-Content -LiteralPath $localProps -Value ("sdk.dir=" + $sdk.Replace('\', '\\'))
}

& (Join-Path $repoRoot "gradlew.bat") @GradleArgs
exit $LASTEXITCODE
