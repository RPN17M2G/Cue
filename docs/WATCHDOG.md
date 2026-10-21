# Watchdog

A two-hour check that reports whether work is still moving and prompts a rethink when it is not.

## What it checks

- Every git worktree: branch, unmerged commits vs `dev`, dirty files, last commit age.
- Detached Gradle tasks named `CueBuild-*`: state, log age, exit code.
- Stale branch: unmerged work with no commit for `-StaleHours` (default 2) while no build is active.
- Stuck build: a running task whose log has not changed for `-BuildStaleMinutes` (default 60).
- Failed build: exit code recorded by `scripts/detached-gradle.ps1`.

Report: `.cue\watchdog\report-latest.md` (gitignored), history in `history.jsonl`.

## Install

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\watchdog-install.ps1
```

Runs every two hours as the interactive user (only while logged in). Uninstall:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\watchdog-install.ps1 -Uninstall
```

## Rethink prompts

By default prompts are written to the report only. To let the watchdog send them to a fresh `opencode run --auto` session inside the stale worktree, create the marker file `.cue\watchdog\autopilot` (or pass `-Autopilot`). At most one agent is launched per run; override with the `CUE_WATCHDOG_MAX_AGENTS` environment variable. Each agent gets 30 minutes and its transcript is written next to the report.

## Manual run

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\watchdog.ps1
```
