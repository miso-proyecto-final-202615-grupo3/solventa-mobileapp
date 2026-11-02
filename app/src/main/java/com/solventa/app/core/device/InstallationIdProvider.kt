package com.solventa.app.core.device

import android.content.Context
import java.util.UUID

/** Identificador aleatorio de la instalación; no usa identificadores de hardware. */
fun interface InstallationIdProvider {
    fun get(): String
}

class SharedPreferencesInstallationIdProvider(context: Context) : InstallationIdProvider {
    private val preferences = context.getSharedPreferences("solventa_device", Context.MODE_PRIVATE)

    override fun get(): String = preferences.getString(KEY, null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString(KEY, it).commit()
    }

    private companion object { const val KEY = "installation_id" }
}
