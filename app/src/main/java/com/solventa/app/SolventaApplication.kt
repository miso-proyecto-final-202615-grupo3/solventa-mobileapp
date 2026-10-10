package com.solventa.app

import android.app.Application
import com.solventa.app.di.AppContainer

class SolventaApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
