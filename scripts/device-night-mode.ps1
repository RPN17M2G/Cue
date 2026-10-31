# Puts the test phone into an all-night, unattended automation state:
# screen stays on while plugged in, the screen never times out, Doze will not
# freeze Cue, and the lock screen is disabled. Always pair this with
# .\scripts\device-restore.ps1 when the work session ends.
#
# Usage:
#   .\scripts\device-night-mode.ps1                 # default device, Cue app
#   .\scripts\device-night-mode.ps1 -Serial RFCW50TTSZP
param(
    [string]$Serial = "",
    [string]$Package = "io.github.rpn17m2g.cue"
)

$ErrorActionPreference = "Stop"
$adb = "C:\Users\omerg\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$adbArgs = @()
if ($Serial) { $adbArgs += @("-s", $Serial) }

& $adb @adbArgs shell svc power stayon true
& $adb @adbArgs shell settings put global stay_on_while_plugged_in 7
& $adb @adbArgs shell settings put system screen_off_timeout 2147483647
& $adb @adbArgs shell cmd deviceidle whitelist "+$Package"

# Best effort: fails when a secure credential is set, which is fine.
& $adb @adbArgs shell locksettings set-disabled true 2>$null | Out-Null

Write-Output "--- night mode active ---"
Write-Output ("stay_on_while_plugged_in = " + (& $adb @adbArgs shell settings get global stay_on_while_plugged_in))
Write-Output ("screen_off_timeout        = " + (& $adb @adbArgs shell settings get system screen_off_timeout))
Write-Output ("lock_screen_disabled      = " + (& $adb @adbArgs shell locksettings get-disabled))
Write-Output ("doze whitelist entry      = " + ((& $adb @adbArgs shell dumpsys deviceidle whitelist | Select-String -Pattern $Package) -join ""))
Write-Output ""
Write-Output "Restore with: .\scripts\device-restore.ps1"
