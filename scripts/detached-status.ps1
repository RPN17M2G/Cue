# Reports the status of a detached build started by detached-gradle.ps1.
# Usage: .\scripts\detached-status.ps1 -Name t1 [-Tail 40]
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [int]$Tail = 30
)

$log = Join-Path $env:TEMP "cue-$Name.log"
$exitFile = Join-Path $env:TEMP "cue-$Name.exit"

$task = Get-ScheduledTask -TaskName "CueBuild-$Name" -ErrorAction SilentlyContinue
if ($task) {
    Write-Output "STATE=$($task.State)"
} else {
    Write-Output "STATE=NO_TASK"
}

if (Test-Path -LiteralPath $exitFile) {
    Write-Output "EXIT=$(Get-Content -LiteralPath $exitFile -Raw)"
} else {
    Write-Output "EXIT=running"
}

if (Test-Path -LiteralPath $log) {
    Write-Output "--- log tail ---"
    Get-Content -LiteralPath $log -Tail $Tail
} else {
    Write-Output "no log file"
}
