package io.github.veritasx1.licida

import android.app.Application
import io.github.veritasx1.licida.i18n.I18n

/** Sets the language before any screen builds its texts. */
class LiCidaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        I18n.init(this)
    }
}
