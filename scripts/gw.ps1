# Gradle wrapper for Cue.
# Requires JDK 21 (installed locally at the path below) and the Android SDK.
# Usage: .\scripts\gw.ps1 assembleDebug
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

$jdk = "C:\Users\omerg\.cue-toolchain\jdk21\jdk-21.0.12.1+1"
if (-not (Test-Path -LiteralPath $jdk)) {
    throw "JDK 21 not found at $jdk. Run scripts/setup-toolchain.ps1 first."
}
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

$repoRoot = Split-Path -Parent $PSScriptRoot
$localProps = Join-Path $repoRoot "local.properties"
if (-not (Test-Path -LiteralPath $localProps)) {
    $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    Set-Content -LiteralPath $localProps -Value ("sdk.dir=" + $sdk.Replace('\', '\\'))
}

& (Join-Path $repoRoot "gradlew.bat") @GradleArgs
exit $LASTEXITCODE
