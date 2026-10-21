[CmdletBinding()]
param(
    [string]$RepoRoot,
    [double]$StaleHours = 2,
    [int]$BuildStaleMinutes = 60,
    [switch]$Autopilot
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
}
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$stateDir = Join-Path $RepoRoot '.cue\watchdog'
New-Item -ItemType Directory -Force -Path $stateDir | Out-Null
$now = Get-Date
$autopilotEnabled = $Autopilot -or (Test-Path -LiteralPath (Join-Path $stateDir 'autopilot'))
$maxAgents = if ($env:CUE_WATCHDOG_MAX_AGENTS) { [int]$env:CUE_WATCHDOG_MAX_AGENTS } else { 1 }

function Invoke-Git {
    param([string]$Path, [string[]]$Arguments)
    $output = & git -C $Path @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) { return @() }
    return @($output)
}

$worktrees = @()
$entry = $null
foreach ($line in (Invoke-Git $RepoRoot @('worktree', 'list', '--porcelain'))) {
    if ($line -like 'worktree *') {
        if ($entry) { $worktrees += $entry }
        $entry = [pscustomobject]@{ Path = ($line -replace '^worktree ', '').Trim(); Branch = $null; Head = $null }
    } elseif ($line -like 'branch refs/heads/*') {
        $entry.Branch = ($line -replace '^branch refs/heads/', '').Trim()
    } elseif ($line -like 'HEAD *') {
        $entry.Head = ($line -replace '^HEAD ', '').Trim()
    }
}
if ($entry) { $worktrees += $entry }

$buildTasks = @()
foreach ($task in (Get-ScheduledTask -TaskName 'CueBuild-*' -ErrorAction SilentlyContinue)) {
    $name = $task.TaskName -replace '^CueBuild-', ''
    $logPath = Join-Path $env:TEMP "cue-$name.log"
    $exitPath = Join-Path $env:TEMP "cue-$name.exit"
    $lastWrite = if (Test-Path -LiteralPath $logPath) { (Get-Item -LiteralPath $logPath).LastWriteTime } else { $null }
    $exitCode = $null
    if (Test-Path -LiteralPath $exitPath) {
        $rawExit = Get-Content -LiteralPath $exitPath -Raw
        if ($rawExit) { $exitCode = $rawExit.Trim() }
    }
    $buildTasks += [pscustomobject]@{
        Name = $name
        State = [string]$task.State
        Log = $logPath
        LastWrite = $lastWrite
        Exit = $exitCode
    }
}

$activeBuilds = @($buildTasks | Where-Object {
    $_.State -eq 'Running' -and $_.LastWrite -and ($now - $_.LastWrite).TotalMinutes -lt 10
})
$stuckBuilds = @($buildTasks | Where-Object {
    $_.State -eq 'Running' -and (-not $_.LastWrite -or ($now - $_.LastWrite).TotalMinutes -ge $BuildStaleMinutes)
})
$failedBuilds = @($buildTasks | Where-Object { $_.State -ne 'Running' -and $_.Exit -and $_.Exit -ne '0' })

$rows = @()
foreach ($wt in $worktrees) {
    if (-not (Test-Path -LiteralPath $wt.Path)) { continue }
    $dirty = @(Invoke-Git $wt.Path @('status', '--porcelain')).Count
    $lastCommit = $null
    $ageHours = $null
    if ($wt.Branch) {
        $ct = (Invoke-Git $wt.Path @('log', '-1', '--format=%ct')) -join ''
        if ($ct) {
            $lastCommit = [DateTimeOffset]::FromUnixTimeSeconds([int64]$ct).LocalDateTime
            $ageHours = [math]::Round(($now - $lastCommit).TotalHours, 1)
        }
    }
    $ahead = 0
    if ($wt.Branch) {
        $count = (Invoke-Git $wt.Path @('rev-list', '--count', "dev..$($wt.Branch)")) -join ''
        if ($count) { $ahead = [int]$count }
    }

    $status = 'WORKING'
    if (-not $wt.Branch) {
        $status = 'DETACHED'
    } elseif ($wt.Path.TrimEnd('\') -eq $RepoRoot.TrimEnd('\')) {
        $status = 'BASE'
    } elseif ($ahead -eq 0) {
        $status = 'MERGED'
    } elseif ($activeBuilds.Count -gt 0 -and $ageHours -ne $null -and $ageHours -lt $StaleHours) {
        $status = 'BUILDING'
    } elseif ($ageHours -ne $null -and $ageHours -ge $StaleHours) {
        $status = 'STALE'
    }

    $rows += [pscustomobject]@{
        Branch = if ($wt.Branch) { $wt.Branch } else { '(detached)' }
        Path = $wt.Path
        Ahead = $ahead
        Dirty = $dirty
        LastCommit = if ($lastCommit) { $lastCommit.ToString('yyyy-MM-dd HH:mm') } else { '-' }
        AgeHours = $ageHours
        Status = $status
    }
}

$prompts = @()
foreach ($row in $rows | Where-Object { $_.Status -eq 'STALE' }) {
    $prompts += [pscustomobject]@{
        Target = $row.Branch
        Path = $row.Path
        Reason = "No commits for $($row.AgeHours)h"
        Text = @"
You are working in the Cue repository worktree $($row.Path) on branch $($row.Branch).
There have been no commits for $($row.AgeHours) hours while this branch has $($row.Ahead) unmerged commit(s).

Stop and reassess before continuing:
1. Read docs/PLAN.md, docs/PHASES.md, and docs/STANDARDS.md.
2. Inspect git status, git log --oneline -10, and the uncommitted diff.
3. Decide explicitly: are you blocked on something meaningful that needs a decision, or is the current approach not worth continuing? If it should be dropped or narrowed, say so and update the branch/plan accordingly.
4. Otherwise take the next concrete step, keep tests green, and commit incrementally.

Do not merge and do not push. Record the reassessment in your final message.
"@
    }
}
foreach ($task in $stuckBuilds) {
    $prompts += [pscustomobject]@{
        Target = "build:$($task.Name)"
        Path = $RepoRoot
        Reason = "Build log unchanged for $BuildStaleMinutes+ min"
        Text = @"
The detached Gradle task CueBuild-$($task.Name) is still running but its log has not changed for at least $BuildStaleMinutes minutes.
Kill the task if it is hung, then rerun the build detached with scripts/detached-gradle.ps1 and poll scripts/detached-status.ps1.
If the hang is environmental, adopt a different verification approach and document it. Do not fake a successful build.
"@
    }
}
foreach ($task in $failedBuilds) {
    $prompts += [pscustomobject]@{
        Target = "build:$($task.Name)"
        Path = $RepoRoot
        Reason = "Last detached build exited $($task.Exit)"
        Text = @"
Detached build CueBuild-$($task.Name) last exited with code $($task.Exit). Read the tail of $($task.Log), fix the failure, and rerun the build. Do not continue feature work on a red build.
"@
    }
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Cue watchdog report")
$lines.Add('')
$lines.Add("Generated: $($now.ToString('yyyy-MM-dd HH:mm:ss'))")
$lines.Add("Autopilot: $autopilotEnabled")
$lines.Add('')
$lines.Add('| Branch | Ahead | Dirty | Last commit | Age (h) | Status |')
$lines.Add('|---|---|---|---|---|---|')
foreach ($row in $rows) {
    $lines.Add("| $($row.Branch) | $($row.Ahead) | $($row.Dirty) | $($row.LastCommit) | $($row.AgeHours) | $($row.Status) |")
}
$lines.Add('')
if ($buildTasks.Count -gt 0) {
    $lines.Add('## Detached builds')
    foreach ($task in $buildTasks) {
        $age = if ($task.LastWrite) { [math]::Round(($now - $task.LastWrite).TotalMinutes, 1) } else { 'n/a' }
        $lines.Add("- CueBuild-$($task.Name): $($task.State), log age ${age}m, exit $($task.Exit)")
    }
    $lines.Add('')
}
if ($prompts.Count -eq 0) {
    $lines.Add('No stale branches or stuck builds.')
} else {
    $lines.Add('## Prompts to send')
    foreach ($prompt in $prompts) {
        $lines.Add('')
        $lines.Add("### $($prompt.Target) - $($prompt.Reason)")
        $lines.Add('```text')
        $lines.Add($prompt.Text.Trim())
        $lines.Add('```')
    }
}

$reportPath = Join-Path $stateDir 'report-latest.md'
Set-Content -LiteralPath $reportPath -Value ($lines -join "`r`n") -Encoding UTF8

$history = [pscustomobject]@{
    at = $now.ToString('o')
    worktrees = $rows
    builds = $buildTasks
    stale = @($prompts | ForEach-Object { $_.Target })
}
Add-Content -LiteralPath (Join-Path $stateDir 'history.jsonl') -Value ($history | ConvertTo-Json -Compress -Depth 6) -Encoding UTF8

if ($autopilotEnabled -and $prompts.Count -gt 0) {
    $sent = 0
    foreach ($prompt in $prompts) {
        if ($sent -ge $maxAgents) { break }
        $safeName = ($prompt.Target -replace '[^\w.-]', '_')
        $logPath = Join-Path $stateDir ("opencode-$safeName-" + $now.ToString('yyyyMMdd-HHmm') + '.log')
        $job = Start-Job -ScriptBlock {
            param($Worktree, $Message)
            Set-Location -LiteralPath $Worktree
            & opencode run --auto $Message 2>&1 | Out-String
        } -ArgumentList $prompt.Path, $prompt.Text
        $completed = Wait-Job -Job $job -Timeout 1800
        if ($completed) {
            Receive-Job -Job $job | Set-Content -LiteralPath $logPath -Encoding UTF8
        } else {
            Stop-Job -Job $job
            'TIMEOUT after 30 minutes' | Set-Content -LiteralPath $logPath -Encoding UTF8
        }
        Remove-Job -Job $job -Force
        Add-Content -LiteralPath (Join-Path $stateDir 'watchdog.log') -Value "$($now.ToString('o')) autopilot $($prompt.Target) -> $logPath" -Encoding UTF8
        $sent++
    }
}

Write-Output "Watchdog report: $reportPath"
foreach ($row in $rows) {
    Write-Output ("{0,-24} {1,-10} ahead={2} dirty={3} age={4}h" -f $row.Branch, $row.Status, $row.Ahead, $row.Dirty, $row.AgeHours)
}
