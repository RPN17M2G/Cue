package io.github.rpn17m2g.cue.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.rpn17m2g.cue.settings.CuePalette

/**
 * The launcher icon follows the palette through four activity aliases, each
 * pointing at MainActivity with its own adaptive icon. Android has no API for
 * repainting an app icon in place, and an alias is the supported way to swap
 * it.
 *
 * [PackageManager.DONT_KILL_APP] keeps the running process alive; the launcher
 * picks up the new component the next time it refreshes.
 */
object LauncherIcon {

    private val aliasNames = mapOf(
        CuePalette.MINT to "MainActivityMint",
        CuePalette.LAGOON to "MainActivityLagoon",
        CuePalette.NAVY to "MainActivityNavy",
        CuePalette.DEEP_TEAL to "MainActivityDeepTeal",
    )

    fun apply(context: Context, palette: CuePalette) {
        val manager = context.packageManager
        val components = aliasNames.mapValues { (_, name) ->
            ComponentName(context, "${context.packageName}.$name")
        }
        val alreadyApplied = components.all { (candidate, component) ->
            manager.getComponentEnabledSetting(component) == enabledState(candidate == palette)
        }
        if (alreadyApplied) return
        runCatching {
            components.forEach { (candidate, component) ->
                manager.setComponentEnabledSetting(
                    component,
                    enabledState(candidate == palette),
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    private fun enabledState(enabled: Boolean): Int = if (enabled) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
}
