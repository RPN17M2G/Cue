package io.github.rpn17m2g.cue

import android.app.Application
import io.github.rpn17m2g.cue.di.AppContainer

class CueApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
