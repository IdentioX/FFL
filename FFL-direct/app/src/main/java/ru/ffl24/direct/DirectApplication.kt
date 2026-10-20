package ru.ffl24.direct

import android.app.Application
import ru.ffl24.direct.config.RuntimeProfiles

class DirectApplication : Application() {
    val profiles: RuntimeProfiles by lazy { RuntimeProfiles(this) }
    val graph: AppGraph get() = profiles.graph.value
}
