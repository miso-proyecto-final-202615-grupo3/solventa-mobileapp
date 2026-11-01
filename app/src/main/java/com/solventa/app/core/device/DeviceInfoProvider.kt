package com.solventa.app.core.device

import android.os.Build

data class DeviceInfo(
    val installationId: String,
    val platform: String = "ANDROID",
    val model: String,
    val osVersion: String,
)

fun interface DeviceInfoProvider {
    fun get(): DeviceInfo
}

/** Datos del dispositivo para la vinculación; no usa identificadores de hardware. */
class AndroidDeviceInfoProvider(private val installationIdProvider: InstallationIdProvider) : DeviceInfoProvider {
    override fun get(): DeviceInfo = DeviceInfo(
        installationId = installationIdProvider.get(),
        model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        osVersion = Build.VERSION.RELEASE.orEmpty(),
    )
}
