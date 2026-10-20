package io.github.rpn17m2g.cue.di

import android.content.Context
import io.github.rpn17m2g.cue.data.settings.DataStoreSettingsRepository
import io.github.rpn17m2g.cue.settings.SettingsRepository

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(appContext)
}
