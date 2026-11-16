# Undoes .\scripts\device-night-mode.ps1 so the phone behaves normally again:
# screen sleeps after the stock timeout, the lock screen comes back, and Cue
# loses its Doze exemption.
#
# Usage:
#   .\scripts\device-restore.ps1
#   .\scripts\device-restore.ps1 -ScreenTimeoutMs 30000 -Serial RFCW50TTSZP
param(
    [string]$Serial = "",
    [string]$Package = "io.github.rpn17m2g.cue",
    [int]$ScreenTimeoutMs = 30000
)

$ErrorActionPreference = "Stop"
$adb = "C:\Users\omerg\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$adbArgs = @()
if ($Serial) { $adbArgs += @("-s", $Serial) }

# The scheduled run happens whether or not the phone is plugged in. Restoring
# is idempotent, but adb fails when there is nothing to talk to, and a failed
# task is noise; say so and succeed instead.
$devices = & $adb devices
$attached = if ($Serial) {
    $devices -match ("^" + [regex]::Escape($Serial) + "\s+device")
} else {
    $devices -match "\sdevice$"
}
if (-not $attached) {
    Write-Output "--- no device attached, nothing to restore ---"
    exit 0
}

& $adb @adbArgs shell svc power stayon false
& $adb @adbArgs shell settings put global stay_on_while_plugged_in 0
& $adb @adbArgs shell settings put system screen_off_timeout $ScreenTimeoutMs
& $adb @adbArgs shell cmd deviceidle whitelist "-$Package"
& $adb @adbArgs shell locksettings set-disabled false

Write-Output "--- phone restored ---"
Write-Output ("stay_on_while_plugged_in = " + (& $adb @adbArgs shell settings get global stay_on_while_plugged_in))
Write-Output ("screen_off_timeout        = " + (& $adb @adbArgs shell settings get system screen_off_timeout))
Write-Output ("lock_screen_disabled      = " + (& $adb @adbArgs shell locksettings get-disabled))
Write-Output ("doze whitelist entry      = " + ((& $adb @adbArgs shell dumpsys deviceidle whitelist | Select-String -Pattern $Package) -join ""))
