package io.github.rpn17m2g.cue

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Invisible entry point behind the four launcher aliases.
 *
 * Android records the component that started a task as its base. When an alias
 * targets MainActivity directly, that alias *is* the base, so disabling it (the
 * only way to swap the adaptive icon) tears the whole task down and drops the
 * user to the home screen mid-session. Forwarding through this short-lived
 * activity keeps MainActivity as the task's root, so a palette change can
 * repaint the icon without dismissing the app.
 *
 * The aliases keep the icon and label; this activity owns no UI of its own and
 * finishes as soon as the real screen is on its way.
 */
class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}
