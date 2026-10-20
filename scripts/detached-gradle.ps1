# Starts a Gradle build in a detached Scheduled Task so it survives the
# agent shell's process-tree kill on long-running commands.
# Usage: .\scripts\detached-gradle.ps1 -Name t1 -Worktree <path> -GradleArgs testDebugUnitTest
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$Worktree,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

$jdk = "C:\Users\omerg\.cue-toolchain\jdk21\jdk-21.0.12.1+1"
$log = Join-Path $env:TEMP "cue-$Name.log"
$exitFile = Join-Path $env:TEMP "cue-$Name.exit"
$argsText = $GradleArgs -join " "
$cueDir = Join-Path $Worktree ".cue"
New-Item -ItemType Directory -Force -Path $cueDir | Out-Null
$cmdPath = Join-Path $cueDir "run-$Name.cmd"

$lines = @(
    "@echo off",
    "setlocal",
    "set `"JAVA_HOME=$jdk`"",
    "set `"PATH=%JAVA_HOME%\bin;%PATH%`"",
    "cd /d `"$Worktree`"",
    "if not exist local.properties (",
    "  echo sdk.dir=C\:\\Users\\omerg\\AppData\\Local\\Android\\Sdk> local.properties",
    ")",
    "del /q `"$exitFile`" 2>nul",
    "call gradlew.bat $argsText --console=plain > `"$log`" 2>&1",
    "echo %ERRORLEVEL%> `"$exitFile`"",
    "endlocal"
)
Set-Content -LiteralPath $cmdPath -Value $lines -Encoding ASCII

$taskName = "CueBuild-$Name"
$action = New-ScheduledTaskAction -Execute "cmd.exe" -Argument "/c `"$cmdPath`""
$principal = New-ScheduledTaskPrincipal `
    -UserId "$env:COMPUTERNAME\$env:USERNAME" `
    -LogonType Interactive
Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Force | Out-Null
Start-ScheduledTask -TaskName $taskName

Write-Output "Started $taskName"
Write-Output "log=$log"
